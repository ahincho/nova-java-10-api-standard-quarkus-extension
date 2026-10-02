package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Los tres lugares donde Bean Validation falla en un recurso: un campo, un parámetro y toda la clase. */
@Path("/validated")
public class ValidationResource {

    /** Un pedido por crear, con restricciones sobre sus campos y sobre los elementos de una lista. */
    public record NewOrder(
            @NotBlank(message = "El nombre es obligatorio") String name,
            @Positive(message = "La cantidad debe ser positiva") int quantity,
            @NotEmpty(message = "Faltan las etiquetas") List<@NotBlank(message = "La etiqueta no puede estar vacía") String> tags) {}

    /** Un rango cuya restricción se declara sobre la clase y no sobre un campo. */
    @ValidRange
    public record Range(int min, int max) {}

    /** El máximo tiene que ser mayor que el mínimo. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Constraint(validatedBy = RangeValidator.class)
    public @interface ValidRange {

        String message() default "El máximo tiene que ser mayor que el mínimo";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Valida un {@link Range}. */
    public static class RangeValidator implements ConstraintValidator<ValidRange, Range> {

        @Override
        public boolean isValid(Range range, ConstraintValidatorContext context) {
            return range == null || range.max() > range.min();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String create(@Valid NewOrder request) {
        return "creado";
    }

    @GET
    @Path("/top")
    public String top(@QueryParam("limit") @Max(value = 10, message = "No se pueden pedir más de 10") int limit) {
        return "ok";
    }

    @POST
    @Path("/range")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String range(@Valid Range range) {
        return "ok";
    }

    @GET
    @Path("/forgotten")
    @NotNull(message = "El servicio devolvió nada")
    public String forgotten() {
        return null;
    }
}
