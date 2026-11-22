"""Solicita una sola clase Java a Gemini y escribe la propuesta como artefacto."""

import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CONTEXT = [
    "contracts/bian/openapi.yaml",
    "contracts/bian/current-account.acceptance.md",
    "src/main/java/demo/bian/application/CurrentAccounts.java",
    "src/main/java/demo/bian/application/AccountStore.java",
    "src/main/java/demo/bian/domain/Account.java",
    "src/main/java/demo/bian/domain/AccountError.java",
]


def generate(model, api_key):
    context = "\n\n".join(f"## {name}\n{(ROOT / name).read_text(encoding='utf-8')}" for name in CONTEXT)
    payload = {
        "systemInstruction": {"parts": [{"text": (ROOT / "ai/system.md").read_text(encoding="utf-8")}]},
        "contents": [{"role": "user", "parts": [{"text": context}]}],
        "generationConfig": {
            "maxOutputTokens": 8000,
            "responseMimeType": "application/json",
            "responseJsonSchema": {
                "type": "object",
                "properties": {"source": {"type": "string"}},
                "required": ["source"],
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
        with urlopen(request, timeout=120) as response:
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
    source = json.loads(text).get("source")
    if not isinstance(source, str) or not source.strip() or len(source) > 32_000:
        raise ValueError("Gemini devolvió una clase vacía o fuera del tamaño permitido.")
    return source


def main():
    output = Path(os.environ["GITHUB_OUTPUT"])
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        with output.open("a", encoding="utf-8") as stream:
            stream.write("mode=sin-clave\n")
        print("::notice::Falta el secreto GEMINI_API_KEY. Se comprueba la implementación de referencia.")
        if os.environ.get("REQUIRE_GEMINI") == "true":
            return 1
        return 0

    model = os.environ.get("GEMINI_MODEL") or "gemini-3.5-flash-lite"
    try:
        if not re.fullmatch(r"gemini-[a-z0-9.-]+", model):
            raise ValueError("GEMINI_MODEL debe ser un identificador válido de Gemini.")
        source = generate(model, api_key)
    except (TypeError, AttributeError, json.JSONDecodeError):
        print("::error::Gemini devolvió una respuesta JSON inesperada.")
        return 1
    except ValueError as error:
        print(f"::error::{error}")
        return 1

    artifact = Path("proposal/CurrentAccountService.java")
    artifact.parent.mkdir(exist_ok=True)
    artifact.write_text(source.rstrip() + "\n", encoding="utf-8")
    with output.open("a", encoding="utf-8") as stream:
        stream.write(f"mode=gemini\nmodel={model}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
