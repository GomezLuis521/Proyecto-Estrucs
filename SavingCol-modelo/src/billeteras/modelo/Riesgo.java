package billeteras.modelo;

public enum Riesgo {
    NINGUNO, BAJO, MEDIO, ALTO;

    public Riesgo mayor(Riesgo otro) { return otro.ordinal() > ordinal() ? otro : this; }
}
