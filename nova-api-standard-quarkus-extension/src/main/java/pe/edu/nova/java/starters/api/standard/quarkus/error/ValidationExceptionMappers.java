package pe.edu.nova.java.starters.api.standard.quarkus.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import pe.edu.nova.java.libs.api.standard.error.FieldError;

/**
 * La validación con Bean Validation (ADR-050): lee un {@link ConstraintViolationException} por lo que
 * validó.
 * <ul>
 *   <li>Sobre la entrada es un 400 con un error por violación, cada uno con su campo.</li>
 *   <li>Sobre el valor de retorno es un defecto del servicio, no del cliente: un 500 {@code platform}, con el
 *       mensaje genérico del catálogo.</li>
 * </ul>
 * El campo de una violación es el último nodo de su ruta de propiedad: {@code create.request.name} se escribe
 * {@code name}. Una violación de toda la clase lleva el campo vacío.
 * <p>
 * Esta clase nombra {@code jakarta.validation}, así que el módulo de deployment la registra solo si el
 * servicio tiene la capacidad {@code io.quarkus.hibernate.validator}.
 */
@Singleton
public class ValidationExceptionMappers {

    /** Mensaje de una violación que no trae uno propio. */
    private static final String INVALID_VALUE_MESSAGE = "Valor inválido";

    private final ErrorResponder responder;

    /**
     * Crea los mappers.
     *
     * @param responder el núcleo que registra y responde
     */
    @Inject
    public ValidationExceptionMappers(ErrorResponder responder) {
        this.responder = Objects.requireNonNull(responder, "responder es obligatorio");
    }

    /**
     * Responde una validación fallida.
     *
     * @param exception la excepción, con las violaciones
     * @return la respuesta que deciden los puertos
     */
    @ServerExceptionMapper
    public Response constraintViolation(ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        if (violations == null) {
            violations = Set.of();
        }
        if (validatesReturnValue(violations)) {
            return responder.respondFramework(500, null, List.of(), null, exception);
        }
        List<FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            fields.add(FieldError.of(fieldOf(violation), messageOf(violation)));
        }
        return responder.respondFramework(400, null, fields, null, exception);
    }

    /**
     * Indica si alguna violación es del valor de retorno de un método.
     */
    private static boolean validatesReturnValue(Set<ConstraintViolation<?>> violations) {
        for (ConstraintViolation<?> violation : violations) {
            for (Path.Node node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.RETURN_VALUE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * El campo de una violación: el último nodo de su ruta, o la cadena vacía si es de toda la clase.
     */
    static String fieldOf(ConstraintViolation<?> violation) {
        Path.Node last = null;
        for (Path.Node node : violation.getPropertyPath()) {
            // Un elemento de un contenedor, como el de una lista, se nombra por su propiedad
            if (node.getKind() != ElementKind.CONTAINER_ELEMENT) {
                last = node;
            }
        }
        if (last == null || last.getName() == null || isClassLevel(violation, last)) {
            return "";
        }
        return last.getKind() == ElementKind.PROPERTY || last.getKind() == ElementKind.PARAMETER ? last.getName() : "";
    }

    /**
     * Una restricción que se declara sobre la clase y no sobre un campo no tiene campo propio. Fallar sobre el
     * parámetro que es el objeto entero se reconoce porque lo inválido es el mismo objeto que lo contiene.
     */
    private static boolean isClassLevel(ConstraintViolation<?> violation, Path.Node last) {
        return last.getKind() == ElementKind.BEAN
                || (last.getKind() == ElementKind.PARAMETER
                        && violation.getLeafBean() != null
                        && violation.getLeafBean() == violation.getInvalidValue());
    }

    private static String messageOf(ConstraintViolation<?> violation) {
        String message = violation.getMessage();
        return message != null && !message.isBlank() ? message : INVALID_VALUE_MESSAGE;
    }
}
