package billeteras.modelo;

import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.Pila;
import java.time.LocalDateTime;

public class Usuario {

    /** Cuántas notificaciones recientes se conservan por usuario. */
    public static final int MAXIMO_NOTIFICACIONES = 25;

    private final String id;
    private final LocalDateTime fechaRegistro;
    private String nombre;
    private String correo;
    private String telefono;
    private long puntosAcumulados;   // definen el nivel
    private long puntosDisponibles;  // los que se pueden canjear
    private int transferenciasSinComision;

    private final ListaEnlazada<Billetera> billeteras = new ListaEnlazada<>();
    private final ListaEnlazada<Transaccion> historial = new ListaEnlazada<>();
    private final ListaEnlazada<Canje> canjes = new ListaEnlazada<>();
    private final ListaEnlazada<Notificacion> notificaciones = new ListaEnlazada<>();
    /** Pila: la última operación hecha es la primera que se puede deshacer. */
    private final Pila<Transaccion> deshacer = new Pila<>();

    public Usuario(String id, String nombre, String correo, String telefono, LocalDateTime fechaRegistro) {
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.fechaRegistro = fechaRegistro;
    }

    public Nivel nivel() { return Nivel.de(puntosAcumulados); }

    /** Suma (o resta, si es negativo) puntos sin dejar ningún saldo de puntos por debajo de cero. */
    public void ajustarPuntos(long delta) {
        puntosAcumulados = Math.max(0, puntosAcumulados + delta);
        puntosDisponibles = Math.max(0, puntosDisponibles + delta);
    }

    public void gastarPuntos(long puntos) { puntosDisponibles -= puntos; }

    public void recibirNotificacion(Notificacion n) {
        notificaciones.agregarAlInicio(n);
        if (notificaciones.tamano() > MAXIMO_NOTIFICACIONES) notificaciones.eliminarUltimo();
    }

    public long saldoTotal() {
        long total = 0;
        for (Billetera b : billeteras) total += b.saldo();
        return total;
    }

    public void modificar(String nombre, String correo, String telefono) {
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
    }

    public void agregarTransferenciasSinComision(int cantidad) { transferenciasSinComision += cantidad; }
    public void usarTransferenciaSinComision() { transferenciasSinComision--; }

    public String id() { return id; }
    public LocalDateTime fechaRegistro() { return fechaRegistro; }
    public String nombre() { return nombre; }
    public String correo() { return correo; }
    public String telefono() { return telefono; }
    public long puntosAcumulados() { return puntosAcumulados; }
    public long puntosDisponibles() { return puntosDisponibles; }
    public int transferenciasSinComision() { return transferenciasSinComision; }
    public ListaEnlazada<Billetera> billeteras() { return billeteras; }
    public ListaEnlazada<Transaccion> historial() { return historial; }
    public ListaEnlazada<Canje> canjes() { return canjes; }
    public ListaEnlazada<Notificacion> notificaciones() { return notificaciones; }
    public Pila<Transaccion> deshacer() { return deshacer; }
}
