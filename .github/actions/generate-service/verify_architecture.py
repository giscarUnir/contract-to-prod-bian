"""Comprueba con jdeps las dependencias de las clases ya compiladas."""

import re
import subprocess
import sys
from pathlib import Path

classes = Path(sys.argv[1]) / "target/classes"
analysis = subprocess.run(
    ["jdeps", "-verbose:class", "-filter:none", str(classes)],
    check=True, capture_output=True, encoding="utf-8",
).stdout
dependencies = re.findall(r"(?m)^\s+(demo\.bian\.\S+)\s+->\s+(\S+)", analysis)
if not dependencies:
    raise SystemExit("jdeps no encontró dependencias para verificar.")

failures = []
for source, target in dependencies:
    if source.startswith("demo.bian.domain."):
        if not target.startswith(("java.", "demo.bian.domain.")):
            failures.append((source, target))
    elif source.startswith("demo.bian.application."):
        if target.startswith(("demo.bian.adapter.", "demo.bian.generated.", "jakarta.ws.rs.", "io.quarkus.")):
            failures.append((source, target))
    elif source.startswith("demo.bian.adapter."):
        if target.startswith("demo.bian.application.") and not target.startswith("demo.bian.application.port."):
            failures.append((source, target))
if failures:
    for source, target in failures:
        print(f"Dependencia de capa inválida: {source} -> {target}")
    raise SystemExit(1)
print("Dependencias de dominio, aplicación, puertos y adaptadores verificadas.")
