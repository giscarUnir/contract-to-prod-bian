package demo.bian.adapter.in.rest;

import demo.bian.domain.AccountError;
import demo.bian.generated.model.Error;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

@Provider
public class AccountErrorMapper implements ExceptionMapper<AccountError> {
    @Override
    public Response toResponse(AccountError failure) {
        int status = switch (failure.code()) {
            case SOLICITUD_INVALIDA -> 400;
            case CUENTA_NO_EXISTE, PAGO_NO_EXISTE -> 404;
            case TRANSICION_INVALIDA, CUENTA_SUSPENDIDA, MONEDA_DISTINTA, SALDO_INSUFICIENTE, CLAVE_REUTILIZADA -> 422;
        };
        return response(status, failure.code());
    }

    static Response response(int status, AccountError.Code code) {
        return Response.status(status)
                .type(APPLICATION_JSON_TYPE)
                .entity(new Error().code(code.name()).message(code.message()))
                .build();
    }
}
