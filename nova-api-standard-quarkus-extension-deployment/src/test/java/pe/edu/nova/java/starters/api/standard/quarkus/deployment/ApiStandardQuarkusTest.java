package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Una aplicación Quarkus con la extensión como única dependencia, sin {@code quarkus.index-dependency}: el
 * módulo de deployment registra el mapper y el customizer, y el servicio responde como antes de separarlo.
 */
class ApiStandardQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FailingResource.class, FailingResource.Empty.class));

    @Test
    void anIllegalArgumentIsABadRequestWithItsOwnMessage() {
        given().get("/failing/invalid-argument")
                .then()
                .statusCode(400)
                .contentType(MediaType.APPLICATION_JSON)
                .body("success", is(false))
                .body("status", is(400))
                .body("data", nullValue())
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("BAD_REQUEST"))
                .body("errors[0].message", equalTo("field 'email' is invalid"));
    }

    @Test
    void aSecurityExceptionIsForbidden() {
        given().get("/failing/security")
                .then()
                .statusCode(403)
                .body("success", is(false))
                .body("status", is(403))
                .body("errors[0].code", equalTo("FORBIDDEN"))
                .body("errors[0].message", equalTo("access denied"));
    }

    @Test
    void anUnexpectedExceptionIsAnInternalErrorThatHidesItsDetails() {
        given().get("/failing/unexpected")
                .then()
                .statusCode(500)
                .body("success", is(false))
                .body("status", is(500))
                .body("errors[0].code", equalTo("INTERNAL_ERROR"))
                .body("errors[0].message", equalTo("Internal server error"))
                .body(not(containsString("secret stack trace")));
    }

    @Test
    void theCustomizerIsRegisteredSoAnEmptyBeanIsSerializedAsAnEmptyObject() {
        given().get("/failing/empty-bean").then().statusCode(200).body(equalTo("{}"));
    }

    /** Un recurso por cada excepción que la extensión responde, y uno que usa el Jackson que ella configura. */
    @Path("/failing")
    public static class FailingResource {

        /** Un bean sin propiedades. */
        public static class Empty {}

        @GET
        @Path("/invalid-argument")
        public String invalidArgument() {
            throw new IllegalArgumentException("field 'email' is invalid");
        }

        @GET
        @Path("/security")
        public String security() {
            throw new SecurityException("access denied");
        }

        @GET
        @Path("/unexpected")
        public String unexpected() {
            throw new RuntimeException("secret stack trace info");
        }

        @GET
        @Path("/empty-bean")
        @Produces(MediaType.APPLICATION_JSON)
        public Empty emptyBean() {
            return new Empty();
        }
    }
}
