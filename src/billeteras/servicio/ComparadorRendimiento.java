package billeteras.servicio;

import billeteras.estructuras.ArbolAVL;
import billeteras.estructuras.ColaPrioridad;
import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.TablaHash;
import java.util.Comparator;

/**
 * Compara el tiempo real de distintas estructuras haciendo el mismo trabajo:
 * insertar n elementos, buscar 1.000 claves y obtener los 10 mayores.
 */
public final class ComparadorRendimiento {

    public record Medicion(String operacion, String estructura, String complejidad, long nanosegundos) { }

    private static final int BUSQUEDAS = 1_000;
    private static final int MAYORES = 10;

    private ComparadorRendimiento() { }

    public static ListaEnlazada<Medicion> comparar(int n) {
        n = Math.max(1_000, Math.min(n, 100_000));
        medir(2_000); // calentamiento: deja que la JVM compile el código antes de medir
        return medir(n);
    }

    private static ListaEnlazada<Medicion> medir(int n) {
        long[] datos = new long[n];
        long semilla = 20262;
        for (int i = 0; i < n; i++) {
            semilla = semilla * 6364136223846793005L + 1442695040888963407L; // generador congruencial: datos repetibles
            datos[i] = semilla >>> 20;
        }
        ListaEnlazada<Medicion> r = new ListaEnlazada<>();

        ListaEnlazada<Long> lista = new ListaEnlazada<>();
        ArbolAVL<Long, Long> arbol = new ArbolAVL<>();
        TablaHash<Long, Long> tabla = new TablaHash<>();
        ColaPrioridad<Long> monticulo = new ColaPrioridad<Long>(Comparator.reverseOrder());

        String insertar = "Insertar " + n + " elementos";
        long t = System.nanoTime();
        for (long d : datos) lista.agregar(d);
        r.agregar(new Medicion(insertar, "Lista enlazada", "O(1) c/u", nanos(t)));
        t = System.nanoTime();
        for (long d : datos) tabla.poner(d, d);
        r.agregar(new Medicion(insertar, "Tabla hash", "O(1) promedio c/u", nanos(t)));
        t = System.nanoTime();
        for (long d : datos) arbol.insertar(d, d);
        r.agregar(new Medicion(insertar, "Árbol AVL", "O(log n) c/u", nanos(t)));
        t = System.nanoTime();
        for (long d : datos) monticulo.insertar(d);
        r.agregar(new Medicion(insertar, "Cola de prioridad", "O(log n) c/u", nanos(t)));

        String buscar = "Buscar " + BUSQUEDAS + " claves";
        int paso = n / BUSQUEDAS;
        long encontrados = 0;
        t = System.nanoTime();
        for (int i = 0; i < BUSQUEDAS; i++) { long clave = datos[i * paso]; if (lista.buscar(x -> x == clave) != null) encontrados++; }
        r.agregar(new Medicion(buscar, "Lista enlazada", "O(n) c/u", nanos(t)));
        t = System.nanoTime();
        for (int i = 0; i < BUSQUEDAS; i++) if (tabla.obtener(datos[i * paso]) != null) encontrados++;
        r.agregar(new Medicion(buscar, "Tabla hash", "O(1) promedio c/u", nanos(t)));
        t = System.nanoTime();
        for (int i = 0; i < BUSQUEDAS; i++) if (arbol.buscar(datos[i * paso]) != null) encontrados++;
        r.agregar(new Medicion(buscar, "Árbol AVL", "O(log n) c/u", nanos(t)));
        if (encontrados != 3L * BUSQUEDAS) throw new IllegalStateException("Las estructuras no encontraron las mismas claves");

        String mayores = "Obtener los " + MAYORES + " mayores";
        t = System.nanoTime();
        long tope = Long.MAX_VALUE;
        for (int k = 0; k < MAYORES; k++) { // una pasada completa por cada mayor
            long mejor = Long.MIN_VALUE;
            for (long d : lista) if (d < tope && d > mejor) mejor = d;
            tope = mejor;
        }
        r.agregar(new Medicion(mayores, "Lista enlazada", "O(k·n)", nanos(t)));
        t = System.nanoTime();
        arbol.mayores(MAYORES, x -> true);
        r.agregar(new Medicion(mayores, "Árbol AVL", "O(log n + k)", nanos(t)));
        t = System.nanoTime();
        for (int k = 0; k < MAYORES; k++) monticulo.extraerMinimo();
        r.agregar(new Medicion(mayores, "Cola de prioridad", "O(k·log n)", nanos(t)));
        return r;
    }

    private static long nanos(long inicio) { return System.nanoTime() - inicio; }
}
