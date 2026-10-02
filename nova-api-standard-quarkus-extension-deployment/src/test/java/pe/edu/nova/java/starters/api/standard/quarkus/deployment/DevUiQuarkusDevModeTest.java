package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.List;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * La Dev UI y los endpoints de desarrollo, que solo existen en modo dev, contestan con su propio formato y no pasan
 * por el sobre de éxito: son rutas de Vert.x y no recursos JAX-RS. Es el equivalente en Quarkus de dejar fuera a
 * Actuator. La aplicación arranca en modo dev, que es lo único que activa la Dev UI.
 * <p>
 * Escucha en el puerto {@value #PORT}, que no es el 8080 de un servicio en desarrollo ni el 8081 de las demás pruebas.
 */
class DevUiQuarkusDevModeTest {

    private static final int PORT = 18080;

    @RegisterExtension
    static final QuarkusDevModeTest APPLICATION = new QuarkusDevModeTest()
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
                            SeenPathsFilter.class)
                    .addAsResource(new StringAsset("quarkus.http.port=" + PORT + "\n"), "application.properties"));

    private static int previousPort;

    @BeforeAll
    static void talkToTheDevServer() {
        previousPort = RestAssured.port;
        RestAssured.port = PORT;
    }

    @AfterAll
    static void restoreThePort() {
        RestAssured.port = previousPort;
    }

    @Test
    void theDevUiAnswersWithItsOwnHtml() {
        given().get("/q/dev-ui/")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(startsWith("<!DOCTYPE html>"))
                .body(not(containsString("\"success\"")));
    }

    @Test
    void theDevUiRedirectionIsNotTouched() {
        given().redirects()
                .follow(false)
                .get("/q/dev-ui")
                .then()
                .statusCode(302)
                .body(equalTo(""));
    }

    @Test
    void theArcEndpointOfDevelopmentKeepsItsOwnJson() {
        given().get("/q/arc")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("beans", greaterThan(0))
                .body("success", nullValue());
    }

    @Test
    void theDevEndpointsNeverReachTheJaxRsFilterChain() {
        given().get("/q/dev-ui/");
        given().redirects().follow(false).get("/q/dev-ui");
        given().get("/q/arc");
        given().get("/catalog");

        String seen = given().get("/seen").then().statusCode(200).extract().asString();
        List<String> paths = List.of(seen.split("\n"));

        assertTrue(paths.contains("GET /catalog"), "un recurso del servicio sí pasa por la cadena: " + paths);
        for (String path : paths) {
            assertFalse(path.contains("q/"), "ninguna ruta de desarrollo pasa por la cadena de filtros: " + paths);
        }
    }

    @Test
    void aResourceOfTheServiceStillGetsTheEnvelopeInDevMode() {
        given().get("/catalog")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data[0].name", equalTo("Mesa"));
    }
}
