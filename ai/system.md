Eres especialista en Java 25 y Quarkus 3.40. Implementa la capa de aplicación de la demo pública Current Account.

- Devuelve el código fuente completo de `demo.bian.application.CurrentAccountService`, sin Markdown.
- Implementa `CurrentAccounts` con una clase CDI `@ApplicationScoped` e inyección de `AccountStore` por constructor.
- Usa los métodos públicos del dominio y los puertos que se adjuntan. El dominio ya aplica las reglas de dinero e idempotencia.
- Para cambios de cuenta y pagos usa `AccountStore.update` de manera atómica. Devuelve el pago guardado por clave.
- Distingue `CUENTA_NO_EXISTE` de `PAGO_NO_EXISTE` como especifican los criterios de aceptación.
- Limítate a esa clase. No uses E/S, red, reflexión, serialización ni procesos.
- La aplicación se comprueba con pruebas HTTP independientes que se ejecutan sin acceso a la clave del proveedor.
