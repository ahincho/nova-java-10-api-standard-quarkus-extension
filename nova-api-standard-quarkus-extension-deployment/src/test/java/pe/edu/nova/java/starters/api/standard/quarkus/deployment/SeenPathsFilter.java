package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Un filtro de respuesta de JAX-RS que solo anota por qué rutas pasa. Prueba qué respuestas ve la cadena de filtros
 * de Quarkus REST, de la que es parte el filtro del sobre de éxito, y cuáles contestan sin pasar por ella.
 */
@Path("/seen")
public class SeenPathsFilter {

    private static final List<String> PATHS = new CopyOnWriteArrayList<>();

    /**
     * Anota la ruta de cada respuesta que pasa por la cadena de filtros.
     *
     * @param request la petición
     */
    @ServerResponseFilter
    public void remember(ContainerRequestContext request) {
        PATHS.add(request.getMethod() + " " + request.getUriInfo().getPath());
    }

    /**
     * Las rutas anotadas hasta ahora, una por línea.
     *
     * @return las rutas, como texto
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String paths() {
        return String.join("\n", PATHS);
    }
}
