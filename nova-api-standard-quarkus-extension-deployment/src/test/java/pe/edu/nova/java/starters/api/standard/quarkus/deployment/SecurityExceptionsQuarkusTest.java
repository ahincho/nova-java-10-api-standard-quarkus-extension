package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.starters.api.standard.quarkus.deployment.RecordingLogHandler.Line;

/**
 * Las excepciones de Quarkus Security que llegan a REST no extienden {@code WebApplicationException}, pero tienen
 * un status inequívoco y se leen igual (ADR-050): una identidad que falta o no es válida es un 401 y una
 * identidad sin permiso es un 403, las dos de la capa {@code application}. Todas extienden
 * {@code SecurityException}, que por sí sola sería un 500.
 */
class SecurityExceptionsQuarkusTest {

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(SecurityResource.class, RecordingLogHandler.class, Line.class));

    @BeforeAll
    static void captureTheLog() {
        LOG.attach();
    }

    @AfterAll
    static void releaseTheLog() {
        LOG.detach();
    }

    @BeforeEach
    void forgetEarlierLines() {
        LOG.clear();
    }

    @Test
    void aMissingIdentityIsUnauthorized() {
        envelope(given().get("/secure/unauthorized"), 401)
                .body("errors[0].code", equalTo("UNAUTHORIZED"))
                .body("errors[0].message", equalTo("Hace falta autenticarse"))
                .body(not(containsString("Acme")));
    }

    @Test
    void anIdentityThatCouldNotBeAuthenticatedIsUnauthorized() {
        envelope(given().get("/secure/authentication-failed"), 401)
                .body("errors[0].code", equalTo("UNAUTHORIZED"))
                .body(not(containsString("Acme")));
    }

    @Test
    void anIdentityWithoutPermissionIsForbidden() {
        envelope(given().get("/secure/forbidden"), 403)
                .body("errors[0].code", equalTo("FORBIDDEN"))
                .body("errors[0].message", equalTo("No hay permiso para esta operación"))
                .body(not(containsString("Acme")));
    }

    @Test
    void theyAreExpectedErrorsOfTheApplicationLayer() {
        given().get("/secure/unauthorized");
        given().get("/secure/forbidden");

        var lines = LOG.lines();
        assertEquals(2, lines.size(), lines.toString());
        for (Line line : lines) {
            assertTrue(line.isWarning(), "una identidad que falta o sin permiso es esperada: " + line);
            assertNull(line.thrown());
            assertEquals("application", line.mdc().get("layer"));
        }
        assertEquals("401", lines.get(0).mdc().get("status"));
        assertEquals("403", lines.get(1).mdc().get("status"));
    }

    private static ValidatableResponse envelope(Response response, int status) {
        return response.then()
                .statusCode(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("success", is(false))
                .body("status", is(status))
                .body("metadata.traceId", notNullValue());
    }
}
