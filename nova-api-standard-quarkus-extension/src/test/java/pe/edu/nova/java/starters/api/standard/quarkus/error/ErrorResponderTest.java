package pe.edu.nova.java.starters.api.standard.quarkus.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.DomainError;
import pe.edu.nova.java.libs.api.standard.error.ErrorPorts;
import pe.edu.nova.java.libs.api.standard.error.FieldError;
import pe.edu.nova.java.libs.api.standard.error.InfrastructureError;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorCatalog;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorStatusMapper;
import pe.edu.nova.java.libs.api.standard.error.SerializedError;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/** El núcleo responde con los puertos, cuenta cada error y deja el MDC como lo encontró. */
class ErrorResponderTest {

    private final List<String> counted = new ArrayList<>();
    private final ErrorResponder responder = new ErrorResponder(ErrorPorts.defaults(),
            (layer, code) -> counted.add(layer + ":" + code));

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void aNovaErrorIsAnsweredWithTheStatusOfItsTypeAndItsOwnCode() {
        Response response = responder.respond(DomainError.notFound("ORDER_NOT_FOUND", "El pedido 42 no existe"));

        assertEquals(404, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertFalse(body.success());
        assertEquals(404, body.status());
        assertEquals("ORDER_NOT_FOUND", body.errors().get(0).code());
        assertEquals("El pedido 42 no existe", body.errors().get(0).message());
    }

    @Test
    void theFieldErrorsOfAnInvalidInputArriveOneByOne() {
        Response response = responder.respond(ApplicationError.invalidInput("La solicitud tiene campos inválidos",
                List.of(FieldError.of("email", "El correo no es válido"), FieldError.of("quantity", "Debe ser positiva"))));

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals(400, response.getStatus());
        assertEquals(List.of("email", "quantity"), body.errors().stream().map(error -> error.field()).toList());
    }

    @Test
    void theRetryAfterOfTheErrorBecomesAHeader() {
        Response response = responder.respond(ApplicationError.rateLimited("Superaste el límite", Duration.ofSeconds(30)));

        assertEquals(429, response.getStatus());
        assertEquals("30", response.getHeaderString("Retry-After"));
    }

    @Test
    void anIncidentNeverShowsItsUpstreamNorItsMessage() {
        Response response = responder.respond(
                InfrastructureError.timeout("courier-acme", new RuntimeException("courier-acme.interno:8443")));

        assertEquals(504, response.getStatus());
        String body = response.getEntity().toString();
        assertFalse(body.contains("courier-acme"), body);
        assertFalse(body.contains("8443"), body);
        assertEquals("GATEWAY_TIMEOUT", ((ApiResponse<?>) response.getEntity()).errors().get(0).code());
    }

    @Test
    void anUnexpectedExceptionIsAPlatformErrorWithTheGenericMessage() {
        Response response = responder.respondUnexpected(new IllegalArgumentException("clave interna 123"));

        assertEquals(500, response.getStatus());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals("INTERNAL_SERVER_ERROR", body.errors().get(0).code());
        assertEquals("Error interno del servidor", body.errors().get(0).message());
        assertEquals(List.of("platform:INTERNAL_SERVER_ERROR"), counted);
    }

    @Test
    void eachResponseIsCountedByLayerAndCode() {
        responder.respond(DomainError.notFound("ORDER_NOT_FOUND", "no existe"));
        responder.respondFramework(503, null, List.of(), null, new RuntimeException("Acme"));

        assertEquals(List.of("domain:ORDER_NOT_FOUND", "infrastructure:SERVICE_UNAVAILABLE"), counted);
    }

    @Test
    void withoutATraceIdOneIsGeneratedForTheBodyAndItLeavesTheMdc() {
        Response response = responder.respond(DomainError.conflict("El pedido está cancelado"));

        String traceId = ((ApiResponse<?>) response.getEntity()).metadata().traceId();
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertNull(MDC.get("traceId"), "el id generado sale del MDC al terminar");
    }

    @Test
    void theTraceIdOfTheRequestIsTheOneInTheBodyAndStaysInTheMdc() {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

        Response response = responder.respond(DomainError.conflict("El pedido está cancelado"));

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ((ApiResponse<?>) response.getEntity()).metadata().traceId());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get("traceId"));
    }

    @Test
    void theFieldsOfTheLogNeverStayInTheMdc() {
        MDC.put("layer", "previous");

        responder.respond(InfrastructureError.unavailable("inventario", null));

        assertEquals("previous", MDC.get("layer"), "lo que había se devuelve");
        assertNull(MDC.get("code"));
        assertNull(MDC.get("status"));
        assertNull(MDC.get("upstream"));
    }

    @Test
    void theHeadersOfAFrameworkExceptionSurviveExceptThoseThatDescribeItsBody() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("Allow", "GET, POST");
        headers.add("Content-Type", "text/plain");
        headers.add("Content-Length", "11");

        Response response = responder.respondFramework(405, null, List.of(), headers, new RuntimeException());

        assertEquals(405, response.getStatus());
        assertEquals("GET, POST", response.getHeaderString("Allow"));
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertNull(response.getHeaderString("Content-Length"));
    }

    @Test
    void theHeadersOfTheSerializerWinOverThoseOfTheException() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("retry-after", "120");
        ErrorResponder custom = new ErrorResponder(
                new ErrorPorts(new NovaErrorStatusMapper(), new NovaErrorCatalog(),
                        failure -> new SerializedError(failure.status(), "{}", Map.of("Retry-After", "5"))),
                (layer, code) -> { });

        Response response = custom.respondFramework(503, null, List.of(), headers, new RuntimeException());

        assertEquals(List.of("5"), response.getStringHeaders().get("Retry-After"));
    }

    @Test
    void anotherFormatDeclaresItsOwnContentType() {
        ErrorResponder custom = new ErrorResponder(
                new ErrorPorts(new NovaErrorStatusMapper(), new NovaErrorCatalog(),
                        failure -> new SerializedError(failure.status(), Map.of("title", failure.code()),
                                Map.of("Content-Type", "application/problem+json"))),
                (layer, code) -> { });

        Response response = custom.respond(DomainError.notFound("ORDER_NOT_FOUND", "no existe"));

        assertEquals("application/problem+json", response.getMediaType().toString());
    }
}
