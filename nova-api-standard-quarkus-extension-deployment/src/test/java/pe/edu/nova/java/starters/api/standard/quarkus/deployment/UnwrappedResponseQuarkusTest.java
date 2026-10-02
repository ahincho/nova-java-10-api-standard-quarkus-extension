package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * Lo que no es un cuerpo JSON o ya es un sobre sale tal cual, y lo que contesta el manejo de excepciones nunca se
 * envuelve. Es el equivalente en Quarkus de {@code UnwrappedResponseTest} del starter de Spring Boot: un
 * {@code byte[]}, un flujo o un {@code String} los escribe su propio escritor, y un sobre armado a mano no se
 * envuelve de nuevo, como hacen los recursos de los ejemplos 04 y 06.
 */
class UnwrappedResponseQuarkusTest {

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
    void aByteArrayIsWrittenAsIs() {
        Response response = given().get("/catalog/1/report");

        response.then().statusCode(200).contentType(ContentType.BINARY);
        assertArrayEquals(CatalogResource.REPORT, response.asByteArray());
    }

    @Test
    void aByteArrayWithoutAContentTypeIsWrittenAsIs() {
        Response response = given().get("/catalog/1/raw");

        response.then().statusCode(200).contentType(ContentType.BINARY);
        assertArrayEquals(CatalogResource.REPORT, response.asByteArray());
    }

    @Test
    void anInputStreamIsWrittenAsIs() {
        Response response = given().get("/catalog/1/manual");

        response.then().statusCode(200).contentType("application/pdf");
        assertArrayEquals(CatalogResource.MANUAL, response.asByteArray());
    }

    @Test
    void aStreamingOutputIsWrittenAsIs() {
        Response response = given().get("/catalog/1/manual-stream");

        response.then().statusCode(200).contentType("application/pdf");
        assertArrayEquals(CatalogResource.MANUAL, response.asByteArray());
    }

    @Test
    void aStringIsWrittenAsIs() {
        given().get("/catalog/1/name").then().statusCode(200).contentType(ContentType.TEXT).body(equalTo("Mesa"));
    }

    @Test
    void aStringWithoutAContentTypeIsWrittenAsIs() {
        given().get("/catalog/1/label").then().statusCode(200).contentType(ContentType.TEXT).body(equalTo("Mesa"));
    }

    @Test
    void aJsonThatTheResourceAlreadyWroteIsWrittenAsIs() {
        given().get("/catalog/1/json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"raw\":true}"));
    }

    @Test
    void aCsvIsWrittenAsIs() {
        given().get("/catalog/csv").then().statusCode(200).contentType("text/csv").body(startsWith("id,name"));
    }

    @Test
    void anObjectThatTheResourceDeclaresAsTextIsWrittenAsText() {
        given().get("/catalog/1/text")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .body(startsWith("Product["));
    }

    @Test
    void aNumberWithoutJsonStaysAsText() {
        given().get("/catalog/count").then().statusCode(200).contentType(ContentType.TEXT).body(equalTo("42"));
    }

    @Test
    void aStreamOfObjectsAsAJsonArrayIsWrittenAsIs() {
        given().get("/catalog/stream/json")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(2))
                .body("[0].name", equalTo("Mesa"))
                .body("[1].name", equalTo("Silla"));
    }

    @Test
    void aStreamOfObjectsAsNdjsonIsWrittenAsIs() {
        String body = given().get("/catalog/stream/ndjson")
                .then()
                .statusCode(200)
                .contentType("application/x-ndjson")
                .extract()
                .asString();

        assertEquals(
                "{\"id\":1,\"name\":\"Mesa\",\"quantity\":4}\n{\"id\":2,\"name\":\"Silla\",\"quantity\":2}\n", body);
    }

    @Test
    void aStreamOfEventsIsWrittenAsIs() {
        String body = given().get("/catalog/stream/events")
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract()
                .asString();

        assertEquals(
                "data:{\"id\":1,\"name\":\"Mesa\",\"quantity\":4}\n\ndata:{\"id\":2,\"name\":\"Silla\",\"quantity\":2}\n\n",
                body);
    }

    @Test
    void anEnvelopeBuiltByHandIsWrittenAsItIsWithoutWrappingItAgain() throws Exception {
        String expected = mapper.writeValueAsString(ApiResponse.ok(CatalogResource.TABLE));

        given().get("/catalog/envelope/ok")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(expected))
                .body("data.id", is(1))
                .body("data.data", nullValue());
    }

    @Test
    void anEnvelopeBuiltByHandKeepsItsOwnStatus() throws Exception {
        String expected = mapper.writeValueAsString(ApiResponse.created(CatalogResource.TABLE));

        // El sobre dice 201 y la respuesta HTTP dice 200, como lo armó el recurso: el filtro no corrige lo que no armó
        given().get("/catalog/envelope/created")
                .then()
                .statusCode(200)
                .body(equalTo(expected))
                .body("status", is(201));
    }

    @Test
    void anEnvelopeBuiltByHandKeepsItsOwnMetadata() throws Exception {
        String expected = mapper.writeValueAsString(ApiResponse.<CatalogResource.Product>builder()
                .data(CatalogResource.TABLE)
                .status(200)
                .metadata(ApiMetadata.builder()
                        .timestamp(CatalogResource.MOMENT)
                        .traceId("trace-1")
                        .apiVersion("v1")
                        .build())
                .build());

        given().get("/catalog/envelope/described")
                .then()
                .statusCode(200)
                .body(equalTo(expected))
                .body("metadata.timestamp", equalTo("2026-07-14T12:34:56Z"))
                .body("metadata.traceId", equalTo("trace-1"))
                .body("metadata.apiVersion", equalTo("v1"));
    }

    @Test
    void whatTheErrorMappersAnswerIsNeverWrappedAgain() {
        given().get("/catalog/1/audit")
                .then()
                .statusCode(500)
                .body("success", is(false))
                .body("status", is(500))
                .body("data", nullValue())
                .body("errors", hasSize(1))
                .body("errors[0].code", equalTo("INTERNAL_SERVER_ERROR"));
        given().get("/catalog/1/price")
                .then()
                .statusCode(503)
                .body("success", is(false))
                .body("data", nullValue())
                .body("errors[0].code", equalTo("SERVICE_UNAVAILABLE"));
        given().get("/nada")
                .then()
                .statusCode(404)
                .body("success", is(false))
                .body("data", nullValue())
                .body("errors[0].code", equalTo("NOT_FOUND"));
    }

    @Test
    void aWebApplicationExceptionWithTheBodyOfTheProviderIsWrittenAsIs() {
        given().get("/catalog/1/provider")
                .then()
                .statusCode(502)
                .body("upstream", equalTo("acme"))
                .body("success", nullValue());
    }

    @Test
    void anExceptionMapperOfTheServiceKeepsItsOwnBody() {
        // En Spring el @RestControllerAdvice del servicio sí se envuelve; aquí un mapper del servicio contesta como
        // lo armó, porque no hay cómo distinguirlo del de la extensión sin marcar cada respuesta
        given().post("/catalog/1/locks")
                .then()
                .statusCode(423)
                .body("lockedBy", equalTo("inventario"))
                .body("success", nullValue());
    }

    @Test
    void aNoContentResponseHasNoBodyAndNoContentType() {
        for (var request : new Response[] {
            given().delete("/catalog/1"), given().delete("/catalog/1/void"), given().get("/catalog/1/missing")
        }) {
            request.then().statusCode(204).body(emptyOrNullString());
            assertNull(request.header("Content-Type"));
        }
    }

    @Test
    void aResetContentHasNoBody() {
        given().post("/catalog/1/resets").then().statusCode(205).body(emptyOrNullString());
    }

    @Test
    void aPartialContentKeepsItsOwnBody() {
        given().get("/catalog/1/range")
                .then()
                .statusCode(206)
                .body("id", is(1))
                .body("success", nullValue());
    }

    @Test
    void aRedirectionHasNoEnvelope() {
        given().redirects()
                .follow(false)
                .get("/catalog/1/redirection")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/catalog/1"))
                .body(emptyOrNullString());
    }

    @Test
    void aNotModifiedHasNoBody() {
        given().get("/catalog/1/validation").then().statusCode(304).body(emptyOrNullString());
    }

    @Test
    void aHeadRequestHasNoBody() {
        given().head("/catalog").then().statusCode(200).contentType(ContentType.JSON).body(emptyOrNullString());
    }

    @Test
    void anOptionsRequestIsAnsweredByTheFrameworkWithoutAnEnvelope() {
        Response options = given().options("/catalog");

        options.then().statusCode(200).body(emptyOrNullString());
        assertTrue(options.header("Allow").contains("GET"), options.headers().toString());
    }

    @Test
    void aClientThatDoesNotAcceptJsonGetsTheEmptyResponseAsItIs() {
        // Es lo que hace Spring: sin un JSON que escribir, un 200 sin cuerpo sigue sin cuerpo y no pasa a un 406
        for (String accept : new String[] {"text/plain", "application/xml", "application/vnd.api+json"}) {
            given().header("Accept", accept)
                    .post("/catalog/1/confirmations")
                    .then()
                    .statusCode(200)
                    .body(emptyOrNullString());
        }
    }

    @Test
    void anAcceptHeaderThatCannotBeReadNeverTurnsAResponseIntoAnError() {
        // Quarkus REST contesta 406, 200 o 404 con estos headers, y el filtro no los convierte en un 500
        for (String accept : new String[] {"invalid", "text", ";", "/", "a/b/c", "application/json, ,", ""}) {
            given().header("Accept", accept)
                    .post("/catalog/1/confirmations")
                    .then()
                    .statusCode(200)
                    .body(emptyOrNullString());
            given().header("Accept", accept).get("/catalog/42").then().statusCode(404).body(emptyOrNullString());
            int listStatus = given().header("Accept", accept).get("/catalog").statusCode();
            assertTrue(listStatus == 200 || listStatus == 406, "Accept [" + accept + "] contestó " + listStatus);
        }
    }

    @Test
    void aClientThatAcceptsJsonGetsTheEnvelope() {
        for (String accept : new String[] {"application/json", "application/*", "*/*", "text/html, */*;q=0.8",
            "application/xml, application/json;q=0.5"}) {
            given().header("Accept", accept)
                    .post("/catalog/1/confirmations")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("success", is(true))
                    .body("data", nullValue());
        }
    }

    @Test
    void aClientThatAsksForTheVendorTypeOfTheResourceGetsTheEnvelopeWithThatType() {
        Response response = given().header("Accept", "application/vnd.nova.product+json").get("/catalog/1/vendor");

        assertEquals("application/vnd.nova.product+json", response.contentType().split(";")[0]);
        response.then().statusCode(200).body("success", is(true)).body("data.name", equalTo("Mesa"));
    }
}
