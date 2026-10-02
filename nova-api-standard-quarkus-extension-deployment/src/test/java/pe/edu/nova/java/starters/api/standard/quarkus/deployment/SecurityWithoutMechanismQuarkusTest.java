package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Un servicio con Quarkus Security pero sin mecanismo de autenticación no tiene reto que poner. El autenticador
 * de Quarkus contesta ahí un 403 sin headers; la extensión mantiene el 401 de siempre, con el sobre de Nova y sin
 * {@code WWW-Authenticate}, y no falla por buscar un reto que no existe.
 */
class SecurityWithoutMechanismQuarkusTest {

    private static final String CHALLENGE = "WWW-Authenticate";

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(SecurityResource.class));

    @Test
    void aMissingIdentityIsUnauthorizedWithoutAChallenge() {
        Response response = given().get("/secure/unauthorized");

        envelope(response, 401)
                .body("errors[0].code", equalTo("UNAUTHORIZED"))
                .body("errors[0].message", equalTo("Hace falta autenticarse"));
        assertNull(response.getHeader(CHALLENGE));
    }

    @Test
    void anIdentityThatCouldNotBeAuthenticatedIsUnauthorizedWithoutAChallenge() {
        Response response = given().get("/secure/authentication-failed");

        envelope(response, 401).body("errors[0].code", equalTo("UNAUTHORIZED"));
        assertNull(response.getHeader(CHALLENGE));
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
