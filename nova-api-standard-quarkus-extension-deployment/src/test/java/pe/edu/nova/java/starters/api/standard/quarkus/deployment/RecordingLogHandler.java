package pe.edu.nova.java.starters.api.standard.quarkus.deployment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import pe.edu.nova.java.starters.api.standard.quarkus.error.ErrorResponder;

/**
 * Guarda las líneas de log del núcleo con su MDC. El MDC se copia al publicar, no al leer: un handler de
 * consola o de JSON lo lee en ese mismo instante, y después el núcleo ya lo devolvió a como estaba.
 */
final class RecordingLogHandler extends ExtHandler {

    /**
     * Una línea de log.
     *
     * @param level   el nivel
     * @param message el texto ya formateado
     * @param thrown  la excepción con su stack trace, o null
     * @param mdc     las entradas del MDC al momento de escribirla
     */
    record Line(Level level, String message, Throwable thrown, Map<String, String> mdc) {

        boolean isWarning() {
            return level.intValue() == Level.WARNING.intValue();
        }

        boolean isError() {
            return level.intValue() == Level.SEVERE.intValue();
        }
    }

    private final List<Line> lines = new CopyOnWriteArrayList<>();

    /**
     * Conecta el handler al logger del núcleo.
     *
     * @return este handler, para encadenar
     */
    RecordingLogHandler attach() {
        java.util.logging.Logger.getLogger(ErrorResponder.class.getName()).addHandler(this);
        return this;
    }

    /** Lo suelta del logger del núcleo. */
    void detach() {
        java.util.logging.Logger.getLogger(ErrorResponder.class.getName()).removeHandler(this);
    }

    /** Olvida lo que se escribió hasta ahora. */
    void clear() {
        lines.clear();
    }

    /**
     * Lo que se escribió desde el último {@link #clear()}.
     *
     * @return las líneas, en orden
     */
    List<Line> lines() {
        return List.copyOf(lines);
    }

    @Override
    protected void doPublish(ExtLogRecord record) {
        lines.add(new Line(record.getLevel(), record.getFormattedMessage(), record.getThrown(), record.getMdcCopy()));
    }
}
