package billeteras.modelo;

import java.time.LocalDateTime;

public class Transaccion {

    private final String id;
    private final LocalDateTime fecha;
    private final TipoTransaccion tipo;
    private final long valor;
    private final String usuarioId;
    private final String origen;
    private final String destino;
    private final boolean programada;
    private final String referencia;
    private EstadoTransaccion estado = EstadoTransaccion.EXITOSA;
    private Riesgo riesgo = Riesgo.NINGUNO;
    private long puntos;
    private long comision;
    private long saldoOrigen = -1;
    private long saldoDestino = -1;
    private String motivo;

    public Transaccion(String id, LocalDateTime fecha, TipoTransaccion tipo, long valor, String usuarioId,
                       String origen, String destino, boolean programada, String referencia) {
        this.id = id;
        this.fecha = fecha;
        this.tipo = tipo;
        this.valor = valor;
        this.usuarioId = usuarioId;
        this.origen = origen;
        this.destino = destino;
        this.programada = programada;
        this.referencia = referencia;
    }

    public void rechazar(String motivo) {
        estado = EstadoTransaccion.RECHAZADA;
        this.motivo = motivo;
    }

    public void marcarRevertida() { estado = EstadoTransaccion.REVERTIDA; }
    public void subirRiesgo(Riesgo nuevo) { riesgo = riesgo.mayor(nuevo); }
    public void asignarPuntos(long puntos) { this.puntos = puntos; }
    public void asignarComision(long comision) { this.comision = comision; }

    public void asignarSaldos(long saldoOrigen, long saldoDestino) {
        this.saldoOrigen = saldoOrigen;
        this.saldoDestino = saldoDestino;
    }

    public boolean exitosa() { return estado == EstadoTransaccion.EXITOSA; }

    public String id() { return id; }
    public LocalDateTime fecha() { return fecha; }
    public TipoTransaccion tipo() { return tipo; }
    public long valor() { return valor; }
    public String usuarioId() { return usuarioId; }
    public String origen() { return origen; }
    public String destino() { return destino; }
    public boolean programada() { return programada; }
    public String referencia() { return referencia; }
    public EstadoTransaccion estado() { return estado; }
    public Riesgo riesgo() { return riesgo; }
    public long puntos() { return puntos; }
    public long comision() { return comision; }
    public long saldoOrigen() { return saldoOrigen; }
    public long saldoDestino() { return saldoDestino; }
    public String motivo() { return motivo; }
}
