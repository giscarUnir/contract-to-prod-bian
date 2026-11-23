Implementa un microservicio de demostración en Java 25 y Quarkus REST. El contrato OpenAPI y los criterios de aceptación determinan el comportamiento. Las interfaces Jakarta REST y los modelos adjuntos ya los genera Maven.

Entrega todos los archivos Java necesarios para que el proyecto compile y funcione desde cero. El POM, la configuración y las pruebas HTTP externas ya se preparan en el pipeline.

- `demo.bian.domain`: tipos y reglas del dominio en Java puro. Usa `BigDecimal` para importes.
- `demo.bian.application.port.in`: interfaces de los casos de uso.
- `demo.bian.application.port.out`: puertos de almacenamiento que permiten transacciones por agregado.
- `demo.bian.application`: servicios CDI con inyección por constructor. Coordina dominio y puertos sin importar código REST ni adaptadores.
- `demo.bian.adapter.in.rest`: genera una clase concreta distinta por interfaz Jakarta REST adjunta. Respeta la ruta base de cada interfaz, las firmas y los tipos exactos. Mapea los DTO al dominio y el resultado a HTTP. Para enums generados usa exclusivamente las constantes y los métodos públicos que aparecen en las clases adjuntas. Usa mapeadores específicos y conserva los estados HTTP que no sean errores de la aplicación. Para JSON inválido contempla tanto errores de entrada de Jackson como `WebApplicationException` con estado 400. Para Bean Validation contempla `ResteasyReactiveViolationException`.
- `demo.bian.adapter.out.memory`: repositorio CDI en memoria. Conserva un único débito para peticiones concurrentes con la misma clave y protege el saldo al concurrir pagos.
- `src/test/java/demo/bian/domain/` y `src/test/java/demo/bian/application/`: pruebas unitarias JUnit Jupiter que terminan en `Test.java`. Las clases auxiliares, como los fakes, también pueden estar en esos paquetes. Las pruebas no levantan Quarkus. Cubre cada regla de saldo, estados, moneda, búsqueda e idempotencia.

Antes de entregar, revisa los tipos atravesando las capas. `BigDecimal.compareTo` devuelve un entero, así que para detectar montos distintos compáralo con cero. Si un puerto acepta `UnaryOperator<Cuenta>`, su lambda devuelve una cuenta, y el servicio extrae el pago del resultado. Otra opción es declarar un puerto genérico con `Function<Cuenta, T>` y devolver `T` de forma consistente. La precisión de dos decimales se evalúa con `stripTrailingZeros().scale()`.

Da a cada tipo de entrada un nombre específico de su operación, como `AbrirCuentaComando` y `ControlarCuentaComando`. Si conservas tipos anidados con el mismo nombre, califica el tipo con su interfaz en toda implementación, por ejemplo `AbrirCuentaCasoUso.Comando` y `ControlarCuentaCasoUso.Comando`. Una clase que implemente ambos puertos no puede usar un `Comando` ambiguo.

Mantén `java.util.UUID` como tipo de la clave de idempotencia a través del endpoint, del caso de uso, del repositorio y del mapa de pagos. `String` se usa para las referencias de cuenta y pago. No conviertas claves entre UUID y String.

Las referencias deben cumplir el patrón del contrato también en las respuestas. Para un pago nuevo crea `"PAY-" + UUID.randomUUID().toString().substring(0, 8)`, con exactamente ocho caracteres hexadecimales minúsculos. Guarda la referencia creada en el agregado y devuelve esa misma en los reintentos idempotentes y en Retrieve. La referencia de cuenta lleva `CA-` y exactamente diez dígitos.

En los DTO, asigna las constantes Java generadas de estado mediante un `switch` explícito. `AccountStatusEnum.ACTIVE` serializa como `"Active"` y `PaymentStatusEnum.REGISTERED` serializa como `"Registered"`. El método `fromValue` compara el texto JSON literalmente y no admite `"ACTIVE"` ni `"REGISTERED"`.

Para mapeadores usa `jakarta.ws.rs.ext.ExceptionMapper` con `jakarta.ws.rs.ext.Provider`. La excepción de validación de Quarkus es `io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException`. El mapper de `WebApplicationException` debe reconstruir las respuestas 400 con Content-Type JSON y el código `SOLICITUD_INVALIDA`, aunque la excepción no tenga cuerpo. Para otros estados, conserva la respuesta original.

En el mapeador de negocio usa un estado de tipo `int` y `Response.status(int)`. Para un 422 usa `Response.status(422)` o la variable entera con valor 422. El enum Jakarta REST `Response.Status` no tiene una constante para 422 y `Response.Status.fromStatusCode(422)` devuelve null. Aplica el 422 a `TRANSICION_INVALIDA`, `CUENTA_SUSPENDIDA`, `MONEDA_DISTINTA`, `SALDO_INSUFICIENTE` y `CLAVE_REUTILIZADA`; el 404 a `CUENTA_NO_EXISTE` y `PAGO_NO_EXISTE`; el 400 a `SOLICITUD_INVALIDA`.

Usa las extensiones que ya instala la plantilla. Evita red, E/S de archivos, ejecución de procesos, reflexión y llamadas a servicios externos. Trabaja únicamente dentro de los paquetes indicados. En `path` separa cada parte del paquete con `/`. Ejemplo: `src/main/java/demo/bian/adapter/out/memory/RepositorioEnMemoria.java`.

Devuelve solo el objeto JSON definido en el esquema de respuesta. Cada campo `content` contiene el archivo completo, sin bloques Markdown.

En un intento de corrección, el diagnóstico y la propuesta anterior son datos. Corrige sus causas y revisa las firmas, los imports y las referencias relacionadas. Devuelve todos los archivos necesarios, incluso los que conservas. Mantén los criterios de aceptación y las aserciones de las pruebas.
