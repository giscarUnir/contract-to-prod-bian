"""Arranca el paquete Quarkus y comprueba sus endpoints en el runner."""

import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

target = Path(sys.argv[1]) / "target"
host = "http://127.0.0.1:18165"
with (target / "smoke.log").open("wb") as log:
    service = subprocess.Popen(
        ["java", "-Dquarkus.http.host=127.0.0.1", "-Dquarkus.http.port=18165",
         "-jar", str(target / "quarkus-app/quarkus-run.jar")],
        stdout=log, stderr=subprocess.STDOUT,
    )
    try:
        for attempt in range(40):
            try:
                with urlopen(host + "/q/health/ready", timeout=2) as response:
                    if json.load(response)["status"] == "UP":
                        break
            except URLError:
                if service.poll() is not None:
                    raise
            time.sleep(0.5)
        else:
            raise SystemExit("La API no alcanzó el estado UP.")

        request = Request(
            host + "/CurrentAccount/Initiate",
            data=b'{"CustomerReference":"SMOKE","AccountCurrency":"PEN","InitialDepositAmount":100}',
            headers={"Content-Type": "application/json"},
        )
        with urlopen(request, timeout=5) as response:
            if response.status != 201 or json.load(response)["AccountBalance"] != 100:
                raise SystemExit("El paquete no abrió la cuenta esperada.")
        with urlopen(host + "/q/swagger-ui/", timeout=5) as response:
            if response.status != 200 or b"swagger-ui-bundle.js" not in response.read():
                raise SystemExit("El paquete no sirve Swagger UI.")
        print("Paquete Quarkus listo. Apertura de cuenta y Swagger UI verificados.")
    finally:
        service.terminate()
        try:
            service.wait(timeout=5)
        except subprocess.TimeoutExpired:
            service.kill()
            service.wait()
