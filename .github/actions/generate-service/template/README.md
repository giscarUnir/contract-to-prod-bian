# BIAN Current Account

Microservicio Java 25 y Quarkus generado desde el contrato ficticio de Current Account. Maven genera las interfaces REST y los modelos OpenAPI durante el build. El almacenamiento en memoria permite iniciar la API sin servicios externos.

```sh
mvn verify
mvn quarkus:dev
```

Abre [Swagger UI](http://localhost:8080/q/swagger-ui/) y crea una cuenta con `POST /CurrentAccount/Initiate`:

```json
{"CustomerReference":"DEMO","AccountCurrency":"PEN","InitialDepositAmount":100}
```

El paquete ejecutable se crea en `target/quarkus-app/`. Puedes arrancarlo con `java -jar target/quarkus-app/quarkus-run.jar`. Las cuentas se reinician con el proceso.
