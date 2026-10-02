package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.api.standard.error.ApiError;
import pe.edu.nova.java.libs.api.standard.error.TraceIdSource;
import pe.edu.nova.java.libs.api.standard.link.ApiLink;
import pe.edu.nova.java.libs.api.standard.metadata.ApiMetadata;
import pe.edu.nova.java.libs.api.standard.page.PageInfo;
import pe.edu.nova.java.libs.api.standard.ratelimit.RateLimitInfo;
import pe.edu.nova.java.libs.api.standard.response.ApiResponse;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorPortProducers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorResponder;
import pe.edu.nova.java.starters.api.standard.quarkus.error.MdcTraceIdSource;
import pe.edu.nova.java.starters.api.standard.quarkus.error.MicrometerErrorCounterProducer;
import pe.edu.nova.java.starters.api.standard.quarkus.error.NoopErrorCounterProducer;
import pe.edu.nova.java.starters.api.standard.quarkus.error.NovaExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.SecurityExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ValidationExceptionMappers;
import pe.edu.nova.java.starters.api.standard.quarkus.jackson.ApiObjectMapperCustomizer;

/** Lo que la extensión registra al construir: sin esto, un servicio no la descubre y una imagen nativa no responde. */
class NovaApiStandardProcessorTest {

    private final NovaApiStandardProcessor processor = new NovaApiStandardProcessor();

    @Test
    void theFeatureIsListedWithTheNameOfTheExtension() {
        assertEquals("nova-api-standard", processor.feature().getName());
    }

    @Test
    void aServiceWithoutOptionalExtensionsGetsTheCoreAndTheEmptyCounter() {
        var beans = processor.beans(capabilities(), Optional.empty());

        assertEquals(
                List.of(
                        ApiObjectMapperCustomizer.class.getName(),
                        ErrorPortProducers.class.getName(),
                        ErrorResponder.class.getName(),
                        NovaExceptionMappers.class.getName(),
                        NoopErrorCounterProducer.class.getName()),
                beans.getBeanClasses());
        assertFalse(beans.isRemovable(), "nadie inyecta estos beans por su clase, así que ArC no puede quitarlos");
    }

    @Test
    void micrometerReplacesTheEmptyCounterInsteadOfAddingASecondDefaultBean() {
        var beans = processor.beans(capabilities(), Optional.of(metricsOf(MetricsFactory.MICROMETER)));

        assertTrue(beans.getBeanClasses().contains(MicrometerErrorCounterProducer.class.getName()));
        assertFalse(beans.getBeanClasses().contains(NoopErrorCounterProducer.class.getName()));
    }

    @Test
    void anotherMetricsSystemKeepsTheEmptyCounterBecauseTheRegistryOfMicrometerIsNotThere() {
        var beans = processor.beans(capabilities(), Optional.of(metricsOf("other")));

        assertFalse(beans.getBeanClasses().contains(MicrometerErrorCounterProducer.class.getName()));
        assertTrue(beans.getBeanClasses().contains(NoopErrorCounterProducer.class.getName()));
    }

    @Test
    void theValidationAndSecurityMappersNeedTheirExtension() {
        var withBoth = processor.beans(
                capabilities(Capability.HIBERNATE_VALIDATOR, Capability.SECURITY), Optional.empty());
        var withNeither = processor.beans(capabilities(), Optional.empty());

        assertTrue(withBoth.getBeanClasses().contains(ValidationExceptionMappers.class.getName()));
        assertTrue(withBoth.getBeanClasses().contains(SecurityExceptionMappers.class.getName()));
        assertFalse(withNeither.getBeanClasses().contains(ValidationExceptionMappers.class.getName()));
        assertFalse(withNeither.getBeanClasses().contains(SecurityExceptionMappers.class.getName()));
    }

    @Test
    void everyClassWithServerExceptionMapperIsIndexed() {
        assertEquals(
                Set.of(NovaExceptionMappers.class.getName()),
                processor.indexedClasses(capabilities()).getClassesToIndex());
        assertEquals(
                Set.of(
                        NovaExceptionMappers.class.getName(),
                        ValidationExceptionMappers.class.getName(),
                        SecurityExceptionMappers.class.getName()),
                processor.indexedClasses(capabilities(Capability.HIBERNATE_VALIDATOR, Capability.SECURITY))
                        .getClassesToIndex());
    }

    @Test
    void theTraceIdSourceIsRegisteredForTheNativeImage() {
        var source = processor.traceIdSource();

        assertEquals("META-INF/services/" + TraceIdSource.class.getName(), source.serviceDescriptorFile());
        assertEquals(List.of(MdcTraceIdSource.class.getName()), source.providers());
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

    private static Capabilities capabilities(String... names) {
        return new Capabilities(Set.of(names));
    }

    /** Lo que produce una extensión de métricas: dice qué sistema de métricas es el suyo. */
    private static MetricsCapabilityBuildItem metricsOf(String system) {
        return new MetricsCapabilityBuildItem(system::equals);
    }
}
