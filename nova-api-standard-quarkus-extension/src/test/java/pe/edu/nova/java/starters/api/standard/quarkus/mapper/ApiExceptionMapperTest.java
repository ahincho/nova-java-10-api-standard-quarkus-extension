package pe.edu.nova.java.starters.api.standard.quarkus.mapper;

import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios del {@link ApiExceptionMapper}.
 * <p>
 * Estos tests validan la logica de mapeo Throwable -&gt; Response sin requerir
 * un contexto Quarkus corriendo. La validacion end-to-end (que el mapper se
 * descubre via CDI/Jandex) se hace en {@code examples/code-with-quarkus}.
 */
class ApiExceptionMapperTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void illegalArgumentExceptionMapsToBadRequestWithBadRequestCode() {
        Response response = mapper.toResponse(new IllegalArgumentException("field 'email' is invalid"));

        assertEquals(400, response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertNotNull(body);
        assertEquals(false, body.success());
        assertEquals(400, body.status());
        assertNull(body.data());
        assertEquals(1, body.errors().size());
        assertEquals("BAD_REQUEST", body.errors().get(0).code());
        assertEquals("field 'email' is invalid", body.errors().get(0).message());
    }

    @Test
    void securityExceptionMapsToForbiddenWithForbiddenCode() {
        Response response = mapper.toResponse(new SecurityException("access denied"));

        assertEquals(403, response.getStatus());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals(false, body.success());
        assertEquals(403, body.status());
        assertEquals("FORBIDDEN", body.errors().get(0).code());
        assertEquals("access denied", body.errors().get(0).message());
    }

    @Test
    void unexpectedRuntimeExceptionMapsToInternalErrorWithoutLeakingDetails() {
        Response response = mapper.toResponse(new RuntimeException("secret stack trace info"));

        assertEquals(500, response.getStatus());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals(false, body.success());
        assertEquals(500, body.status());
        assertEquals("INTERNAL_ERROR", body.errors().get(0).code());
        // Mensaje interno NUNCA se expone al cliente
        assertEquals("Internal server error", body.errors().get(0).message());
        assertTrue(!body.errors().get(0).message().contains("secret stack trace"));
    }

    @Test
    void nullPointerExceptionMapsToInternalError() {
        Response response = mapper.toResponse(new NullPointerException("npe"));

        assertEquals(500, response.getStatus());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals("INTERNAL_ERROR", body.errors().get(0).code());
    }

    @Test
    void exceptionWithoutMessageFallsBackToClassName() {
        Response response = mapper.toResponse(new IllegalArgumentException());

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals("BAD_REQUEST", body.errors().get(0).code());
        // Sin mensaje, usamos el nombre de la clase como fallback
        assertEquals("IllegalArgumentException", body.errors().get(0).message());
    }

    @Test
    void subclassOfIllegalArgumentExceptionIsAlsoBadRequest() {
        Response response = mapper.toResponse(new NumberFormatException("not a number"));

        assertEquals(400, response.getStatus());
        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertEquals("BAD_REQUEST", body.errors().get(0).code());
        assertEquals("not a number", body.errors().get(0).message());
    }

    @Test
    void responseEntityIsApiResponseRecord() {
        Response response = mapper.toResponse(new RuntimeException());

        Object entity = response.getEntity();
        assertNotNull(entity);
        assertTrue(entity instanceof ApiResponse);
    }
}