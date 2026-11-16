package billeteras.modelo;

import billeteras.estructuras.ListaEnlazada;

public class Billetera {

    private final String codigo;
    private final String propietarioId;
    private final TipoBilletera tipo;
    private String nombre;
    private long saldo;
    private boolean activa = true;
    /** Lista: los movimientos se agregan al final y se consultan en orden. */
    private final ListaEnlazada<Transaccion> historial = new ListaEnlazada<>();

    public Billetera(String codigo, String propietarioId, String nombre, TipoBilletera tipo) {
        this.codigo = codigo;
        this.propietarioId = propietarioId;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public void abonar(long valor) { saldo += valor; }
    public void debitar(long valor) { saldo -= valor; }

    public String codigo() { return codigo; }
    public String propietarioId() { return propietarioId; }
    public TipoBilletera tipo() { return tipo; }
    public String nombre() { return nombre; }
    public void renombrar(String nombre) { this.nombre = nombre; }
    public long saldo() { return saldo; }
    public boolean activa() { return activa; }
    public void activar(boolean activa) { this.activa = activa; }
    public ListaEnlazada<Transaccion> historial() { return historial; }
}
