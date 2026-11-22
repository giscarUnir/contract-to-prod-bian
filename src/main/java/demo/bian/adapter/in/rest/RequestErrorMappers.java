package demo.bian.adapter.in.rest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import static demo.bian.domain.AccountError.Code.SOLICITUD_INVALIDA;

public class RequestErrorMappers {
    @ServerExceptionMapper({
            BadRequestException.class, JsonParseException.class, MismatchedInputException.class,
            ResteasyReactiveViolationException.class
    })
    public Response invalidRequest() {
        return AccountErrorMapper.response(400, SOLICITUD_INVALIDA);
    }
}
