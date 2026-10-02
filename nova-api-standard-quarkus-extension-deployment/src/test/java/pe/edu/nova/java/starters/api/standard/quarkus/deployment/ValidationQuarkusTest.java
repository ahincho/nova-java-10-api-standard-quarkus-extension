package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Bean Validation sobre la entrada es un 400 con un error por violación (ADR-050); sobre el valor de retorno es
 * un defecto del servicio y sale como 500. El campo es el último nodo de la ruta de la propiedad, y una
 * restricción de toda la clase lleva el campo vacío.
 */
class ValidationQuarkusTest {

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(
                    ValidationResource.class,
                    ValidationResource.NewOrder.class,
                    ValidationResource.Range.class,
                    ValidationResource.ValidRange.class,
                    ValidationResource.RangeValidator.class,
                    RecordingLogHandler.class,
                    Line.class));

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
    void anInvalidBodyIsABadRequestWithOneErrorPerFieldNamedByItsLastNode() {
        envelope(given().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"\",\"quantity\":0,\"tags\":[\"uno\"]}")
                        .post("/validated"), 400)
                .body("errors", hasSize(2))
                .body("errors.code", everyItem(is("BAD_REQUEST")))
                .body("errors.field", containsInAnyOrder("name", "quantity"))
                .body("errors.message", containsInAnyOrder("El nombre es obligatorio", "La cantidad debe ser positiva"));
    }

    @Test
    void anInvalidElementOfAListIsNamedByTheListAndNotByThePlaceholderOfTheElement() {
        envelope(given().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"Silla\",\"quantity\":1,\"tags\":[\"uno\",\"\"]}")
                        .post("/validated"), 400)
                .body("errors", hasSize(1))
                .body("errors[0].field", equalTo("tags"))
                .body("errors[0].message", equalTo("La etiqueta no puede estar vacía"));
    }

    @Test
    void aConstraintOnAParameterIsNamedByTheParameter() {
        envelope(given().queryParam("limit", 50).get("/validated/top"), 400)
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("BAD_REQUEST"))
                .body("errors[0].field", equalTo("limit"))
                .body("errors[0].message", equalTo("No se pueden pedir más de 10"));
    }

    @Test
    void aConstraintOnTheWholeClassHasAnEmptyField() {
        envelope(given().contentType(MediaType.APPLICATION_JSON).body("{\"min\":5,\"max\":1}").post("/validated/range"), 400)
                .body("errors", hasSize(1))
                .body("errors[0].field", equalTo(""))
                .body("errors[0].message", equalTo("El máximo tiene que ser mayor que el mínimo"));
    }

    @Test
    void aValidRequestIsNotTouched() {
        given().contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Silla\",\"quantity\":2,\"tags\":[\"uno\"]}")
                .post("/validated")
                .then()
                .statusCode(200)
                .body(equalTo("creado"));
    }

    @Test
    void aViolationOfTheReturnValueIsAPlatformErrorThatHidesTheViolation() {
        envelope(given().get("/validated/forgotten"), 500)
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("errors[0].message", equalTo("Error interno del servidor"))
                .body(not(containsString("devolvió nada")));
    }

    @Test
    void anInputViolationIsAnExpectedWarningAndAReturnViolationAnIncident() {
        given().queryParam("limit", 50).get("/validated/top");
        given().get("/validated/forgotten");

        var lines = LOG.lines();
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).isWarning());
        assertEquals("application", lines.get(0).mdc().get("layer"));
        assertTrue(lines.get(1).isError());
        assertNotNull(lines.get(1).thrown(), "la violación del retorno va al log con su causa");
        assertEquals("platform", lines.get(1).mdc().get("layer"));
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
