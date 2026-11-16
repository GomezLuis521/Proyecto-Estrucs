package billeteras.api;

import billeteras.estructuras.ArbolAVL;
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
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import billeteras.servicio.Analitica;
import billeteras.servicio.Plataforma;
import billeteras.servicio.Recompensas;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/*Convierte el estado de la plataforma en mapas listos para serializar a JSON.*/
final class Vistas {

    private final Plataforma p;
    private final Analitica analitica;

    Vistas(Plataforma p) {
        this.p = p;
        this.analitica = new Analitica(p);
    }

    Map<String, Object> estado(String usuarioActivo, LocalDateTime desde, LocalDateTime hasta, Long puntosMin, Long puntosMax) {
        // Árbol auxiliar: entrega los usuarios ordenados por nombre con un recorrido en orden.
        ArbolAVL<String, Usuario> porNombre = new ArbolAVL<>();
        for (Usuario u : p.usuarios().valores()) porNombre.insertar(u.nombre().toLowerCase() + u.id(), u);
        ListaEnlazada<Usuario> ordenados = porNombre.enOrden();

        ListaEnlazada<Object> usuarios = new ListaEnlazada<>();
        ListaEnlazada<Object> billeteras = new ListaEnlazada<>();
        for (Usuario u : ordenados) {
            usuarios.agregar(resumenUsuario(u));
            for (Billetera b : u.billeteras()) billeteras.agregar(billetera(b));
        }
        Usuario activo = p.usuarios().obtener(usuarioActivo);
        if (activo == null) activo = ordenados.primero();

        ListaEnlazada<Object> programadas = new ListaEnlazada<>();
        int turno = 0;
        for (OperacionProgramada op : p.programadas().enOrden()) programadas.agregar(programada(op, ++turno));

        ListaEnlazada<Object> ranking = new ListaEnlazada<>();
        for (Usuario u : p.ranking()) ranking.agregar(resumenUsuario(u));

        ListaEnlazada<Object> niveles = new ListaEnlazada<>();
        for (Nivel n : Nivel.values()) niveles.agregar(nivel(n));

        ListaEnlazada<Object> beneficios = new ListaEnlazada<>();
        for (Beneficio b : Recompensas.CATALOGO) beneficios.agregar(beneficio(b));

        ListaEnlazada<Object> auditoria = new ListaEnlazada<>();
        for (EventoAuditoria e : p.auditoria()) auditoria.agregarAlInicio(mapa(
            "id", e.id(), "fecha", e.fecha(), "usuarioId", e.usuarioId(), "usuario", nombreUsuario(e.usuarioId()),
            "transaccionId", e.transaccionId(), "regla", e.regla(), "riesgo", e.riesgo(), "detalle", e.detalle()));

        Map<String, Object> estado = mapa(
            "reloj", p.ahora(),
            "usuarios", usuarios,
            "billeteras", billeteras,
            "activo", activo == null ? null : detalleUsuario(activo),
            "programadas", programadas,
            "ranking", ranking,
            "niveles", niveles,
            "beneficios", beneficios,
            "auditoria", auditoria,
            "analitica", analitica(desde, hasta),
            "grafo", grafo(),
            "estructuras", estructuras());
        if (puntosMin != null && puntosMax != null) {
            ListaEnlazada<Object> rango = new ListaEnlazada<>();
            for (Usuario u : p.usuariosPorPuntos(puntosMin, puntosMax)) rango.agregar(resumenUsuario(u));
            estado.put("rangoPuntos", rango);
        }
        return estado;
    }

    private Map<String, Object> resumenUsuario(Usuario u) {
        return mapa("id", u.id(), "nombre", u.nombre(), "correo", u.correo(), "telefono", u.telefono(),
            "nivel", u.nivel().etiqueta(), "puntos", u.puntosAcumulados(), "saldoTotal", u.saldoTotal(),
            "billeteras", u.billeteras().tamano(), "movimientos", u.historial().tamano());
    }

    private Map<String, Object> detalleUsuario(Usuario u) {
        Map<String, Object> m = resumenUsuario(u);
        Nivel actual = u.nivel(), siguiente = actual.siguiente();
        m.put("fechaRegistro", u.fechaRegistro());
        m.put("puntosDisponibles", u.puntosDisponibles());
        m.put("nivelDetalle", nivel(actual));
        m.put("siguienteNivel", siguiente == null ? null : siguiente.etiqueta());
        m.put("puntosParaSiguiente", siguiente == null ? 0 : siguiente.desde() - u.puntosAcumulados());
        m.put("transferenciasSinComision", u.transferenciasSinComision());

        ListaEnlazada<Object> billeteras = new ListaEnlazada<>();
        for (Billetera b : u.billeteras()) billeteras.agregar(billetera(b));
        m.put("billeteras", billeteras);

        ListaEnlazada<Object> historial = new ListaEnlazada<>();
        for (Transaccion t : u.historial()) historial.agregarAlInicio(transaccion(t)); // lo más reciente arriba
        m.put("historial", historial);

        ListaEnlazada<Object> pila = new ListaEnlazada<>();
        for (Transaccion t : u.deshacer()) pila.agregar(transaccion(t)); // de la cima hacia el fondo
        m.put("pila", pila);

        ListaEnlazada<Object> notificaciones = new ListaEnlazada<>();
        for (Notificacion n : u.notificaciones()) notificaciones.agregar(mapa(
            "id", n.id(), "tipo", n.tipo(), "mensaje", n.mensaje(), "fecha", n.fecha()));
        m.put("notificaciones", notificaciones);

        ListaEnlazada<Object> canjes = new ListaEnlazada<>();
        for (Canje c : u.canjes()) canjes.agregarAlInicio(mapa(
            "nombre", c.beneficio().nombre(), "costoPuntos", c.beneficio().costoPuntos(), "fecha", c.fecha()));
        m.put("canjes", canjes);
        return m;
    }

    private Map<String, Object> billetera(Billetera b) {
        return mapa("codigo", b.codigo(), "nombre", b.nombre(), "tipo", b.tipo(), "tipoEtiqueta", b.tipo().etiqueta(),
            "saldo", b.saldo(), "activa", b.activa(), "movimientos", b.historial().tamano(),
            "propietarioId", b.propietarioId(), "propietario", nombreUsuario(b.propietarioId()));
    }

    private Map<String, Object> transaccion(Transaccion t) {
        Billetera origen = t.origen() == null ? null : p.billeteras().obtener(t.origen());
        Billetera destino = t.destino() == null ? null : p.billeteras().obtener(t.destino());
        return mapa("id", t.id(), "fecha", t.fecha(), "tipo", t.tipo(), "tipoEtiqueta", t.tipo().etiqueta(),
            "valor", t.valor(), "usuarioId", t.usuarioId(), "usuario", nombreUsuario(t.usuarioId()),
            "origen", t.origen(), "origenNombre", origen == null ? null : origen.nombre(),
            "origenPropietarioId", origen == null ? null : origen.propietarioId(),
            "origenPropietario", origen == null ? null : nombreUsuario(origen.propietarioId()),
            "destino", t.destino(), "destinoNombre", destino == null ? null : destino.nombre(),
            "destinoPropietarioId", destino == null ? null : destino.propietarioId(),
            "destinoPropietario", destino == null ? null : nombreUsuario(destino.propietarioId()),
            "estado", t.estado(), "riesgo", t.riesgo(), "puntos", t.puntos(), "comision", t.comision(),
            "saldoOrigen", t.saldoOrigen(), "saldoDestino", t.saldoDestino(), "motivo", t.motivo(),
            "programada", t.programada(), "referencia", t.referencia());
    }

    private Map<String, Object> programada(OperacionProgramada op, int turno) {
        Billetera origen = op.origen() == null ? null : p.billeteras().obtener(op.origen());
        Billetera destino = op.destino() == null ? null : p.billeteras().obtener(op.destino());
        return mapa("id", op.id(), "turno", turno, "fecha", op.fecha(), "tipo", op.tipo(), "tipoEtiqueta", op.tipo().etiqueta(),
            "valor", op.valor(), "usuarioId", op.usuarioId(), "usuario", nombreUsuario(op.usuarioId()),
            "nivel", Nivel.values()[op.prioridadNivel()].etiqueta(),
            "origen", op.origen(), "origenNombre", origen == null ? null : origen.nombre(),
            "destino", op.destino(), "destinoNombre", destino == null ? null : destino.nombre(),
            "destinoPropietario", destino == null ? null : nombreUsuario(destino.propietarioId()),
            "periodicidad", op.periodicidad(), "periodicidadEtiqueta", op.periodicidad().etiqueta(),
            "descripcion", op.descripcion(), "ejecuciones", op.ejecuciones());
    }

    private Map<String, Object> nivel(Nivel n) {
        Nivel siguiente = n.siguiente();
        return mapa("nombre", n.etiqueta(), "desde", n.desde(), "hasta", siguiente == null ? null : siguiente.desde() - 1,
            "bonoPuntos", n.bonoPuntosPorcentaje() - 100, "comisionPuntosBasicos", n.comisionPuntosBasicos(),
            "limite", n.limitePorOperacion());
    }

    private Map<String, Object> beneficio(Beneficio b) {
        return mapa("codigo", b.codigo(), "nombre", b.nombre(), "descripcion", b.descripcion(), "costoPuntos", b.costoPuntos());
    }

    private Map<String, Object> analitica(LocalDateTime desde, LocalDateTime hasta) {
        ListaEnlazada<Transaccion> operaciones = analitica.operacionesEnPeriodo(desde, hasta);
        ListaEnlazada<Object> mayores = new ListaEnlazada<>();
        for (Transaccion t : analitica.mayoresTransacciones(desde, hasta, 6)) mayores.agregar(transaccion(t));

        ListaEnlazada<Object> ciclos = new ListaEnlazada<>();
        for (ListaEnlazada<String> ciclo : p.grafoUsuarios().ciclos(4, 8)) {
            ListaEnlazada<Object> nombres = new ListaEnlazada<>();
            for (String id : ciclo) nombres.agregar(mapa("id", id, "nombre", nombreUsuario(id)));
            ciclos.agregar(nombres);
        }
        return mapa(
            "totalOperaciones", operaciones.tamano(),
            "montoTotal", analitica.montoTotal(operaciones),
            "billeteras", conteos(analitica.billeterasMasUsadas(operaciones, 6)),
            "transferencias", conteos(analitica.usuariosConMasTransferencias(operaciones, 6)),
            "activos", conteos(analitica.usuariosMasActivos(operaciones, 6)),
            "categorias", conteos(analitica.categoriasMasActivas(operaciones)),
            "tipos", conteos(analitica.frecuenciaPorTipo(operaciones)),
            "mayores", mayores,
            "rutasUsuarios", aristas(analitica.rutasFrecuentes(p.grafoUsuarios(), 6), true),
            "rutasBilleteras", aristas(analitica.rutasFrecuentes(p.grafoBilleteras(), 6), false),
            "ciclos", ciclos);
    }

    private ListaEnlazada<Object> conteos(ListaEnlazada<Analitica.Conteo> conteos) {
        ListaEnlazada<Object> r = new ListaEnlazada<>();
        for (Analitica.Conteo c : conteos) r.agregar(mapa("clave", c.clave(), "etiqueta", c.etiqueta(), "veces", c.veces(), "monto", c.monto()));
        return r;
    }

    private ListaEnlazada<Object> aristas(ListaEnlazada<Grafo.Arista> aristas, boolean entreUsuarios) {
        ListaEnlazada<Object> r = new ListaEnlazada<>();
        for (Grafo.Arista a : aristas) r.agregar(mapa("origen", a.origen(), "destino", a.destino(),
            "origenNombre", entreUsuarios ? nombreUsuario(a.origen()) : nombreBilletera(a.origen()),
            "destinoNombre", entreUsuarios ? nombreUsuario(a.destino()) : nombreBilletera(a.destino()),
            "veces", a.veces(), "monto", a.monto()));
        return r;
    }

    private Map<String, Object> grafo() {
        ListaEnlazada<Object> nodos = new ListaEnlazada<>();
        for (String id : p.grafoUsuarios().vertices()) nodos.agregar(mapa("id", id, "nombre", nombreUsuario(id)));
        return mapa("nodos", nodos, "aristas", aristas(p.grafoUsuarios().aristas(), true));
    }

    private Map<String, Object> estructuras() {
        int enPilas = 0;
        long enHistoriales = 0;
        for (Usuario u : p.usuarios().valores()) {
            enPilas += u.deshacer().tamano();
            enHistoriales += u.historial().tamano();
        }
        return mapa(
            "lista", mapa("transacciones", p.transacciones().tamano(), "enHistoriales", enHistoriales, "auditoria", p.auditoria().tamano()),
            "pila", mapa("elementos", enPilas, "pilas", p.usuarios().tamano()),
            "cola", mapa("pendientes", p.notificacionesPendientes().tamano(), "despachadas", p.notificacionesDespachadas()),
            "colaPrioridad", mapa("elementos", p.programadas().tamano()),
            "arbol", mapa("usuarios", p.rankingPuntos().tamano(), "alturaUsuarios", p.rankingPuntos().altura(),
                "transacciones", p.porValor().tamano(), "alturaTransacciones", p.porValor().altura()),
            "hash", mapa("usuarios", tabla(p.usuarios()), "billeteras", tabla(p.billeteras())),
            "grafo", mapa("verticesUsuarios", p.grafoUsuarios().totalVertices(), "aristasUsuarios", p.grafoUsuarios().totalAristas(),
                "verticesBilleteras", p.grafoBilleteras().totalVertices(), "aristasBilleteras", p.grafoBilleteras().totalAristas()));
    }

    private Map<String, Object> tabla(TablaHash<?, ?> tabla) {
        return mapa("elementos", tabla.tamano(), "cubetas", tabla.capacidad(), "colisiones", tabla.colisiones());
    }

    private String nombreUsuario(String id) {
        Usuario u = p.usuarios().obtener(id);
        return u == null ? id + " (eliminado)" : u.nombre();
    }

    private String nombreBilletera(String codigo) {
        Billetera b = p.billeteras().obtener(codigo);
        return b == null ? codigo + " (eliminada)" : b.nombre() + " · " + nombreUsuario(b.propietarioId());
    }

    static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], pares[i + 1]);
        return m;
    }
}
