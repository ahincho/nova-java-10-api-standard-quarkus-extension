package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Un recurso devuelve el objeto y el cuerpo sale en el sobre de Nova con el status real de la respuesta: un 201
 * dice 201 y no 200. Es el equivalente en Quarkus de {@code EnvelopeStatusTest} del starter de Spring Boot, y los
 * cuerpos de éxito se comparan, byte a byte, con los que ese starter escribe sobre un Tomcat real.
 */
class SuccessEnvelopeQuarkusTest {

    /** Lo que escribe el starter de Spring Boot para {@code GET /items}: el mismo producto, el mismo sobre. */
    private static final String SPRING_LIST = "{\"success\":true,\"status\":200,\"data\":"
            + "[{\"id\":1,\"name\":\"Mesa\",\"quantity\":4}],\"errors\":[],\"metadata\":null,\"links\":[],"
            + "\"rateLimitInfo\":null,\"pageInfo\":null}";

    /** Lo que escribe el starter de Spring Boot para {@code POST /items}, que contesta 201. */
    private static final String SPRING_CREATED = "{\"success\":true,\"status\":201,\"data\":"
            + "{\"id\":2,\"name\":\"Silla\",\"quantity\":2},\"errors\":[],\"metadata\":null,\"links\":[],"
            + "\"rateLimitInfo\":null,\"pageInfo\":null}";

    /** Lo que escribe el starter de Spring Boot para un {@code @ResponseStatus(CREATED)}. */
    private static final String SPRING_COPY = "{\"success\":true,\"status\":201,\"data\":"
            + "{\"id\":3,\"name\":\"Mesa\",\"quantity\":4},\"errors\":[],\"metadata\":null,\"links\":[],"
            + "\"rateLimitInfo\":null,\"pageInfo\":null}";

    /** Lo que escribe el starter de Spring Boot para {@code ResponseEntity.ok().build()}. */
    private static final String SPRING_EMPTY_OK = "{\"success\":true,\"status\":200,\"data\":null,\"errors\":[],"
            + "\"metadata\":null,\"links\":[],\"rateLimitInfo\":null,\"pageInfo\":null}";

    /** Los campos del sobre, en el orden en que los escribe Spring y los escribe esta extensión. */
    private static final List<String> ENVELOPE_FIELDS = List.of(
            "success", "status", "data", "errors", "metadata", "links", "rateLimitInfo", "pageInfo");

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
                    CatalogMappers.ProductLockedException.class));

    @Inject
    ObjectMapper mapper;

    @Test
    void aCreatedResponseSaysCreatedInTheEnvelope() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Silla\",\"quantity\":2}")
                .post("/catalog")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("success", is(true))
                .body("status", is(201))
                .body("data.name", equalTo("Silla"));
    }

    @Test
    void aTypedRestResponseSaysItsStatusInTheEnvelope() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Silla\",\"quantity\":2}")
                .post("/catalog/typed")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("status", is(201))
                .body("data.name", equalTo("Silla"));
    }

    @Test
    void aResponseStatusAnnotationSaysItsStatusInTheEnvelope() {
        given().post("/catalog/1/copies")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("status", is(201))
                .body("data.id", is(3));
    }

    @Test
    void anOkResponseStillSaysOk() {
        given().get("/catalog")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("status", is(200))
                .body("data[0].name", equalTo("Mesa"));
    }

    @Test
    void anOkWithoutBodySaysOkAndNotNoContent() {
        given().post("/catalog/1/confirmations")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("status", is(200))
                .body("data", nullValue())
                .body("errors", hasSize(0));
    }

    @Test
    void anAcceptedWithoutBodySaysAccepted() {
        given().post("/catalog/1/queue")
                .then()
                .statusCode(202)
                .body("success", is(true))
                .body("status", is(202))
                .body("data", nullValue());
    }

    @Test
    void aCreatedWithoutBodyKeepsItsLocationAndSaysCreated() {
        given().post("/catalog/registrations")
                .then()
                .statusCode(201)
                .header("Location", endsWith("/catalog/1"))
                .body("success", is(true))
                .body("status", is(201))
                .body("data", nullValue());
    }

    @Test
    void theBodyIsTheSameThatSpringWritesForAList() {
        assertEquals(SPRING_LIST, given().get("/catalog").then().statusCode(200).extract().asString());
    }

    @Test
    void theBodyIsTheSameThatSpringWritesForACreatedResponse() {
        String body = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Silla\",\"quantity\":2}")
                .post("/catalog")
                .then()
                .statusCode(201)
                .extract()
                .asString();

        assertEquals(SPRING_CREATED, body);
    }

    @Test
    void theBodyIsTheSameThatSpringWritesForAResponseStatusAnnotation() {
        assertEquals(SPRING_COPY, given().post("/catalog/1/copies").then().statusCode(201).extract().asString());
    }

    @Test
    void theBodyIsTheSameThatSpringWritesForAnOkWithoutBody() {
        assertEquals(
                SPRING_EMPTY_OK,
                given().post("/catalog/1/confirmations").then().statusCode(200).extract().asString());
    }

    @Test
    void metadataIsNullOnSuccessAsInSpringAndTheRestOfTheEnvelopeHasTheSameFields() throws Exception {
        JsonNode envelope = mapper.readTree(given().get("/catalog").asString());

        assertEquals(ENVELOPE_FIELDS, fieldNamesOf(envelope));
        assertEquals(0, envelope.get("errors").size());
        assertEquals(0, envelope.get("links").size());
        assertTrue(envelope.get("metadata").isNull(), "ni Spring ni Quarkus escriben metadata en un éxito");
        assertTrue(envelope.get("rateLimitInfo").isNull());
        assertTrue(envelope.get("pageInfo").isNull());
    }

    @Test
    void theFieldsOfTheDataAreNotAlteredByTheEnvelope() throws Exception {
        JsonNode envelope = mapper.readTree(given().get("/catalog").asString());

        assertEquals(List.of("id", "name", "quantity"), fieldNamesOf(envelope.get("data").get(0)));
    }

    @Test
    void aProductFoundByIdIsWrapped() {
        given().get("/catalog/1")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.id", is(1))
                .body("data.quantity", is(4));
    }

    @Test
    void aReactiveResultIsWrapped() {
        given().get("/catalog/1/reactive").then().statusCode(200).body("success", is(true)).body("data.id", is(1));
    }

    @Test
    void aCompletionStageResultIsWrapped() {
        given().get("/catalog/1/stage").then().statusCode(200).body("success", is(true)).body("data.id", is(1));
    }

    @Test
    void aMapIsWrappedAsAnObject() {
        given().get("/catalog/1/attributes")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.color", equalTo("roble"));
    }

    @Test
    void anObjectWithoutPropertiesIsWrappedWithAnEmptyObject() {
        given().get("/catalog/1/nothing")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", equalTo(Map.of()));
    }

    @Test
    void aNumberThatDeclaresJsonIsWrapped() {
        given().get("/catalog/count-json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", is(true))
                .body("data", is(42));
    }

    @Test
    void aJacksonViewOfTheResourceStillAppliesToTheDataAndNotToTheEnvelope() {
        given().get("/catalog/1/summary")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("status", is(200))
                .body("errors", hasSize(0))
                .body("data.id", is(1))
                .body("data.name", equalTo("Mesa"))
                .body("data.cost", nullValue());
    }

    @Test
    void aContentTypeWithTheJsonSuffixIsWrappedToo() {
        var response = given().get("/catalog/1/vendor");

        assertEquals("application/vnd.nova.product+json", response.contentType().split(";")[0]);
        response.then().statusCode(200).body("success", is(true)).body("data.name", equalTo("Mesa"));
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
