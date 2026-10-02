package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Una organización, como UTP, reemplaza los puertos con sus propios beans y la extensión los usa, sin forkear: el
 * de Nova es {@code @DefaultBean}. Ni así un puerto ve al proveedor que falló, porque el núcleo lo quita antes.
 */
class ErrorPortsReplacementQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(OrderResource.class, OrganizationPorts.class));

    @Test
    void theCatalogOfTheOrganizationDecidesCodesAndTexts() {
        given().get("/orders/export")
                .then()
                .statusCode(429)
                .body("title", equalTo("ORG-429"))
                .body("detail", equalTo("Texto de la organización"));
    }

    @Test
    void theStatusMapperOfTheOrganizationDecidesTheStatusOfAnError() {
        given().get("/orders/42").then().statusCode(410).body("title", equalTo("ORG-410")).body("status", equalTo(410));
    }

    @Test
    void theSerializerOfTheOrganizationReplacesTheBodyAndTheContentType() {
        given().get("/orders/42/shipping")
                .then()
                .statusCode(504)
                .contentType("application/problem+json")
                .header("X-Organization", "utp")
                .body("status", equalTo(504))
                .body("success", nullValue());
    }

    @Test
    void noPortSeesTheUpstreamNorTheCause() {
        given().get("/orders/42/shipping")
                .then()
                .statusCode(504)
                .body(not(containsString(OrderResource.UPSTREAM)))
                .body(not(containsString("8443")))
                .body(not(containsString("SocketTimeoutException")));
    }

    @Test
    void aFrameworkExceptionAlsoGoesThroughThePortsOfTheOrganization() {
        given().get("/nada").then().statusCode(404).body("title", equalTo("ORG-404"));
    }

    @Test
    void anUnexpectedExceptionAlsoGoesThroughThePortsOfTheOrganization() {
        given().get("/orders/42/audit")
                .then()
                .statusCode(500)
                .contentType("application/problem+json")
                .body("title", equalTo("ORG-500"))
                .body(not(containsString("Acme")));
    }
}
