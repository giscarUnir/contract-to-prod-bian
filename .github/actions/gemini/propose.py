"""Solicita a Gemini la implementación y sus pruebas unitarias."""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ACTION = Path(__file__).resolve().parent
INPUTS = (
    "contracts/bian/openapi.yaml",
    "contracts/bian/current-account.acceptance.md",
)


def context_for(project):
    generated = project / "target/generated-sources/openapi/src/main/java/demo/bian/generated"
    java_files = sorted(generated.rglob("*.java"))
    if not java_files:
        raise ValueError("Faltan las interfaces y los modelos generados desde el contrato.")
    sources = [project / relative for relative in INPUTS] + java_files
    return "\n\n".join(f"## {source.relative_to(project)}\n{source.read_text(encoding='utf-8-sig')}" for source in sources)


def generate(model, api_key, context):
    payload = {
        "systemInstruction": {"parts": [{"text": (ACTION / "system.md").read_text(encoding="utf-8")}]},
        "contents": [{"role": "user", "parts": [{"text": context}]}],
        "generationConfig": {
            "maxOutputTokens": 32000,
            "responseMimeType": "application/json",
            "responseJsonSchema": {
                "type": "object",
                "properties": {
                    "files": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {"path": {"type": "string"}, "content": {"type": "string"}},
                            "required": ["path", "content"],
                            "additionalProperties": False,
                        },
                    },
                },
                "required": ["files"],
                "additionalProperties": False,
            },
        },
    }
    request = Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
    )
    try:
        with urlopen(request, timeout=240) as response:
            result = json.load(response)
    except HTTPError as error:
        if error.code == 429:
            raise ValueError("Gemini respondió HTTP 429. Se agotó la cuota o se alcanzó el límite de solicitudes del proyecto. "
                             "Revisa https://aistudio.google.com/rate-limit y vuelve a ejecutar el workflow.") from None
        raise ValueError(f"Gemini respondió HTTP {error.code}. Revisa el acceso al modelo y la configuración del secreto.") from None
    except (URLError, TimeoutError):
        raise ValueError("La conexión a Gemini falló o excedió el tiempo de espera.") from None

    candidates = result.get("candidates") or []
    if not candidates or candidates[0].get("finishReason") != "STOP":
        raise ValueError("Gemini no devolvió una propuesta completa.")
    parts = candidates[0].get("content", {}).get("parts", [])
    text = "".join(part.get("text", "") for part in parts if not part.get("thought", False))
    proposal = json.loads(text)
    if not isinstance(proposal, dict) or not isinstance(proposal.get("files"), list) or not proposal["files"]:
        raise ValueError("Gemini devolvió una propuesta vacía.")
    if len(text.encode("utf-8")) > 260_000:
        raise ValueError("La propuesta excede el límite de tamaño.")
    return proposal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("proposal", type=Path)
    args = parser.parse_args()
    output = Path(os.environ["GITHUB_OUTPUT"])
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        with output.open("a", encoding="utf-8") as stream:
            stream.write("mode=sin-clave\n")
        print("::error::Falta el secreto GEMINI_API_KEY. No se generó el servicio.")
        return 1

    model = os.environ.get("GEMINI_MODEL") or "gemini-3.8-flash"
    try:
        if not re.fullmatch(r"gemini-[a-z0-9.-]+", model):
            raise ValueError("GEMINI_MODEL debe ser un identificador válido de Gemini.")
        proposal = generate(model, api_key, context_for(args.project))
    except (TypeError, AttributeError, json.JSONDecodeError):
        print("::error::Gemini devolvió una respuesta JSON inesperada.")
        return 1
    except ValueError as error:
        print(f"::error::{error}")
        return 1

    args.proposal.parent.mkdir(parents=True, exist_ok=True)
    args.proposal.write_text(json.dumps(proposal, ensure_ascii=False) + "\n", encoding="utf-8")
    with output.open("a", encoding="utf-8") as stream:
        stream.write(f"mode=gemini\nmodel={model}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
