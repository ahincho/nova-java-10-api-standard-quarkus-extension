package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.starters.api.standard.quarkus.deployment.RecordingLogHandler.Line;

/**
 * Las excepciones propias de Quarkus REST salen con su status y su código del catálogo, no como 500: se leen
 * por su status (ADR-031), así que un 4xx es {@code application}, un 502, 503 o 504 {@code infrastructure} y
 * cualquier otro 5xx {@code platform}.
 */
class FrameworkExceptionsQuarkusTest {

    private static final String UNREADABLE_BODY = "No se pudo leer el cuerpo de la solicitud";

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(
                    ItemResource.class, ItemResource.NewItem.class, RecordingLogHandler.class, Line.class));

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
    void aRouteThatDoesNotExistIsNotFound() {
        envelope(given().get("/nada"), 404)
                .body("errors[0].code", equalTo("NOT_FOUND"))
                .body("errors[0].message", equalTo("El recurso no existe"));
    }

    @Test
    void aPathParameterThatIsNotANumberIsNotFound() {
        envelope(given().get("/items/abc/price"), 404).body("errors[0].code", equalTo("NOT_FOUND"));
    }

    @Test
    void anUnsupportedMethodIsMethodNotAllowed() {
        // Quarkus REST arma este 405 sin el header Allow, así que no hay nada que conservar
        envelope(given().put("/items"), 405)
                .body("errors[0].code", equalTo("METHOD_NOT_ALLOWED"))
                .body("errors[0].message", equalTo("El método no está permitido en este recurso"));
    }

    @Test
    void aMethodNotAllowedKeepsTheAllowHeaderThatItCarries() {
        envelope(given().get("/items/1/method"), 405)
                .header("Allow", containsString("GET"))
                .header("Allow", containsString("POST"))
                .body("errors[0].code", equalTo("METHOD_NOT_ALLOWED"));
    }

    @Test
    void anUnsupportedContentTypeIsUnsupportedMediaType() {
        envelope(given().contentType("text/plain").body("Silla").post("/items"), 415)
                .body("errors[0].code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void aRepresentationNobodyCanProduceIsNotAcceptable() {
        envelope(given().accept("application/xml").get("/items"), 406)
                .body("errors[0].code", equalTo("NOT_ACCEPTABLE"));
    }

    @Test
    void aMalformedBodyIsABadRequestWithTheFixedMessage() {
        envelope(given().contentType(MediaType.APPLICATION_JSON).body("{\"name\":").post("/items"), 400)
                .body("errors[0].code", equalTo("BAD_REQUEST"))
                .body("errors[0].message", equalTo(UNREADABLE_BODY))
                .body(not(containsString("end-of-input")));
    }

    @Test
    void aBodyOfTheWrongShapeIsABadRequestWithTheFixedMessage() {
        envelope(given().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"Silla\",\"quantity\":\"muchas\"}")
                        .post("/items"), 400)
                .body("errors[0].code", equalTo("BAD_REQUEST"))
                .body("errors[0].message", equalTo(UNREADABLE_BODY))
                .body(not(containsString("NewItem")));
    }

    @Test
    void aServerErrorOfJaxRsKeepsItsStatusAndHidesItsReason() {
        envelope(given().get("/items/1/price"), 503)
                .body("errors[0].code", equalTo("SERVICE_UNAVAILABLE"))
                .body("errors[0].message", equalTo("El servicio no está disponible en este momento"))
                .body(not(containsString("Acme")));
    }

    @Test
    void aBadGatewayAndAGatewayTimeoutKeepTheirOwnCodes() {
        envelope(given().get("/items/1/gateway"), 502).body("errors[0].code", equalTo("BAD_GATEWAY"));
        envelope(given().get("/items/1/timeout"), 504).body("errors[0].code", equalTo("GATEWAY_TIMEOUT"));
    }

    @Test
    void anyOtherServerErrorIsAnInternalServerError() {
        envelope(given().get("/items/1/not-implemented"), 501)
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("errors[0].message", equalTo("Error interno del servidor"))
                .body(not(containsString("Acme")));
    }

    @Test
    void aClientErrorAnswersWithTheCatalogMessageAndNotTheOneOfTheException() {
        envelope(given().get("/items/1/reservation"), 409)
                .body("errors[0].code", equalTo("CONFLICT"))
                .body("errors[0].message", equalTo("La operación choca con el estado actual del recurso"));
        envelope(given().get("/items/1/bad-request"), 400)
                .body("errors[0].code", equalTo("BAD_REQUEST"))
                .body("errors[0].message", equalTo("La solicitud no es válida"))
                .body(not(containsString("xyz")));
    }

    @Test
    void anExceptionThatCarriesItsOwnEntityKeepsItAsJaxRsRequires() {
        // La especificación manda usar tal cual la respuesta de una WebApplicationException con cuerpo, sin
        // consultar a los mappers: quien arma un cuerpo a mano responde por él
        given().get("/items/1/entity")
                .then()
                .statusCode(409)
                .body(containsString("pedido abierto"))
                .body(not(containsString("success")));
    }

    @Test
    void theHeadersOfTheExceptionSurvive() {
        envelope(given().get("/items/1/retry"), 503).header("Retry-After", "120");
    }

    @Test
    void aRedirectIsNotAnErrorAndFollowsItsWay() {
        given().redirects()
                .follow(false)
                .get("/items/1/moved")
                .then()
                .statusCode(303)
                .header("Location", containsString("/items/9/price"));
    }

    @Test
    void anIllegalArgumentIsAPlatformErrorAndHidesItsMessage() {
        envelope(given().get("/items/1/check"), 500)
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("errors[0].message", equalTo("Error interno del servidor"))
                .body(not(containsString("no es válido")));
    }

    @Test
    void aSecurityExceptionOfTheJdkIsAPlatformErrorBecauseItDoesNotSayWhatIsMissing() {
        envelope(given().get("/items/1/secret"), 500)
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"))
                .body(not(containsString("Acme")));
    }

    @Test
    void theLayerOfAFrameworkErrorComesFromItsStatus() {
        given().get("/nada");
        given().get("/items/1/price");
        given().get("/items/1/gateway");
        given().get("/items/1/not-implemented");

        var lines = LOG.lines();
        assertEquals(4, lines.size(), lines.toString());
        assertLine(lines.get(0), true, "application", "404");
        assertLine(lines.get(1), false, "infrastructure", "503");
        assertLine(lines.get(2), false, "infrastructure", "502");
        assertLine(lines.get(3), false, "platform", "501");
    }

    @Test
    void aClientErrorIsAWarningWithoutStackTrace() {
        given().put("/items");

        Line line = LOG.lines().get(0);
        assertTrue(line.isWarning());
        assertNull(line.thrown());
        assertEquals("METHOD_NOT_ALLOWED", line.mdc().get("code"));
    }

    private static void assertLine(Line line, boolean expected, String layer, String status) {
        assertEquals(layer, line.mdc().get("layer"));
        assertEquals(status, line.mdc().get("status"));
        assertEquals(expected, line.isWarning(), "lo esperado va en WARN y un incidente en ERROR: " + line);
        assertEquals(!expected, line.isError());
        assertEquals(expected, line.thrown() == null, "solo un incidente lleva la causa");
    }

    private static ValidatableResponse envelope(io.restassured.response.Response response, int status) {
        return response.then()
                .statusCode(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("success", is(false))
                .body("status", is(status))
                .body("data", nullValue())
                .body("errors", hasSize(1))
                .body("metadata.traceId", org.hamcrest.Matchers.notNullValue());
    }
}
