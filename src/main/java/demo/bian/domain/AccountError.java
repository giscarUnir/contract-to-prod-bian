package demo.bian.domain;

public final class AccountError extends RuntimeException {
    public enum Code {
        SOLICITUD_INVALIDA("La solicitud contiene datos inválidos."),
        CUENTA_NO_EXISTE("La cuenta no existe."),
        PAGO_NO_EXISTE("El pago no existe en esta cuenta."),
        TRANSICION_INVALIDA("La cuenta ya se encuentra en el estado solicitado."),
        CUENTA_SUSPENDIDA("La cuenta está suspendida."),
        MONEDA_DISTINTA("La moneda del pago no coincide con la de la cuenta."),
        SALDO_INSUFICIENTE("El saldo es insuficiente."),
        CLAVE_REUTILIZADA("La clave de idempotencia ya se usó para otro pago.");

        private final String message;

        Code(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Code code;

    public AccountError(Code code) {
        super(code.message());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
