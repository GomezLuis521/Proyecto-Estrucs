package billeteras.modelo;

import java.time.LocalDateTime;

public class OperacionProgramada {

    private final String id;
    private final String usuarioId;
    private final TipoTransaccion tipo;
    private final String origen;
    private final String destino;
    private final long valor;
    private final Periodicidad periodicidad;
    private final String descripcion;
    /** Nivel del usuario al programar: desempata dos operaciones con la misma fecha. */
    private final int prioridadNivel;
    private LocalDateTime fecha;
    private boolean avisada;
    private int ejecuciones;

    public OperacionProgramada(String id, String usuarioId, TipoTransaccion tipo, String origen, String destino,
                               long valor, LocalDateTime fecha, Periodicidad periodicidad, String descripcion,
                               int prioridadNivel) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.origen = origen;
        this.destino = destino;
        this.valor = valor;
        this.fecha = fecha;
        this.periodicidad = periodicidad;
        this.descripcion = descripcion;
        this.prioridadNivel = prioridadNivel;
    }

    /** Mueve la operación a su siguiente fecha. Devuelve false si no se repite. */
    public boolean reprogramar() {
        LocalDateTime siguiente = periodicidad.siguiente(fecha);
        if (siguiente == null) return false;
        fecha = siguiente;
        avisada = false;
        return true;
    }

    public void contarEjecucion() { ejecuciones++; }
    public void marcarAvisada() { avisada = true; }

    public String id() { return id; }
    public String usuarioId() { return usuarioId; }
    public TipoTransaccion tipo() { return tipo; }
    public String origen() { return origen; }
    public String destino() { return destino; }
    public long valor() { return valor; }
    public Periodicidad periodicidad() { return periodicidad; }
    public String descripcion() { return descripcion; }
    public int prioridadNivel() { return prioridadNivel; }
    public LocalDateTime fecha() { return fecha; }
    public boolean avisada() { return avisada; }
    public int ejecuciones() { return ejecuciones; }
}
