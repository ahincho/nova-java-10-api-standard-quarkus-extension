package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.starters.api.standard.quarkus.deployment.RecordingLogHandler.Line;

/**
 * Un 4xx o 5xx que contesta el recurso sin lanzar una excepción, con o sin cuerpo, sale como sobre de error. Es el
 * equivalente en Quarkus de {@code ErrorEnvelopeTest} del starter de Spring Boot: el sobre se arma con los puertos
 * como el de una excepción del framework, y el cuerpo propio del recurso se conserva en {@code data}.
 */
class ResourceErrorEnvelopeQuarkusTest {

    /** Los campos de la metadata de un error, en el orden en que los escribe Spring y los escribe esta extensión. */
    private static final List<String> METADATA_FIELDS =
            List.of("timestamp", "traceId", "apiVersion", "processingTimeMs", "customFields");

    private static final RecordingLogHandler LOG = new RecordingLogHandler();

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(
                    CatalogResource.class,
                    CatalogResource.Product.class,
                    CatalogResource.NewProduct.class,
                    CatalogResource.Nothing.class,
                    CatalogResource.Public.class,
                    CatalogResource.Internal.class,
                    CatalogResource.Summary.class,
                    CatalogMappers.class,
                    CatalogMappers.ProductLockedException.class,
                    RecordingLogHandler.class,
                    Line.class));

    @Inject
    ObjectMapper mapper;

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
    void aNotFoundWithoutBodyIsAnErrorEnvelope() {
        given().get("/catalog/42")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("success", is(false))
                .body("status", is(404))
                .body("data", nullValue())
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("NOT_FOUND"))
                .body("errors[0].message", equalTo("El recurso no existe"));
    }

    @Test
    void aServerErrorWithoutBodyCarriesTheGenericMessage() {
        given().get("/catalog/1/stock")
                .then()
                .statusCode(503)
                .body("success", is(false))
                .body("status", is(503))
                .body("errors[0].code", equalTo("SERVICE_UNAVAILABLE"))
                .body("errors[0].message", equalTo("El servicio no está disponible en este momento"));
    }

    @Test
    void aClientErrorWithABodyKeepsTheBodyAsData() {
        given().put("/catalog/1")
                .then()
                .statusCode(409)
                .body("success", is(false))
                .body("status", is(409))
                .body("data.reason", equalTo("El producto está en un pedido abierto"))
                .body("errors[0].code", equalTo("CONFLICT"));
    }

    @Test
    void aRealNoContentStaysWithoutBody() {
        // Spring arma el sobre de un 204 y Tomcat descarta su cuerpo: lo que llega al cliente es lo mismo
        given().delete("/catalog/1")
                .then()
                .statusCode(204)
                .body(emptyOrNullString());
    }

    @Test
    void theErrorEnvelopeCarriesTheMetadataOfTheRequest() throws Exception {
        JsonNode envelope = mapper.readTree(given().get("/catalog/42").asString());
        JsonNode metadata = envelope.get("metadata");

        assertEquals(METADATA_FIELDS, fieldNamesOf(metadata));
        assertTrue(metadata.get("timestamp").isTextual());
        assertTrue(metadata.get("traceId").isTextual() && !metadata.get("traceId").asText().isBlank());
    }

    @Test
    void anErrorEnvelopeThatTheResourceBuiltItselfIsNotWrappedAgain() {
        given().get("/catalog/envelope/failed")
                .then()
                .statusCode(409)
                .body("success", is(false))
                .body("status", is(409))
                .body("data", nullValue())
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("ERROR"))
                .body("errors[0].message", equalTo("El producto está en un pedido abierto"))
                .body("metadata", nullValue());
    }

    @Test
    void aTextErrorKeepsItsText() {
        given().get("/catalog/1/complaint")
                .then()
                .statusCode(400)
                .contentType(ContentType.TEXT)
                .body(equalTo("texto plano"));
    }

    @Test
    void anAnswerOfTheResourceIsNotLoggedLikeAnIncident() {
        // Lo contestó el recurso a propósito: no es una excepción, así que ni el log ni el contador lo ven
        given().get("/catalog/42").then().statusCode(404);
        given().get("/catalog/1/stock").then().statusCode(503);

        assertEquals(List.of(), LOG.lines());
    }

    @Test
    void aSuccessIsNotLoggedEither() {
        given().get("/catalog").then().statusCode(200).body("metadata", is(nullValue())).body("data", notNullValue());

        assertEquals(List.of(), LOG.lines());
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
