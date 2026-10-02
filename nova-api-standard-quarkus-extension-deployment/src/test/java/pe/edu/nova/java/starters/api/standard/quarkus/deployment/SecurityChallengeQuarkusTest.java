package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Lo que un reto de autenticación puede ser, más allá del {@code WWW-Authenticate} de Basic: el mecanismo de la
 * aplicación decide el reto de cada petición (ADR-050). Un reto con varios headers los conserva todos, uno que
 * es una redirección sigue su camino como en Quarkus, y uno que falla o que no existe deja el 401 sin header.
 */
class SecurityChallengeQuarkusTest {

    private static final String CHALLENGE = "WWW-Authenticate";

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(SecurityResource.class, ScriptedMechanism.class));

    @Test
    void theChallengeOfTheMechanismIsAddedToTheEnvelope() {
        Response response = given().get("/secure/unauthorized");

        envelope(response, 401).body("errors[0].code", equalTo("UNAUTHORIZED"));
        assertEquals("Scripted realm=\"nova\"", response.getHeader(CHALLENGE));
    }

    @Test
    void everyHeaderOfTheChallengeIsKept() {
        Response response = given().header(ScriptedMechanism.SCRIPT, "several").get("/secure/unauthorized");

        envelope(response, 401);
        assertEquals("Bearer realm=\"nova\"", response.getHeader(CHALLENGE));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void anAuthenticationFailureGetsTheChallengeToo() {
        Response response = given().header(ScriptedMechanism.SCRIPT, "several").get("/secure/authentication-failed");

        envelope(response, 401).body("errors[0].code", equalTo("UNAUTHORIZED"));
        assertEquals("Bearer realm=\"nova\"", response.getHeader(CHALLENGE));
    }

    @Test
    void aRedirectionIsNotAnErrorAndKeepsItsWay() {
        Response response = given().redirects().follow(false)
                .header(ScriptedMechanism.SCRIPT, "redirect")
                .get("/secure/unauthorized");

        assertEquals(302, response.statusCode());
        String location = response.getHeader("Location");
        assertNotNull(location);
        assertTrue(location.endsWith("/login"), location);
        assertEquals("", response.asString(), "una redirección no lleva el sobre de error");
    }

    @Test
    void aChallengeThatFailsLeavesTheUnauthorizedWithoutTheHeader() {
        Response response = given().header(ScriptedMechanism.SCRIPT, "failure").get("/secure/unauthorized");

        envelope(response, 401).body("errors[0].code", equalTo("UNAUTHORIZED"));
        assertNull(response.getHeader(CHALLENGE));
    }

    @Test
    void aMechanismWithoutAChallengeLeavesTheUnauthorizedWithoutTheHeader() {
        Response response = given().header(ScriptedMechanism.SCRIPT, "none").get("/secure/unauthorized");

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
