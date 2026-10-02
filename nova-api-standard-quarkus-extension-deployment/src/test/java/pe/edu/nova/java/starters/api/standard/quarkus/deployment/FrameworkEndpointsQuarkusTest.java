package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Los endpoints del framework contestan con su propio formato: SmallRye Health, las métricas y el documento de
 * OpenAPI no son recursos JAX-RS sino rutas de Vert.x, así que no pasan por la cadena de filtros de Quarkus REST ni
 * por el sobre de éxito. Es el equivalente en Quarkus de dejar fuera a Actuator: un health caído sigue diciendo
 * {@code "status":"DOWN"}, como en {@code UnwrappedResponseTest} del starter de Spring Boot.
 * <p>
 * Un filtro de respuesta de JAX-RS que anota las rutas que ve prueba que no es que el sobre se contenga: ni siquiera
 * las ve. Un recurso del servicio sí pasa por el filtro y se envuelve como cualquier otro.
 */
class FrameworkEndpointsQuarkusTest {

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
                    FailingReadinessCheck.class,
                    SeenPathsFilter.class));

    @Test
    void aDownReadinessCheckAnswersWithItsOwnBodyAndStatus() {
        given().get("/q/health/ready")
                .then()
                .statusCode(503)
                .contentType(ContentType.JSON)
                .body("status", equalTo("DOWN"))
                .body("checks[0].name", equalTo("inventory"))
                .body("checks[0].status", equalTo("DOWN"))
                .body("success", nullValue())
                .body("data", nullValue());
    }

    @Test
    void theOverallHealthIsDownAndKeepsItsOwnFormat() {
        given().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("success", nullValue())
                .body("data", nullValue());
    }

    @Test
    void theLivenessProbeIsUpAndKeepsItsOwnFormat() {
        given().get("/q/health/live")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("UP"))
                .body("success", nullValue())
                .body("data", nullValue());
    }

    @Test
    void theOpenApiDocumentKeepsItsOwnFormat() {
        given().queryParam("format", "json")
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("openapi", startsWith("3."))
                .body("paths", notNullValue())
                .body("success", nullValue());
    }

    @Test
    void theMetricsKeepThePrometheusFormat() {
        // El registro de Prometheus negocia el formato con el cliente: texto de Prometheus u OpenMetrics
        String body = given().get("/q/metrics")
                .then()
                .statusCode(200)
                .contentType(anyOf(containsString("text/plain"), containsString("openmetrics-text")))
                .extract()
                .asString();

        assertTrue(body.contains("# TYPE"), "las métricas salen en un formato de texto, no en un sobre");
        assertFalse(body.startsWith("{"), "las métricas no salen como JSON, y menos en un sobre");
    }

    @Test
    void theFrameworkEndpointsNeverReachTheJaxRsFilterChain() {
        given().get("/q/health/ready");
        given().get("/q/health");
        given().get("/q/health/live");
        given().queryParam("format", "json").get("/q/openapi");
        given().get("/q/metrics");
        given().get("/catalog");

        String seen = given().get("/seen").then().statusCode(200).extract().asString();
        List<String> paths = List.of(seen.split("\n"));

        assertTrue(paths.contains("GET /catalog"), "un recurso del servicio sí pasa por la cadena: " + paths);
        for (String path : paths) {
            assertFalse(path.contains("q/"), "ninguna ruta del framework pasa por la cadena de filtros: " + paths);
        }
    }

    @Test
    void aResourceOfTheServiceStillGetsTheEnvelope() {
        given().get("/catalog")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data[0].name", equalTo("Mesa"));
    }
}
