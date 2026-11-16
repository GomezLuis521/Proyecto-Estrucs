package billeteras.servicio;

import billeteras.estructuras.ArbolAVL;
import billeteras.estructuras.ClaveOrden;
import billeteras.estructuras.Cola;
import billeteras.estructuras.ColaPrioridad;
import billeteras.estructuras.Grafo;
import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.TablaHash;
import billeteras.modelo.Beneficio;
import billeteras.modelo.Billetera;
import billeteras.modelo.Canje;
import billeteras.modelo.EventoAuditoria;
import billeteras.modelo.Nivel;
import billeteras.modelo.Notificacion;
import billeteras.modelo.OperacionProgramada;
import billeteras.modelo.Periodicidad;
import billeteras.modelo.TipoBilletera;
import billeteras.modelo.TipoNotificacion;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

/**Núcleo de la plataforma: guarda el estado y aplica las reglas del negocio.*/
public class Plataforma {

    public record ResultadoAvance(int ejecutadas, int rechazadas) { }

    public static final String CONF_SALDO_BAJO = "saldoBajo";
    public static final String CONF_BONO_PROGRAMADA = "bonoProgramada";
    public static final String CONF_HORAS_AVISO = "horasAvisoProgramada";

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("d/MM HH:mm");

    private final TablaHash<String, Usuario> usuarios = new TablaHash<>();
    private final TablaHash<String, Billetera> billeteras = new TablaHash<>();
    private final TablaHash<String, Long> configuracion = new TablaHash<>();
    private final ArbolAVL<ClaveOrden, Usuario> rankingPuntos = new ArbolAVL<>();
    private final ArbolAVL<ClaveOrden, Transaccion> porValor = new ArbolAVL<>();
    private final ListaEnlazada<Transaccion> transacciones = new ListaEnlazada<>();
    private final ListaEnlazada<EventoAuditoria> auditoria = new ListaEnlazada<>();
    private final Cola<Notificacion> notificacionesPendientes = new Cola<>();
    private final Grafo grafoUsuarios = new Grafo();
    private final Grafo grafoBilleteras = new Grafo();
    private final DetectorInusual detector = new DetectorInusual();
    /** Sale primero la fecha más cercana; si empatan, el usuario de mayor nivel. */
    private final ColaPrioridad<OperacionProgramada> programadas = new ColaPrioridad<>(
        Comparator.comparing(OperacionProgramada::fecha)
            .thenComparing(Comparator.comparingInt(OperacionProgramada::prioridadNivel).reversed())
            .thenComparing(OperacionProgramada::id));

    private LocalDateTime ahora;
    private int consecutivoTransaccion, consecutivoBilletera, consecutivoProgramada, consecutivoNotificacion, consecutivoAuditoria;
    private long notificacionesDespachadas;

    public Plataforma(LocalDateTime inicio) {
        ahora = inicio;
        configuracion.poner(CONF_SALDO_BAJO, 100L);
        configuracion.poner(CONF_BONO_PROGRAMADA, 10L);
        configuracion.poner(CONF_HORAS_AVISO, 24L);
    }


    public Usuario registrarUsuario(String id, String nombre, String correo, String telefono) {
        id = requerido(id, "la identificación");
        if (!id.matches("\\d{5,12}")) throw new ErrorNegocio("La identificación debe tener entre 5 y 12 dígitos.");
        if (usuarios.contiene(id)) throw new ErrorNegocio("Ya existe un usuario con la identificación " + id + ".");
        Usuario u = new Usuario(id, requerido(nombre, "el nombre"), correoValido(correo), telefonoValido(telefono), ahora);
        usuarios.poner(id, u);
        rankingPuntos.insertar(clave(u), u);
        grafoUsuarios.agregarVertice(id);
        return u;
    }

    public Usuario modificarUsuario(String id, String nombre, String correo, String telefono) {
        Usuario u = usuario(id);
        u.modificar(requerido(nombre, "el nombre"), correoValido(correo), telefonoValido(telefono));
        return u;
    }

    public void eliminarUsuario(String id) {
        Usuario u = usuario(id);
        if (u.saldoTotal() > 0) throw new ErrorNegocio("No se puede eliminar a " + u.nombre() + ": aún tiene "
            + dinero(u.saldoTotal()) + " en sus billeteras. Retira o transfiere ese saldo primero.");
        for (Billetera b : u.billeteras()) {
            billeteras.eliminar(b.codigo());
            grafoBilleteras.eliminarVertice(b.codigo());
        }
        programadas.eliminarSi(op -> op.usuarioId().equals(id));
        rankingPuntos.eliminar(clave(u));
        grafoUsuarios.eliminarVertice(id);
        usuarios.eliminar(id);
    }

    /** Búsqueda por identificación en la tabla hash*/
    public Usuario usuario(String id) {
        Usuario u = usuarios.obtener(id);
        if (u == null) throw new ErrorNegocio("No existe un usuario con la identificación " + id + ".");
        return u;
    }



    public Billetera crearBilletera(String usuarioId, String nombre, TipoBilletera tipo) {
        Usuario u = usuario(usuarioId);
        String limpio = requerido(nombre, "el nombre de la billetera");
        if (tipo == null) throw new ErrorNegocio("Elige el tipo de billetera.");
        if (u.billeteras().buscar(b -> b.nombre().equalsIgnoreCase(limpio)) != null)
            throw new ErrorNegocio(u.nombre() + " ya tiene una billetera llamada «" + limpio + "».");
        Billetera b = new Billetera(String.format("BIL-%04d", ++consecutivoBilletera), usuarioId, limpio, tipo);
        billeteras.poner(b.codigo(), b);
        u.billeteras().agregar(b);
        grafoBilleteras.agregarVertice(b.codigo());
        return b;
    }

    public void renombrarBilletera(String codigo, String nombre) {
        billetera(codigo).renombrar(requerido(nombre, "el nombre de la billetera"));
    }

    public void cambiarEstadoBilletera(String codigo, boolean activa) { billetera(codigo).activar(activa); }

    public void eliminarBilletera(String codigo) {
        Billetera b = billetera(codigo);
        if (b.saldo() > 0) throw new ErrorNegocio("La billetera " + b.nombre() + " aún tiene " + dinero(b.saldo())
            + ". Retira o transfiere ese saldo antes de eliminarla.");
        programadas.eliminarSi(op -> codigo.equals(op.origen()) || codigo.equals(op.destino()));
        usuario(b.propietarioId()).billeteras().eliminarSi(x -> x == b);
        billeteras.eliminar(codigo);
        grafoBilleteras.eliminarVertice(codigo);
    }

    /** Búsqueda por código en la tabla hash */
    public Billetera billetera(String codigo) {
        Billetera b = billeteras.obtener(codigo);
        if (b == null) throw new ErrorNegocio("No existe la billetera " + (codigo == null || codigo.isBlank() ? "indicada" : codigo) + ".");
        return b;
    }



    public Transaccion recargar(String usuarioId, String destino, long valor) {
        return ejecutar(TipoTransaccion.RECARGA, usuarioId, null, destino, valor, null);
    }

    public Transaccion retirar(String usuarioId, String origen, long valor) {
        return ejecutar(TipoTransaccion.RETIRO, usuarioId, origen, null, valor, null);
    }

    public Transaccion transferir(String usuarioId, String origen, String destino, long valor) {
        return ejecutar(TipoTransaccion.TRANSFERENCIA, usuarioId, origen, destino, valor, null);
    }

    private Transaccion ejecutar(TipoTransaccion tipo, String usuarioId, String origen, String destino,
                                 long valor, String operacionProgramada) {
        Usuario u = usuario(usuarioId);
        boolean programada = operacionProgramada != null;
        Transaccion t = new Transaccion(String.format("TX-%06d", ++consecutivoTransaccion), ahora, tipo, valor,
            usuarioId, origen, destino, programada, operacionProgramada);
        try {
            moverDinero(t, u);
        } catch (ErrorNegocio e) {
            t.rechazar(e.getMessage());
            registrar(t, u);
            notificar(usuarioId, TipoNotificacion.OPERACION_RECHAZADA,
                tipo.etiqueta() + " de " + dinero(valor) + " rechazada. " + e.getMessage());
            despacharNotificaciones();
            throw e;
        }

        long puntos = Recompensas.puntos(tipo, valor, u.nivel());
        if (programada) puntos += configuracion.obtener(CONF_BONO_PROGRAMADA);
        t.asignarPuntos(puntos);
        registrar(t, u);
        porValor.insertar(new ClaveOrden(valor, t.id()), t);
        u.deshacer().apilar(t);

        if (tipo == TipoTransaccion.TRANSFERENCIA) {
            grafoBilleteras.agregarArista(origen, destino, valor);
            String receptor = billeteras.obtener(destino).propietarioId();
            if (!receptor.equals(usuarioId)) grafoUsuarios.agregarArista(usuarioId, receptor, valor);
        }
        ajustarPuntos(u, puntos);

        Billetera debitada = origen == null ? null : billeteras.obtener(origen);
        if (debitada != null && debitada.saldo() < configuracion.obtener(CONF_SALDO_BAJO))
            notificar(usuarioId, TipoNotificacion.SALDO_BAJO,
                "Saldo bajo en " + debitada.nombre() + ": quedan " + dinero(debitada.saldo()) + ".");

        if (!programada) auditar(t, u);
        despacharNotificaciones();
        return t;
    }

    /** Valida y aplica el movimiento. Si algo no cumple, lanza ErrorNegocio sin tocar ningún saldo. */
    private void moverDinero(Transaccion t, Usuario u) {
        TipoTransaccion tipo = t.tipo();
        long valor = t.valor();
        Nivel nivel = u.nivel();
        if (valor <= 0) throw new ErrorNegocio("El valor debe ser mayor que cero.");
        if (valor > nivel.limitePorOperacion()) throw new ErrorNegocio("El nivel " + nivel.etiqueta()
            + " permite hasta " + dinero(nivel.limitePorOperacion()) + " por operación.");

        Billetera origen = tipo == TipoTransaccion.RECARGA ? null : billeteraOperable(t.origen(), "de origen");
        Billetera destino = tipo == TipoTransaccion.RETIRO ? null : billeteraOperable(t.destino(), "de destino");
        Billetera propia = origen != null ? origen : destino;
        if (!propia.propietarioId().equals(u.id()))
            throw new ErrorNegocio("La billetera " + propia.codigo() + " no pertenece a " + u.nombre() + ".");
        if (origen == destino) throw new ErrorNegocio("El origen y el destino son la misma billetera.");

        boolean aOtroUsuario = tipo == TipoTransaccion.TRANSFERENCIA && !destino.propietarioId().equals(u.id());
        boolean usaBeneficio = aOtroUsuario && nivel.comisionPuntosBasicos() > 0 && u.transferenciasSinComision() > 0;
        long comision = aOtroUsuario && !usaBeneficio ? valor * nivel.comisionPuntosBasicos() / 10_000 : 0;
        if (origen != null && origen.saldo() < valor + comision)
            throw new ErrorNegocio("Saldo insuficiente en " + origen.nombre() + ": tiene " + dinero(origen.saldo())
                + " y la operación necesita " + dinero(valor + comision) + ".");

        if (origen != null) origen.debitar(valor + comision);
        if (destino != null) destino.abonar(valor);
        if (usaBeneficio) u.usarTransferenciaSinComision();
        t.asignarComision(comision);
        t.asignarSaldos(origen == null ? -1 : origen.saldo(), destino == null ? -1 : destino.saldo());
    }

    private Billetera billeteraOperable(String codigo, String papel) {
        if (codigo == null || codigo.isBlank()) throw new ErrorNegocio("Elige la billetera " + papel + ".");
        Billetera b = billetera(codigo);
        if (!b.activa()) throw new ErrorNegocio("La billetera " + papel + " (" + b.nombre() + ") está inactiva.");
        return b;
    }

    /** Agrega la transacción a las listas de historial */
    private void registrar(Transaccion t, Usuario u) {
        transacciones.agregar(t);
        u.historial().agregar(t);
        registrarEnBilletera(t, t.origen(), u);
        registrarEnBilletera(t, t.destino(), u);
    }

    private void registrarEnBilletera(Transaccion t, String codigo, Usuario u) {
        Billetera b = codigo == null ? null : billeteras.obtener(codigo);
        if (b == null) return;
        b.historial().agregar(t);
        Usuario contraparte = usuarios.obtener(b.propietarioId());
        if (contraparte != null && contraparte != u) contraparte.historial().agregar(t);
    }

    /** Cambia los puntos y reubica al usuario en el árbol*/
    private void ajustarPuntos(Usuario u, long delta) {
        if (delta == 0) return;
        Nivel antes = u.nivel();
        rankingPuntos.eliminar(clave(u));
        u.ajustarPuntos(delta);
        rankingPuntos.insertar(clave(u), u);
        Nivel despues = u.nivel();
        if (despues.ordinal() > antes.ordinal())
            notificar(u.id(), TipoNotificacion.CAMBIO_NIVEL, "Subiste a nivel " + despues.etiqueta() + " con "
                + u.puntosAcumulados() + " puntos. Tu límite por operación ahora es " + dinero(despues.limitePorOperacion()) + ".");
        else if (despues != antes)
            notificar(u.id(), TipoNotificacion.CAMBIO_NIVEL, "Volviste a nivel " + despues.etiqueta()
                + " porque se descontaron puntos de una operación revertida.");
    }

    private void auditar(Transaccion t, Usuario u) {
        ListaEnlazada<DetectorInusual.Hallazgo> hallazgos = detector.evaluar(t, u, billeteras);
        if (hallazgos.estaVacia()) return;
        StringBuilder reglas = new StringBuilder();
        for (DetectorInusual.Hallazgo h : hallazgos) {
            t.subirRiesgo(h.riesgo());
            auditoria.agregar(new EventoAuditoria(String.format("AUD-%04d", ++consecutivoAuditoria), ahora, u.id(),
                t.id(), h.regla(), h.riesgo(), h.detalle()));
            reglas.append(reglas.isEmpty() ? "" : ", ").append(h.regla().toLowerCase(Locale.ROOT));
        }
        notificar(u.id(), TipoNotificacion.SEGURIDAD, "Alerta de seguridad en " + t.id() + " (riesgo "
            + t.riesgo().name().toLowerCase(Locale.ROOT) + "): " + reglas + ".");
    }



    /** Deshace la operación que está en la cima de la pila del usuario: devuelve el dinero, descuenta los puntos que había generado y deja constancia en el historial.*/
    public Transaccion revertirUltima(String usuarioId) {
        Usuario u = usuario(usuarioId);
        Transaccion t = u.deshacer().cima();
        if (t == null) throw new ErrorNegocio(u.nombre() + " no tiene operaciones para deshacer.");

        Billetera origen = t.origen() == null ? null : billeteras.obtener(t.origen());
        Billetera destino = t.destino() == null ? null : billeteras.obtener(t.destino());
        if ((t.origen() != null && origen == null) || (t.destino() != null && destino == null))
            throw new ErrorNegocio("No se puede deshacer " + t.id() + ": una de sus billeteras ya no existe.");
        if (destino != null && destino.saldo() < t.valor())
            throw new ErrorNegocio("No se puede deshacer " + t.id() + ": " + destino.nombre() + " solo tiene "
                + dinero(destino.saldo()) + " y habría que devolver " + dinero(t.valor()) + ".");

        u.deshacer().desapilar();
        if (destino != null) destino.debitar(t.valor());
        if (origen != null) origen.abonar(t.valor() + t.comision());
        t.marcarRevertida();
        porValor.eliminar(new ClaveOrden(t.valor(), t.id()));
        if (t.tipo() == TipoTransaccion.TRANSFERENCIA) {
            grafoBilleteras.quitarArista(t.origen(), t.destino(), t.valor());
            if (!destino.propietarioId().equals(usuarioId))
                grafoUsuarios.quitarArista(usuarioId, destino.propietarioId(), t.valor());
        }

        Transaccion reversion = new Transaccion(String.format("TX-%06d", ++consecutivoTransaccion), ahora,
            TipoTransaccion.REVERSION, t.valor(), usuarioId, t.destino(), t.origen(), false, t.id());
        reversion.asignarPuntos(-t.puntos());
        reversion.asignarSaldos(destino == null ? -1 : destino.saldo(), origen == null ? -1 : origen.saldo());
        registrar(reversion, u);
        ajustarPuntos(u, -t.puntos());
        notificar(usuarioId, TipoNotificacion.REVERSION, "Se revirtió " + t.id() + " (" + t.tipo().etiqueta().toLowerCase(Locale.ROOT)
            + " de " + dinero(t.valor()) + ") y se descontaron " + t.puntos() + " puntos.");
        despacharNotificaciones();
        return reversion;
    }



    public OperacionProgramada programar(String usuarioId, TipoTransaccion tipo, String origen, String destino, long valor,
                                         LocalDateTime fecha, Periodicidad periodicidad, String descripcion) {
        Usuario u = usuario(usuarioId);
        if (tipo == null || !tipo.reversible()) throw new ErrorNegocio("Solo se programan recargas, retiros y transferencias.");
        if (valor <= 0) throw new ErrorNegocio("El valor debe ser mayor que cero.");
        if (fecha == null || !fecha.isAfter(ahora))
            throw new ErrorNegocio("La fecha debe ser posterior al reloj de la plataforma (" + ahora.format(FECHA_CORTA) + ").");
        if (periodicidad == null) periodicidad = Periodicidad.UNICA;
        origen = tipo == TipoTransaccion.RECARGA ? null : billetera(origen).codigo();
        destino = tipo == TipoTransaccion.RETIRO ? null : billetera(destino).codigo();
        String propia = origen != null ? origen : destino;
        if (!billetera(propia).propietarioId().equals(usuarioId))
            throw new ErrorNegocio("La billetera " + propia + " no pertenece a " + u.nombre() + ".");
        if (propia.equals(destino) && origen != null) throw new ErrorNegocio("El origen y el destino son la misma billetera.");

        OperacionProgramada op = new OperacionProgramada(String.format("OP-%04d", ++consecutivoProgramada), usuarioId, tipo,
            origen, destino, valor, fecha, periodicidad,
            descripcion == null || descripcion.isBlank() ? tipo.etiqueta() + " programada" : descripcion.trim(),
            u.nivel().ordinal());
        programadas.insertar(op);
        avisarProximas();
        despacharNotificaciones();
        return op;
    }

    public void cancelarProgramada(String id) {
        if (programadas.eliminarSi(op -> op.id().equals(id)) == 0)
            throw new ErrorNegocio("No existe la operación programada " + id + ".");
    }

    /**adelanta el reloj y ejecuta, en orden de prioridad, todas las operaciones programadas cuya fecha ya llegó.*/
    public ResultadoAvance avanzarReloj(long horas) {
        if (horas < 1 || horas > 24 * 90) throw new ErrorNegocio("El reloj avanza entre 1 hora y 90 días por vez.");
        LocalDateTime hasta = ahora.plusHours(horas);
        int ejecutadas = 0, rechazadas = 0;
        while (!programadas.estaVacia() && !programadas.verMinimo().fecha().isAfter(hasta)) {
            OperacionProgramada op = programadas.extraerMinimo();
            if (op.fecha().isAfter(ahora)) ahora = op.fecha();
            try {
                Transaccion t = ejecutar(op.tipo(), op.usuarioId(), op.origen(), op.destino(), op.valor(), op.id());
                op.contarEjecucion();
                ejecutadas++;
                notificar(op.usuarioId(), TipoNotificacion.PROGRAMADA_EJECUTADA, "Se ejecutó «" + op.descripcion() + "» por "
                    + dinero(op.valor()) + " y sumó " + t.puntos() + " puntos con bono.");
            } catch (ErrorNegocio e) {
                rechazadas++;
            }
            if (op.reprogramar()) programadas.insertar(op);
        }
        ahora = hasta;
        avisarProximas();
        despacharNotificaciones();
        return new ResultadoAvance(ejecutadas, rechazadas);
    }

    private void avisarProximas() {
        LocalDateTime limite = ahora.plusHours(configuracion.obtener(CONF_HORAS_AVISO));
        for (OperacionProgramada op : programadas.enOrden()) {
            if (op.fecha().isAfter(limite)) break;
            if (op.avisada()) continue;
            op.marcarAvisada();
            notificar(op.usuarioId(), TipoNotificacion.PROGRAMADA_PROXIMA, "«" + op.descripcion() + "» por "
                + dinero(op.valor()) + " se ejecutará el " + op.fecha().format(FECHA_CORTA) + ".");
        }
    }

    public Canje canjear(String usuarioId, String codigoBeneficio) {
        Usuario u = usuario(usuarioId);
        Beneficio b = Recompensas.beneficio(codigoBeneficio);
        if (u.puntosDisponibles() < b.costoPuntos()) throw new ErrorNegocio("Faltan "
            + (b.costoPuntos() - u.puntosDisponibles()) + " puntos para canjear «" + b.nombre() + "».");
        switch (b.codigo()) {
            case Recompensas.BONO_50 -> abonarBono(u, 50, b);
            case Recompensas.BONO_150 -> abonarBono(u, 150, b);
            default -> u.agregarTransferenciasSinComision(3);
        }
        u.gastarPuntos(b.costoPuntos());
        Canje canje = new Canje(b, ahora);
        u.canjes().agregar(canje);
        notificar(usuarioId, TipoNotificacion.CANJE, "Canjeaste «" + b.nombre() + "» por " + b.costoPuntos() + " puntos.");
        despacharNotificaciones();
        return canje;
    }

    private void abonarBono(Usuario u, long valor, Beneficio b) {
        Billetera destino = u.billeteras().buscar(Billetera::activa);
        if (destino == null) throw new ErrorNegocio("Necesitas una billetera activa para recibir el bono.");
        destino.abonar(valor);
        Transaccion t = new Transaccion(String.format("TX-%06d", ++consecutivoTransaccion), ahora, TipoTransaccion.BENEFICIO,
            valor, u.id(), null, destino.codigo(), false, b.codigo());
        t.asignarSaldos(-1, destino.saldo());
        registrar(t, u);
    }



    private void notificar(String usuarioId, TipoNotificacion tipo, String mensaje) {
        notificacionesPendientes.encolar(new Notificacion("N-" + (++consecutivoNotificacion), usuarioId, tipo, mensaje, ahora));
    }

    /** Atiende la cola en orden de llegada y entrega cada alerta a las recientes de su usuario. */
    private void despacharNotificaciones() {
        while (!notificacionesPendientes.estaVacia()) {
            Notificacion n = notificacionesPendientes.desencolar();
            Usuario u = usuarios.obtener(n.usuarioId());
            if (u != null) u.recibirNotificacion(n);
            notificacionesDespachadas++;
        }
    }

    // ------------------------------------------------------------------ consultas

    /** Usuarios ordenados de más a menos puntos */
    public ListaEnlazada<Usuario> ranking() { return rankingPuntos.mayores(Integer.MAX_VALUE, u -> true); }

    /** Usuarios con puntos entre minimo y maximo */
    public ListaEnlazada<Usuario> usuariosPorPuntos(long minimo, long maximo) {
        return rankingPuntos.rango(ClaveOrden.desde(minimo), ClaveOrden.hasta(maximo));
    }

    public void fijarReloj(LocalDateTime instante) {
        if (instante.isBefore(ahora)) throw new ErrorNegocio("El reloj no puede retroceder.");
        ahora = instante;
    }

    private static ClaveOrden clave(Usuario u) { return new ClaveOrden(u.puntosAcumulados(), u.id()); }

    public static String dinero(long valor) { return "$" + String.format(Locale.ROOT, "%,d", valor).replace(',', '.'); }

    private static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new ErrorNegocio("Falta " + campo + ".");
        return valor.trim();
    }

    private static String correoValido(String correo) {
        String limpio = requerido(correo, "el correo");
        if (!limpio.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ErrorNegocio("El correo «" + limpio + "» no es válido.");
        return limpio;
    }

    private static String telefonoValido(String telefono) {
        if (telefono == null || telefono.isBlank()) return "";
        String limpio = telefono.trim();
        if (!limpio.matches("\\d{7,10}")) throw new ErrorNegocio("El teléfono debe tener entre 7 y 10 dígitos.");
        return limpio;
    }

    public LocalDateTime ahora() { return ahora; }
    public TablaHash<String, Usuario> usuarios() { return usuarios; }
    public TablaHash<String, Billetera> billeteras() { return billeteras; }
    public TablaHash<String, Long> configuracion() { return configuracion; }
    public ArbolAVL<ClaveOrden, Usuario> rankingPuntos() { return rankingPuntos; }
    public ArbolAVL<ClaveOrden, Transaccion> porValor() { return porValor; }
    public ListaEnlazada<Transaccion> transacciones() { return transacciones; }
    public ListaEnlazada<EventoAuditoria> auditoria() { return auditoria; }
    public ColaPrioridad<OperacionProgramada> programadas() { return programadas; }
    public Cola<Notificacion> notificacionesPendientes() { return notificacionesPendientes; }
    public long notificacionesDespachadas() { return notificacionesDespachadas; }
    public Grafo grafoUsuarios() { return grafoUsuarios; }
    public Grafo grafoBilleteras() { return grafoBilleteras; }
}
