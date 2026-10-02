package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.api.standard.error.ApiError;
import pe.edu.nova.java.libs.api.standard.link.ApiLink;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.page.PageInfo;
import pe.edu.nova.java.libs.api.standard.ratelimit.RateLimitInfo;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;
import pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer;
import pe.edu.nova.java.starters.api.standard.quarkus.mapper.ApiExceptionMapper;

/** Lo que la extensión registra al construir: sin esto, un servicio no la descubre y una imagen nativa no responde. */
class NovaApiStandardProcessorTest {

    private final NovaApiStandardProcessor processor = new NovaApiStandardProcessor();

    @Test
    void theFeatureIsListedWithTheNameOfTheExtension() {
        assertEquals("nova-api-standard", processor.feature().getName());
    }

    @Test
    void everyBeanOfTheExtensionIsRegisteredAndKept() {
        var beans = processor.beans();

        assertEquals(
                List.of(ApiExceptionMapper.class.getName(), ApiObjectMapperCustomizer.class.getName()),
                beans.getBeanClasses());
        assertFalse(beans.isRemovable(), "nadie inyecta estos beans, así que ArC no puede quitarlos por no usados");
    }

    @Test
    void theClassWithTheServerExceptionMapperIsIndexed() {
        assertEquals(Set.of(ApiExceptionMapper.class.getName()), processor.indexedClasses().getClassesToIndex());
    }

    @Test
    void everyRecordOfTheEnvelopeIsRegisteredForReflection() {
        var records = processor.envelopeRecords();

        assertIterableEquals(
                List.of(
                        ApiResponse.class.getName(),
                        ApiError.class.getName(),
                        ApiMetadata.class.getName(),
                        ApiLink.class.getName(),
                        RateLimitInfo.class.getName(),
                        PageInfo.class.getName()),
                records.getClassNames());
        assertTrue(records.isConstructors() && records.isMethods() && records.isFields());
    }
}
