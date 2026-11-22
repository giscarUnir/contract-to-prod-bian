# Contrato a código · BIAN Current Account

De un contrato OpenAPI bancario ficticio a una API Java 25 y Quarkus con pruebas de aceptación e informe en el pull request.

El contrato define cinco operaciones de cuentas y pagos. Maven genera las interfaces Jakarta REST y los DTO. Una implementación de referencia permite ejecutar la demo con almacenamiento en memoria. Cuando `GEMINI_API_KEY` está configurado, Gemini propone la clase de aplicación y el pipeline la comprueba con la misma suite HTTP en otro job.

## Ejecutar en local

Con Java 25 y Maven, ejecuta:

```sh
mvn verify
mvn quarkus:dev
```

Abre [Swagger UI](http://localhost:8080/q/swagger-ui). Prueba `POST /CurrentAccount/Initiate` con este cuerpo:

```json
{
  "CustomerReference": "DEMO-CLIENTE",
  "AccountCurrency": "PEN",
  "InitialDepositAmount": 100
}
```

Copia la referencia devuelta y úsala en `GET /CurrentAccount/{currentaccountid}/Retrieve`. Las cuentas viven en memoria hasta que reinicias el servicio.

## Ejecutar con Gemini

Crea una clave en [Google AI Studio](https://aistudio.google.com/apikey) y guárdala como secreto de repositorio `GEMINI_API_KEY` en **Settings → Secrets and variables → Actions**. Abre un PR desde una rama del mismo repositorio o ejecuta **Contrato a código** desde Actions. Puedes configurar `GEMINI_MODEL` como variable de repositorio. El valor por defecto es `gemini-3.5-flash-lite`.

El informe enlaza los resultados de Spectral, oasdiff, Gitleaks, pruebas HTTP, JaCoCo y OSV-Scanner. Las propuestas de Gemini se descargan como artefacto para revisión humana.
