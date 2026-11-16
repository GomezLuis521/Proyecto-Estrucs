package billeteras.servicio;

/** Operación inválida según las reglas de la plataforma. El mensaje se le muestra al usuario tal cual. */
public class ErrorNegocio extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ErrorNegocio(String mensaje) { super(mensaje); }
}
