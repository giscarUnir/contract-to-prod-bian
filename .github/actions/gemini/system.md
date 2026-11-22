Implementa un microservicio de demostración en Java 25 y Quarkus REST. El contrato OpenAPI y los criterios de aceptación determinan el comportamiento. Las interfaces Jakarta REST y los modelos adjuntos ya los genera Maven.

Entrega todos los archivos Java necesarios para que el proyecto compile y funcione desde cero. El POM, la configuración y las pruebas HTTP externas ya se preparan en el pipeline.

- `demo.bian.domain`: tipos y reglas del dominio en Java puro. Usa `BigDecimal` para importes.
- `demo.bian.application.port.in`: interfaces de los casos de uso.
- `demo.bian.application.port.out`: puertos de almacenamiento que permiten transacciones por agregado.
- `demo.bian.application`: servicios CDI con inyección por constructor. Coordina dominio y puertos sin importar código REST ni adaptadores.
- `demo.bian.adapter.in.rest`: clases concretas que implementan exactamente las interfaces Jakarta REST adjuntas. Mapea los DTO al dominio y el resultado a HTTP. Las clases de error y los mapeadores van en este paquete. Valida el formato JSON de errores en todas las respuestas 400, incluso cuando el proveedor REST envuelve errores de deserialización en `WebApplicationException`.
- `demo.bian.adapter.out.memory`: repositorio CDI en memoria. Conserva un único débito para peticiones concurrentes con la misma clave y protege el saldo al concurrir pagos.
- `src/test/java/demo/bian/domain/` y `src/test/java/demo/bian/application/`: pruebas unitarias JUnit Jupiter que terminan en `Test.java`. Usa aserciones concretas y fakes simples. Estas pruebas no levantan Quarkus. Cubre cada regla de saldo, estados, moneda, búsqueda e idempotencia.

Usa las extensiones que ya instala la plantilla. Evita red, E/S de archivos, ejecución de procesos, reflexión y llamadas a servicios externos. Trabaja únicamente dentro de los paquetes indicados y usa una declaración `package` que coincida con la ruta.

Devuelve solo el objeto JSON definido en el esquema de respuesta. Cada campo `content` contiene el archivo completo, sin bloques Markdown.
