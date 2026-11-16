package billeteras.modelo;

import java.time.LocalDateTime;

public enum Periodicidad {
    UNICA("Una vez"), DIARIA("Cada día"), SEMANAL("Cada semana"), MENSUAL("Cada mes");

    private final String etiqueta;

    Periodicidad(String etiqueta) { this.etiqueta = etiqueta; }

    public String etiqueta() { return etiqueta; }

    /** Fecha de la siguiente ejecución, o null si la operación no se repite. */
    public LocalDateTime siguiente(LocalDateTime fecha) {
        return switch (this) {
            case UNICA -> null;
            case DIARIA -> fecha.plusDays(1);
            case SEMANAL -> fecha.plusWeeks(1);
            case MENSUAL -> fecha.plusMonths(1);
        };
    }
}
