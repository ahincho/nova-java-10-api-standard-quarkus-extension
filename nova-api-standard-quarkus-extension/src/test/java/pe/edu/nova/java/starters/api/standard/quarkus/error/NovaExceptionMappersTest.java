package pe.edu.nova.java.starters.api.standard.quarkus.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.edu.nova.java.libs.api.standard.error.DomainError;
import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/** Cada excepción se lee por lo que es, sin levantar Quarkus. */
class NovaExceptionMappersTest {

    private static final String UNREADABLE_BODY = "No se pudo leer el cuerpo de la solicitud";

    private final NovaExceptionMappers mappers = new NovaExceptionMappers(
            new ErrorResponder(ErrorPorts.defaults(), ErrorCounter.none()));

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void aNovaErrorKeepsTheStatusOfItsType() {
        assertEquals(409, mappers.novaError(DomainError.conflict("El pedido está cancelado")).getStatus());
    }

    @Test
    void aWebApplicationExceptionIsReadByItsStatus() {
        Response response = mappers.webApplication(new NotFoundException("no hay"));

        assertEquals(404, response.getStatus());
        assertEquals("NOT_FOUND", code(response));
        assertEquals("El recurso no existe", message(response));
    }

    @Test
    void aRedirectionIsNotAnErrorAndIsReturnedAsItIs() {
        RedirectionException redirect = new RedirectionException(Response.Status.SEE_OTHER, URI.create("/otra"));

        assertSame(redirect.getResponse(), mappers.webApplication(redirect));
    }

    @Test
    void aBadRequestThatWrapsAJacksonErrorIsAnUnreadableBody() {
        WebApplicationException malformed = new WebApplicationException(
                new JsonParseException(null, "Unexpected end-of-input"), Response.Status.BAD_REQUEST);

        Response response = mappers.webApplication(malformed);

        assertEquals(400, response.getStatus());
        assertEquals(UNREADABLE_BODY, message(response));
    }

    @Test
    void theJacksonErrorCanBeBuriedUnderOtherCauses() {
        WebApplicationException malformed = new WebApplicationException(
                new IOException("leyendo", new IOException("más adentro", new JsonParseException(null, "x"))),
                Response.Status.BAD_REQUEST);

        assertEquals(UNREADABLE_BODY, message(mappers.webApplication(malformed)));
    }

    @Test
    void aBadRequestWithoutAJacksonCauseKeepsTheMessageOfTheCatalog() {
        Response response = mappers.webApplication(new WebApplicationException("el campo xyz", 400));

        assertEquals("La solicitud no es válida", message(response));
    }

    @Test
    void aJacksonErrorOnAServerErrorIsNotAnUnreadableBody() {
        WebApplicationException broken = new WebApplicationException(
                new JsonParseException(null, "x"), Response.Status.INTERNAL_SERVER_ERROR);

        Response response = mappers.webApplication(broken);

        assertEquals(500, response.getStatus());
        assertEquals("Error interno del servidor", message(response));
    }

    @Test
    void aBodyThatDoesNotMatchWhatTheResourceExpectsIsAnUnreadableBody() {
        Response response = mappers.unreadableBody(MismatchedInputException.from(null, String.class, "No content"));

        assertEquals(400, response.getStatus());
        assertEquals("BAD_REQUEST", code(response));
        assertEquals(UNREADABLE_BODY, message(response));
    }

    @Test
    void anyOtherExceptionIsAPlatformErrorEvenAnIllegalArgumentOrASecurityException() {
        for (Throwable exception : List.of(
                new IllegalArgumentException("entrada inválida"), new SecurityException("sin permiso"))) {
            Response response = mappers.unexpected(exception);

            assertEquals(500, response.getStatus(), exception.toString());
            assertEquals("INTERNAL_SERVER_ERROR", code(response));
            assertEquals("Error interno del servidor", message(response));
        }
    }

    private static String code(Response response) {
        return ((ApiResponse<?>) response.getEntity()).errors().get(0).code();
    }

    private static String message(Response response) {
        return ((ApiResponse<?>) response.getEntity()).errors().get(0).message();
    }
}
