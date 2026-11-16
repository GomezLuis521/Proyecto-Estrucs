package billeteras.modelo;

/**
 * Niveles de fidelización y sus ventajas: bono de puntos, comisión reducida
 * en transferencias a otros usuarios y mayor límite por operación.
 */
public enum Nivel {
    BRONCE("Bronce", 0, 100, 100, 5_000),
    PLATA("Plata", 501, 110, 75, 10_000),
    ORO("Oro", 1001, 125, 50, 25_000),
    PLATINO("Platino", 5001, 150, 0, 100_000);

    private final String etiqueta;
    private final long desde;
    private final int bonoPuntosPorcentaje;
    private final int comisionPuntosBasicos;
    private final long limitePorOperacion;

    Nivel(String etiqueta, long desde, int bonoPuntosPorcentaje, int comisionPuntosBasicos, long limitePorOperacion) {
        this.etiqueta = etiqueta;
        this.desde = desde;
        this.bonoPuntosPorcentaje = bonoPuntosPorcentaje;
        this.comisionPuntosBasicos = comisionPuntosBasicos;
        this.limitePorOperacion = limitePorOperacion;
    }

    public static Nivel de(long puntos) {
        Nivel nivel = BRONCE;
        for (Nivel n : values()) if (puntos >= n.desde) nivel = n;
        return nivel;
    }

    /** Siguiente nivel, o null si ya es el más alto. */
    public Nivel siguiente() { return this == PLATINO ? null : values()[ordinal() + 1]; }

    public String etiqueta() { return etiqueta; }
    public long desde() { return desde; }
    public int bonoPuntosPorcentaje() { return bonoPuntosPorcentaje; }
    public int comisionPuntosBasicos() { return comisionPuntosBasicos; }
    public long limitePorOperacion() { return limitePorOperacion; }
}
