package billeteras.servicio;

import billeteras.estructuras.ColaPrioridad;
import billeteras.estructuras.Grafo;
import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.TablaHash;
import billeteras.modelo.Billetera;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Reportes e indicadores de uso. Cada reporte cuenta en una tabla hash (O(1) por movimiento)
 * y luego saca los primeros lugares con una cola de prioridad, sin ordenar todo el conjunto.
 */
public class Analitica {

    public record Conteo(String clave, String etiqueta, long veces, long monto) { }

    private final Plataforma plataforma;

    public Analitica(Plataforma plataforma) { this.plataforma = plataforma; }

    /** Lista con las operaciones exitosas hechas en el período (extremos incluidos). */
    public ListaEnlazada<Transaccion> operacionesEnPeriodo(LocalDateTime desde, LocalDateTime hasta) {
        return plataforma.transacciones().filtrar(t -> t.exitosa() && t.tipo().reversible()
            && !t.fecha().isBefore(desde) && !t.fecha().isAfter(hasta));
    }

    public long montoTotal(ListaEnlazada<Transaccion> operaciones) {
        long total = 0;
        for (Transaccion t : operaciones) total += t.valor();
        return total;
    }

    public ListaEnlazada<Conteo> billeterasMasUsadas(ListaEnlazada<Transaccion> operaciones, int cuantas) {
        TablaHash<String, long[]> conteos = new TablaHash<>();
        for (Transaccion t : operaciones) {
            contar(conteos, t.origen(), t.valor());
            contar(conteos, t.destino(), t.valor());
        }
        return primeros(conteos, cuantas, this::nombreBilletera);
    }

    public ListaEnlazada<Conteo> usuariosConMasTransferencias(ListaEnlazada<Transaccion> operaciones, int cuantos) {
        TablaHash<String, long[]> conteos = new TablaHash<>();
        for (Transaccion t : operaciones)
            if (t.tipo() == TipoTransaccion.TRANSFERENCIA) contar(conteos, t.usuarioId(), t.valor());
        return primeros(conteos, cuantos, this::nombreUsuario);
    }

    /** El primero de la lista es el usuario con mayor actividad en el período. */
    public ListaEnlazada<Conteo> usuariosMasActivos(ListaEnlazada<Transaccion> operaciones, int cuantos) {
        TablaHash<String, long[]> conteos = new TablaHash<>();
        for (Transaccion t : operaciones) contar(conteos, t.usuarioId(), t.valor());
        return primeros(conteos, cuantos, this::nombreUsuario);
    }

    public ListaEnlazada<Conteo> categoriasMasActivas(ListaEnlazada<Transaccion> operaciones) {
        TablaHash<String, long[]> conteos = new TablaHash<>();
        for (Transaccion t : operaciones) {
            contar(conteos, categoria(t.origen()), t.valor());
            contar(conteos, categoria(t.destino()), t.valor());
        }
        return primeros(conteos, Integer.MAX_VALUE, clave -> clave);
    }

    public ListaEnlazada<Conteo> frecuenciaPorTipo(ListaEnlazada<Transaccion> operaciones) {
        TablaHash<String, long[]> conteos = new TablaHash<>();
        for (Transaccion t : operaciones) contar(conteos, t.tipo().etiqueta(), t.valor());
        return primeros(conteos, Integer.MAX_VALUE, clave -> clave);
    }

    /** Transacciones de mayor valor en el período: recorrido inverso del árbol ordenado por valor. */
    public ListaEnlazada<Transaccion> mayoresTransacciones(LocalDateTime desde, LocalDateTime hasta, int cuantas) {
        return plataforma.porValor().mayores(cuantas,
            t -> t.exitosa() && !t.fecha().isBefore(desde) && !t.fecha().isAfter(hasta));
    }

    /** Aristas más usadas de un grafo: las rutas por donde más veces pasó dinero. */
    public ListaEnlazada<Grafo.Arista> rutasFrecuentes(Grafo grafo, int cuantas) {
        ColaPrioridad<Grafo.Arista> orden = new ColaPrioridad<>(
            Comparator.comparingInt(Grafo.Arista::veces).thenComparingLong(Grafo.Arista::monto).reversed());
        for (Grafo.Arista a : grafo.aristas()) orden.insertar(a);
        ListaEnlazada<Grafo.Arista> resultado = new ListaEnlazada<>();
        while (!orden.estaVacia() && resultado.tamano() < cuantas) resultado.agregar(orden.extraerMinimo());
        return resultado;
    }

    private void contar(TablaHash<String, long[]> conteos, String clave, long monto) {
        if (clave == null) return;
        long[] c = conteos.obtener(clave);
        if (c == null) conteos.poner(clave, c = new long[2]);
        c[0]++;
        c[1] += monto;
    }

    private ListaEnlazada<Conteo> primeros(TablaHash<String, long[]> conteos, int cuantos, Function<String, String> etiqueta) {
        ColaPrioridad<Conteo> orden = new ColaPrioridad<>(
            Comparator.comparingLong(Conteo::veces).thenComparingLong(Conteo::monto).reversed().thenComparing(Conteo::clave));
        for (String clave : conteos.claves()) {
            long[] c = conteos.obtener(clave);
            orden.insertar(new Conteo(clave, etiqueta.apply(clave), c[0], c[1]));
        }
        ListaEnlazada<Conteo> resultado = new ListaEnlazada<>();
        while (!orden.estaVacia() && resultado.tamano() < cuantos) resultado.agregar(orden.extraerMinimo());
        return resultado;
    }

    private String categoria(String codigoBilletera) {
        Billetera b = codigoBilletera == null ? null : plataforma.billeteras().obtener(codigoBilletera);
        return b == null ? null : b.tipo().etiqueta();
    }

    private String nombreBilletera(String codigo) {
        Billetera b = plataforma.billeteras().obtener(codigo);
        return b == null ? codigo + " (eliminada)" : b.nombre() + " · " + nombreUsuario(b.propietarioId());
    }

    private String nombreUsuario(String id) {
        Usuario u = plataforma.usuarios().obtener(id);
        return u == null ? id + " (eliminado)" : u.nombre();
    }
}
