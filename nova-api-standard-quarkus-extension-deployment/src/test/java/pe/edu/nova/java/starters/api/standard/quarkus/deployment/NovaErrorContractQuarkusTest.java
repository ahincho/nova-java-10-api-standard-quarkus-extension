package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * La suite de contrato de ADR-031 por HTTP: cada caso responde el status y el código del ADR, con
 * {@code success: false}, el {@code status} en el cuerpo y {@code metadata.traceId}. Son los mismos casos que
 * corren el starter de Spring Boot y NestJS, así que los tres stacks responden lo mismo.
 */
class NovaErrorContractQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(OrderResource.class));

    @Test
    void domainNotFoundWithItsOwnCode() {
        contract(given().get("/orders/42"), 404)
                .body("errors[0].code", equalTo("ORDER_NOT_FOUND"))
                .body("errors[0].message", equalTo("El pedido 42 no existe"));
    }

    @Test
    void domainConflictWithoutCode() {
        contract(given().post("/orders/42/confirmations"), 409).body("errors[0].code", equalTo("CONFLICT"));
    }

    @Test
    void applicationInvalidInputWithTwoFields() {
        contract(given().post("/orders"), 400)
                .body("errors", hasSize(2))
                .body("errors.code", everyItem(is("BAD_REQUEST")))
                .body("errors.field", containsInAnyOrder("email", "quantity"));
    }

    @Test
    void applicationConflictWithRetryAfterOfOneSecond() {
        contract(given().post("/orders/42/payments"), 409)
                .body("errors[0].code", equalTo("CONFLICT"))
                .header("Retry-After", "1");
    }

    @Test
    void applicationRateLimitedWithThirtySeconds() {
        contract(given().get("/orders/export"), 429)
                .body("errors[0].code", equalTo("TOO_MANY_REQUESTS"))
                .header("Retry-After", "30");
    }

    @Test
    void infrastructureTimeoutNeverNamesTheUpstreamInTheBody() {
        contract(given().get("/orders/42/shipping"), 504)
                .body("errors[0].code", equalTo("GATEWAY_TIMEOUT"))
                .body("errors[0].message", equalTo("Una dependencia no respondió a tiempo"))
                .body(not(containsString(OrderResource.UPSTREAM)))
                .body(not(containsString("8443")));
    }

    @Test
    void infrastructureUnavailable() {
        contract(given().get("/orders/42/stock"), 503)
                .body("errors[0].code", equalTo("SERVICE_UNAVAILABLE"))
                .body("errors[0].message", equalTo("El servicio no está disponible en este momento"));
    }

    @Test
    void anyOtherExceptionIsAPlatformError() {
        contract(given().get("/orders/42/audit"), 500)
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("errors[0].message", equalTo("Error interno del servidor"))
                .body(not(containsString("Acme")));
    }

    private static ValidatableResponse contract(Response response, int status) {
        return response.then()
                .statusCode(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("success", is(false))
                .body("status", is(status))
                .body("metadata.traceId", notNullValue())
                .body("errors.size()", greaterThan(0));
    }
}
