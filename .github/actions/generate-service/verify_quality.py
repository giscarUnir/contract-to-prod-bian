"""Comprueba que se ejecuten ambas suites y exige la cobertura del núcleo."""

import os
import sys
from pathlib import Path
from xml.etree import ElementTree

target = Path(sys.argv[1]) / "target"
suites = [
    ElementTree.parse(report).getroot()
    for report in (target / "surefire-reports").glob("TEST-*.xml")
]


def executed(suite):
    return int(suite.attrib["tests"]) - int(suite.attrib.get("skipped", 0))


http = sum(executed(suite) for suite in suites if suite.attrib["name"] == "demo.bian.CurrentAccountAcceptanceTest")
unit = sum(
    executed(suite) for suite in suites
    if suite.attrib["name"].startswith(("demo.bian.domain.", "demo.bian.application.", "demo.bian.adapter."))
)
if http < 7 or not unit:
    raise SystemExit("Deben ejecutarse las siete pruebas HTTP independientes y pruebas unitarias generadas.")

report = ElementTree.parse(target / "jacoco-report/jacoco.xml")
packages = ("demo/bian/domain", "demo/bian/application")
lines = [
    counter
    for package in report.findall("package")
    if package.attrib["name"] in packages or package.attrib["name"].startswith(tuple(name + "/" for name in packages))
    for counter in package.findall("counter")
    if counter.attrib["type"] == "LINE"
]
covered = sum(int(line.attrib["covered"]) for line in lines)
total = covered + sum(int(line.attrib["missed"]) for line in lines)
if not total:
    raise SystemExit("No se midió la cobertura de dominio y aplicación.")

coverage = 100 * covered / total
print(f"{unit} pruebas unitarias, {http} pruebas HTTP y {coverage:.1f}% de cobertura.")
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    print(f"tests={http}", f"unit={unit}", f"coverage={coverage:.1f}%", sep="\n", file=output)
if coverage < 80:
    raise SystemExit("El mínimo exigido de cobertura es 80%.")
