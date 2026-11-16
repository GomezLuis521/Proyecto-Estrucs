package billeteras.modelo;

public enum TipoBilletera {
    AHORRO("Ahorro"),
    GASTOS_DIARIOS("Gastos diarios"),
    COMPRAS("Compras"),
    TRANSPORTE("Transporte"),
    INVERSION("Inversión");

    private final String etiqueta;

    TipoBilletera(String etiqueta) { this.etiqueta = etiqueta; }

    public String etiqueta() { return etiqueta; }
}
