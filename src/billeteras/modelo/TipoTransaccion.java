package billeteras.modelo;

/** Tipos de movimiento y los puntos base que genera cada uno por cada 100 unidades. */
public enum TipoTransaccion {
    RECARGA("Recarga", 1),
    RETIRO("Retiro", 2),
    TRANSFERENCIA("Transferencia", 3),
    REVERSION("Reversión", 0),
    BENEFICIO("Beneficio", 0);

    private final String etiqueta;
    private final int puntosPorCien;

    TipoTransaccion(String etiqueta, int puntosPorCien) {
        this.etiqueta = etiqueta;
        this.puntosPorCien = puntosPorCien;
    }

    public String etiqueta() { return etiqueta; }
    public int puntosPorCien() { return puntosPorCien; }


    public boolean reversible() { return puntosPorCien > 0; }
}
