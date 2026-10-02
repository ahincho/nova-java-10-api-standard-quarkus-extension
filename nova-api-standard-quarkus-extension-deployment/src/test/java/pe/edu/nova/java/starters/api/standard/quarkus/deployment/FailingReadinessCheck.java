package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Un chequeo de salud caído, para que {@code /q/health/ready} conteste 503: el equivalente del indicador de salud
 * caído del servicio de las pruebas de Spring, con el que {@code /actuator/health} dice {@code "status":"DOWN"}.
 */
@Readiness
@ApplicationScoped
public class FailingReadinessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.down("inventory");
    }
}
