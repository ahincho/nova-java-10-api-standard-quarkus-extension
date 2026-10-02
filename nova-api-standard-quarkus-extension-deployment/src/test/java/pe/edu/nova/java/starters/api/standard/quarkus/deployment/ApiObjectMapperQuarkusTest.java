package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;

/**
 * El {@code ApiObjectMapperCustomizer} llega al {@code ObjectMapper} de Quarkus desde el módulo de deployment, sin
 * que el servicio declare {@code quarkus.index-dependency}: se ve en cómo salen las fechas y los beans vacíos.
 */
class ApiObjectMapperQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(EnvelopeResource.class, EnvelopeResource.Empty.class));

    @Test
    void anEmptyBeanIsSerializedAsAnEmptyObjectInsteadOfFailing() {
        // El recurso devuelve el bean y el sobre de éxito lo lleva en data: sin FAIL_ON_EMPTY_BEANS sale {}
        given().get("/envelope/empty-bean")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", equalTo(Map.of()));
    }

    @Test
    void theTimestampAndTheTraceIdOfTheMetadataReachTheClientAsIso8601() {
        given().get("/envelope/dated")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data", equalTo("hecho"))
                .body("metadata.timestamp", equalTo("2026-07-14T12:34:56Z"))
                .body("metadata.traceId", equalTo("trace-1"))
                .body("errors", empty())
                .body("pageInfo", nullValue());
    }

    /** Dos respuestas que usan el Jackson que configura la extensión. */
    @Path("/envelope")
    public static class EnvelopeResource {

        /** Un bean sin propiedades. */
        public static class Empty {}

        @GET
        @Path("/empty-bean")
        @Produces(MediaType.APPLICATION_JSON)
        public Empty emptyBean() {
            return new Empty();
        }

        @GET
        @Path("/dated")
        @Produces(MediaType.APPLICATION_JSON)
        public ApiResponse<String> dated() {
            return ApiResponse.<String>builder()
                    .status(200)
                    .data("hecho")
                    .metadata(ApiMetadata.builder()
                            .timestamp(Instant.parse("2026-07-14T12:34:56Z"))
                            .traceId("trace-1")
                            .build())
                    .build();
        }
    }
}
