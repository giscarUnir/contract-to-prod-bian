import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class QualifyPortTypes {
    private static final String PORT_PACKAGE = "demo.bian.application.port.in";

    record Port(ClassTree declaration, Set<String> nestedTypes) {
        String name() {
            return declaration.getSimpleName().toString();
        }

        String qualifiedName() {
            return PORT_PACKAGE + "." + name();
        }
    }

    record Edit(int start, int end, String replacement) {}

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        Path application = project.resolve("src/main/java/demo/bian/application");
        Path tests = project.resolve("src/test/java/demo/bian");
        var files = new ArrayList<Path>();
        for (Path root : List.of(application, tests)) {
            try (var paths = Files.walk(root)) {
                files.addAll(paths.filter(path -> path.toString().endsWith(".java")).toList());
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, manager,
                    new DiagnosticCollector<JavaFileObject>(), List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            var ports = new HashMap<String, Port>();
            var declaredNames = new HashMap<String, Long>();
            for (var unit : units) {
                for (Tree type : unit.getTypeDecls()) {
                    if (!(type instanceof ClassTree declaration)) continue;
                    String name = declaration.getSimpleName().toString();
                    declaredNames.merge(name, 1L, Long::sum);
                    if (declaration.getKind() == Tree.Kind.INTERFACE && PORT_PACKAGE.equals(packageName(unit))) {
                        Set<String> nested = declaration.getMembers().stream()
                                .filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                                .map(member -> member.getSimpleName().toString()).collect(Collectors.toSet());
                        ports.put(name, new Port(declaration, nested));
                    }
                }
            }
            ports.keySet().removeIf(name -> declaredNames.get(name) != 1);
            for (var unit : units) {
                Path file = Path.of(unit.getSourceFile().toUri());
                if (PORT_PACKAGE.equals(packageName(unit))) continue;
                var edits = new ArrayList<Edit>();
                if (file.startsWith(tests.toAbsolutePath())) {
                    new TreeScanner<Void, Void>() {
                        @Override
                        public Void visitImport(com.sun.source.tree.ImportTree tree, Void unused) {
                            return null;
                        }

                        @Override
                        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                            if (tree.getExpression() instanceof IdentifierTree identifier) {
                                String name = identifier.getName().toString();
                                Port port = ports.get(name);
                                if (port != null && port.nestedTypes().contains(tree.getIdentifier().toString())
                                        && importsFor(unit, name).isEmpty()) {
                                    addEdit(unit, positions, edits, identifier, port.qualifiedName());
                                }
                            }
                            return super.visitMemberSelect(tree, unused);
                        }
                    }.scan(unit, null);
                } else {
                    for (Tree type : unit.getTypeDecls()) {
                        if (!(type instanceof ClassTree service) || service.getKind() != Tree.Kind.CLASS) continue;
                        var implementedPorts = ports.values().stream().filter(port ->
                                service.getImplementsClause().stream().anyMatch(reference ->
                                        reference.toString().equals(port.qualifiedName())
                                        || (reference.toString().equals(port.name()) &&
                                            (importsFor(unit, port.name()).contains(port.qualifiedName())
                                            || unit.getImports().stream().anyMatch(item ->
                                                    !item.isStatic() && item.getQualifiedIdentifier().toString()
                                                            .equals(PORT_PACKAGE + ".*")))))).toList();
                        for (Tree member : service.getMembers()) {
                            if (member instanceof MethodTree method) {
                                qualifyParameters(unit, positions, edits, implementedPorts, method);
                            }
                        }
                    }
                }
                if (edits.isEmpty()) continue;
                var source = new StringBuilder(Files.readString(file));
                edits.sort(Comparator.comparingInt(Edit::start).reversed());
                for (Edit edit : edits) source.replace(edit.start(), edit.end(), edit.replacement());
                Files.writeString(file, source);
                System.out.printf("%s: %d referencias a tipos de puerto calificadas.%n", file.getFileName(), edits.size());
            }
        }
    }

    private static void qualifyParameters(CompilationUnitTree unit, SourcePositions positions, List<Edit> edits,
                                          List<Port> ports, MethodTree method) {
        var matchingPorts = ports.stream().filter(port -> port.declaration().getMembers().stream()
                .filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
                .anyMatch(candidate -> sameSignature(candidate, method, port))).toList();
        if (matchingPorts.size() != 1) return;
        Port port = matchingPorts.getFirst();
        for (var parameter : method.getParameters()) {
            if (!(parameter.getType() instanceof IdentifierTree type)) continue;
            String name = type.getName().toString();
            if (!port.nestedTypes().contains(name)) continue;
            if (ports.stream().filter(other -> other.nestedTypes().contains(name)).count() < 2) continue;
            addEdit(unit, positions, edits, type, port.qualifiedName() + "." + name);
        }
    }

    private static boolean sameSignature(MethodTree expected, MethodTree actual, Port port) {
        if (!expected.getName().contentEquals(actual.getName())
                || expected.getParameters().size() != actual.getParameters().size()) return false;
        for (int index = 0; index < expected.getParameters().size(); index++) {
            String required = expected.getParameters().get(index).getType().toString();
            String supplied = actual.getParameters().get(index).getType().toString();
            if (required.equals(supplied)) continue;
            if (port.nestedTypes().contains(required)
                    && (supplied.equals(port.name() + "." + required)
                    || supplied.equals(port.qualifiedName() + "." + required))) continue;
            return false;
        }
        return true;
    }

    private static List<String> importsFor(CompilationUnitTree unit, String name) {
        return unit.getImports().stream().filter(item -> !item.isStatic())
                .map(item -> item.getQualifiedIdentifier().toString())
                .filter(imported -> imported.endsWith("." + name)).toList();
    }

    private static String packageName(CompilationUnitTree unit) {
        return unit.getPackageName() == null ? "" : unit.getPackageName().toString();
    }

    private static void addEdit(CompilationUnitTree unit, SourcePositions positions, List<Edit> edits,
                                Tree tree, String replacement) {
        long start = positions.getStartPosition(unit, tree);
        long end = positions.getEndPosition(unit, tree);
        if (start >= 0 && end > start) edits.add(new Edit((int) start, (int) end, replacement));
    }
}
