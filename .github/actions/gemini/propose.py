"""Solicita a Gemini la implementación y sus pruebas unitarias."""

import argparse
import json
import os
import sys
import time
from http.client import IncompleteRead
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ACTION = Path(__file__).resolve().parent
INPUTS = (
    "contracts/bian/openapi.yaml",
    "contracts/bian/current-account.acceptance.md",
)
MODEL_ORDER = ("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite")


class GeminiUnavailable(ValueError):
    """Error en el que puede usarse otro modelo con la misma clave."""


def context_for(project):
    generated = project / "target/generated-sources/openapi/src/main/java/demo/bian/generated"
    java_files = sorted(generated.rglob("*.java"))
    if not java_files:
        raise ValueError("Faltan las interfaces y los modelos generados desde el contrato.")
    sources = [project / relative for relative in INPUTS] + java_files
    return "\n\n".join(f"## {source.relative_to(project)}\n{source.read_text(encoding='utf-8-sig')}" for source in sources)


def read_chunks(response, streaming):
    if not streaming:
        yield json.load(response)
        return
    for line in response:
        if line.startswith(b"data:"):
            data = line.removeprefix(b"data:").strip()
            if data and data != b"[DONE]":
                yield json.loads(data)


def request_proposal(request, streaming):
    for attempt in range(2):
        try:
            with urlopen(request, timeout=165 if streaming else 120) as response:
                text, finish = [], None
                for chunk in read_chunks(response, streaming):
                    if "error" in chunk:
                        raise GeminiUnavailable("El proveedor interrumpió la respuesta.")
                    for candidate in chunk.get("candidates", []):
                        finish = candidate.get("finishReason", finish)
                        parts = candidate.get("content", {}).get("parts", [])
                        text.extend(part.get("text", "") for part in parts if not part.get("thought", False))
                if streaming and finish is None:
                    raise GeminiUnavailable("El proveedor cerró la respuesta antes de completarla.")
                if finish != "STOP":
                    raise ValueError("Gemini no devolvió una propuesta completa.")
                return "".join(text)
        except HTTPError as error:
            if error.code in {500, 502, 503, 504} and attempt == 0:
                print(f"::notice::Gemini respondió HTTP {error.code}. Se hará un reintento.", flush=True)
                time.sleep(6)
                continue
            if error.code == 429:
                raise GeminiUnavailable("HTTP 429. Se agotó la cuota o se alcanzó el límite de solicitudes. "
                                        "Consulta https://aistudio.google.com/rate-limit.") from None
            if error.code in {404, 500, 502, 503, 504}:
                raise GeminiUnavailable(f"HTTP {error.code}. El modelo o el servicio no está disponible.") from None
            raise ValueError(f"Gemini respondió HTTP {error.code}. Revisa el acceso al modelo y la configuración del secreto.") from None
        except (URLError, TimeoutError, IncompleteRead, ConnectionError):
            raise GeminiUnavailable("La conexión falló o excedió el tiempo de espera.") from None


def generate(model, api_key, context):
    streaming = model == "gemini-4-hydrogen"
    payload = {
        "systemInstruction": {"parts": [{"text": (ACTION / "system.md").read_text(encoding="utf-8")}]},
        "contents": [{"role": "user", "parts": [{"text": context}]}],
        "generationConfig": {
            "maxOutputTokens": 48000 if streaming else 32000,
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
    if streaming:
        payload["generationConfig"]["thinkingConfig"] = {"thinkingLevel": "MEDIUM"}
    method = "streamGenerateContent?alt=sse" if streaming else "generateContent"
    request = Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:{method}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
    )
    print(f"Solicitando el microservicio a {model}.", flush=True)
    text = request_proposal(request, streaming)
    proposal = json.loads(text)
    if not isinstance(proposal, dict) or not isinstance(proposal.get("files"), list) or not proposal["files"]:
        raise ValueError("Gemini devolvió una propuesta vacía.")
    if len(text.encode("utf-8")) > 260_000:
        raise ValueError("La propuesta excede el límite de tamaño.")
    return proposal


def propose(api_key, context):
    for model, next_model in zip(MODEL_ORDER, MODEL_ORDER[1:]):
        try:
            return model, generate(model, api_key, context)
        except GeminiUnavailable as error:
            print(f"::warning::{model}: {error} Se intentará con {next_model}.", flush=True)
    model = MODEL_ORDER[-1]
    return model, generate(model, api_key, context)


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

    try:
        model, proposal = propose(api_key, context_for(args.project))
    except (TypeError, AttributeError, json.JSONDecodeError):
        print("::error::Gemini devolvió una respuesta JSON inesperada.")
        return 1
    except ValueError as error:
        print(f"::error::{error}")
        return 1

    args.proposal.parent.mkdir(parents=True, exist_ok=True)
    args.proposal.write_text(json.dumps(proposal, ensure_ascii=False) + "\n", encoding="utf-8")
    with output.open("a", encoding="utf-8") as stream:
        stream.write(f"mode=gemini\nmodel={model}\nprimary={MODEL_ORDER[0]}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
