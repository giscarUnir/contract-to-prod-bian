"""Construye un proyecto independiente y aplica una propuesta con rutas acotadas."""

import argparse
import json
import re
import shutil
from pathlib import Path, PurePosixPath

ACTION = Path(__file__).resolve().parent
REPO = ACTION.parents[2]
JAVA = "src/main/java/demo/bian"
TEST = "src/test/java/demo/bian"
LAYERS = (
    f"{JAVA}/domain/",
    f"{JAVA}/application/port/in/",
    f"{JAVA}/application/port/out/",
    f"{JAVA}/application/",
    f"{JAVA}/adapter/in/rest/",
    f"{JAVA}/adapter/out/memory/",
)
TEST_LAYERS = tuple(f"{TEST}/{package}/" for package in ("domain", "application", "adapter"))
JAVA_NAME = r"[a-z][a-z0-9]*(?:/[a-z][a-z0-9]*)*/[A-Z][A-Za-z0-9]*\.java"


def scaffold(project):
    project.mkdir(parents=True, exist_ok=False)
    sources = {
        ACTION / "template/pom.xml": "pom.xml",
        ACTION / "template/application.properties": "src/main/resources/application.properties",
        ACTION / "template/README.md": "README.md",
        ACTION / "template/LICENSE": "LICENSE",
        ACTION / "acceptance/CurrentAccountAcceptanceTest.java": f"{TEST}/CurrentAccountAcceptanceTest.java",
        REPO / "contracts/bian/openapi.yaml": "contracts/bian/openapi.yaml",
        REPO / "contracts/bian/current-account.acceptance.md": "contracts/bian/current-account.acceptance.md",
    }
    for source, relative in sources.items():
        destination = project / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)


def read_proposal(proposal):
    if proposal.stat().st_size > 280_000:
        raise ValueError("La propuesta supera el tamaño permitido.")
    files = json.loads(proposal.read_text(encoding="utf-8")).get("files")
    if not isinstance(files, list) or not 4 <= len(files) <= 45:
        raise ValueError("La propuesta debe incluir entre 4 y 45 archivos Java.")

    sources = {}
    for file in files:
        path, content = file["path"], file["content"]
        if not isinstance(path, str) or not isinstance(content, str) or not content.strip():
            raise ValueError("Se requiere una ruta y contenido Java por archivo.")
        if not re.fullmatch(r"src/(?:main|test)/java/demo/bian/" + JAVA_NAME, path):
            raise ValueError("La propuesta contiene una ruta Java fuera del formato permitido.")
        if path in sources:
            raise ValueError("La propuesta contiene una ruta duplicada.")
        if path.startswith(JAVA + "/"):
            if not path.startswith(LAYERS):
                raise ValueError("La propuesta contiene código fuera de las capas permitidas.")
        elif not path.startswith(TEST_LAYERS) or not path.endswith("Test.java"):
            raise ValueError("Las pruebas unitarias deben quedar en sus paquetes y terminar en Test.java.")
        expected = ".".join(PurePosixPath(path).parts[3:-1])
        if not re.search(rf"(?m)^package\s+{re.escape(expected)}\s*;", content):
            raise ValueError("El paquete Java de la propuesta no coincide con su ruta.")
        sources[path] = content

    present = {layer for layer in LAYERS if any(path.startswith(layer) for path in sources)}
    if present != set(LAYERS) or not any(path.startswith(TEST_LAYERS) for path in sources):
        raise ValueError("La propuesta debe incluir dominio, casos de uso, ambos puertos, ambos adaptadores y pruebas unitarias.")
    app_prefix = f"{JAVA}/application/"
    if not any(path.startswith(app_prefix) and not path.startswith(app_prefix + "port/") for path in sources):
        raise ValueError("Falta la implementación de casos de uso en la capa de aplicación.")
    return sources


def apply(project, proposal):
    sources = read_proposal(proposal)
    for relative, content in sources.items():
        path = project / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("x", encoding="utf-8", newline="\n") as file:
            file.write(content.rstrip() + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("scaffold").add_argument("project", type=Path)
    apply_proposal = commands.add_parser("apply")
    apply_proposal.add_argument("project", type=Path)
    apply_proposal.add_argument("proposal", type=Path)
    args = parser.parse_args()
    if args.command == "scaffold":
        scaffold(args.project)
    else:
        try:
            apply(args.project, args.proposal)
        except (AttributeError, KeyError, OSError, TypeError, ValueError) as error:
            raise SystemExit(f"No se pudo aplicar la propuesta: {error}") from None


if __name__ == "__main__":
    main()
