Implementa un microservicio de demostración en Java 25 y Quarkus REST. El contrato OpenAPI y los criterios de aceptación determinan el comportamiento. Las interfaces Jakarta REST y los modelos adjuntos ya los genera Maven.

Entrega todos los archivos Java necesarios para que el proyecto compile y funcione desde cero. El POM, la configuración y las pruebas HTTP externas ya se preparan en el pipeline.

- `demo.bian.domain`: tipos y reglas del dominio en Java puro. Usa `BigDecimal` para importes.
- `demo.bian.application.port.in`: interfaces de los casos de uso.
- `demo.bian.application.port.out`: puertos de almacenamiento que permiten transacciones por agregado.
- `demo.bian.application`: servicios CDI con inyección por constructor. Coordina dominio y puertos sin importar código REST ni adaptadores.
- `demo.bian.adapter.in.rest`: genera una clase concreta distinta por interfaz Jakarta REST adjunta. Respeta la ruta base de cada interfaz, las firmas y los tipos exactos. Mapea los DTO al dominio y el resultado a HTTP. Para enums generados usa exclusivamente las constantes y los métodos públicos que aparecen en las clases adjuntas. Usa mapeadores específicos y conserva los estados HTTP que no sean errores de la aplicación. Para JSON inválido contempla tanto errores de entrada de Jackson como `WebApplicationException` con estado 400. Para Bean Validation contempla `ResteasyReactiveViolationException`.
- `demo.bian.adapter.out.memory`: repositorio CDI en memoria. Conserva un único débito para peticiones concurrentes con la misma clave y protege el saldo al concurrir pagos.
- `src/test/java/demo/bian/domain/` y `src/test/java/demo/bian/application/`: pruebas unitarias JUnit Jupiter que terminan en `Test.java`. Usa aserciones concretas y fakes simples. Estas pruebas no levantan Quarkus. Cubre cada regla de saldo, estados, moneda, búsqueda e idempotencia.

Antes de entregar, revisa los tipos atravesando las capas. `BigDecimal.compareTo` devuelve un entero, así que para detectar montos distintos compáralo con cero. Si un puerto acepta `UnaryOperator<Cuenta>`, su lambda devuelve una cuenta, y el servicio extrae el pago del resultado. Otra opción es declarar un puerto genérico con `Function<Cuenta, T>` y devolver `T` de forma consistente. La precisión de dos decimales se evalúa con `stripTrailingZeros().scale()`.

Usa las extensiones que ya instala la plantilla. Evita red, E/S de archivos, ejecución de procesos, reflexión y llamadas a servicios externos. Trabaja únicamente dentro de los paquetes indicados y usa una declaración `package` que coincida con la ruta.

Devuelve solo el objeto JSON definido en el esquema de respuesta. Cada campo `content` contiene el archivo completo, sin bloques Markdown.
