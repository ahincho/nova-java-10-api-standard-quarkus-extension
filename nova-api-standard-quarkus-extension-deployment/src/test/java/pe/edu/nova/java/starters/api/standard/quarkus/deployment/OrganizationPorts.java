package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import pe.edu.nova.java.libs.api.standard.error.CatalogEntry;
import pe.edu.nova.java.libs.api.standard.error.DomainError;
import pe.edu.nova.java.libs.api.standard.error.ErrorCatalog;
import pe.edu.nova.java.libs.api.standard.error.ErrorSerializer;
import pe.edu.nova.java.libs.api.standard.error.ErrorStatusMapper;
import pe.edu.nova.java.libs.api.standard.error.NovaErrorStatusMapper;
import pe.edu.nova.java.libs.api.standard.error.SerializedError;

/**
 * Los puertos de una organización, como UTP: su catálogo, un serializador con RFC 7807 y un mapeador de status.
 * Un servicio los declara como beans y la extensión los usa en lugar de los de Nova, sin forkear.
 */
@Singleton
public class OrganizationPorts {

    @Produces
    @Singleton
    ErrorCatalog organizationCatalog() {
        return failure -> new CatalogEntry("ORG-" + failure.status(), "Texto de la organización");
    }

    @Produces
    @Singleton
    ErrorSerializer organizationSerializer() {
        return failure -> {
            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("status", failure.status());
            problem.put("title", failure.code());
            problem.put("detail", failure.message());
            // El fallo entero, para probar que no trae al proveedor ni la causa
            problem.put("failure", failure.toString());
            return new SerializedError(failure.status(), problem,
                    Map.of("Content-Type", "application/problem+json", "X-Organization", "utp"));
        };
    }

    @Produces
    @Singleton
    ErrorStatusMapper organizationStatusMapper() {
        NovaErrorStatusMapper nova = new NovaErrorStatusMapper();
        // Lo que no existe, la organización lo contesta 410 en vez de 404
        return type -> type == DomainError.Type.NOT_FOUND ? 410 : nova.statusOf(type);
    }
}
