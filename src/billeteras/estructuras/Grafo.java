package billeteras.estructuras;

/**
 * Grafo dirigido y ponderado con listas de adyacencia guardadas en tablas hash.
 * Cada arista acumula cuántas veces se usó y cuánto dinero pasó por ella.
 * Agregar una arista es O(1) promedio; recorrer (BFS/DFS) es O(V + A).
 * Se usa para ver quién le transfiere a quién y encontrar rutas y ciclos de dinero.
 */
public class Grafo {

    public static final class Arista {
        private final String origen;
        private final String destino;
        private int veces;
        private long monto;

        Arista(String origen, String destino) { this.origen = origen; this.destino = destino; }

        public String origen() { return origen; }
        public String destino() { return destino; }
        public int veces() { return veces; }
        public long monto() { return monto; }
    }

    private final TablaHash<String, TablaHash<String, Arista>> adyacencia = new TablaHash<>();
    private int totalAristas;

    public void agregarVertice(String v) {
        if (!adyacencia.contiene(v)) adyacencia.poner(v, new TablaHash<>());
    }

    public void agregarArista(String origen, String destino, long monto) {
        agregarVertice(origen);
        agregarVertice(destino);
        TablaHash<String, Arista> salientes = adyacencia.obtener(origen);
        Arista a = salientes.obtener(destino);
        if (a == null) {
            a = new Arista(origen, destino);
            salientes.poner(destino, a);
            totalAristas++;
        }
        a.veces++;
        a.monto += monto;
    }

    /** Descuenta un uso de la arista (al revertir una transferencia) y la borra si queda en cero. */
    public void quitarArista(String origen, String destino, long monto) {
        TablaHash<String, Arista> salientes = adyacencia.obtener(origen);
        Arista a = salientes == null ? null : salientes.obtener(destino);
        if (a == null) return;
        a.veces--;
        a.monto -= monto;
        if (a.veces <= 0) {
            salientes.eliminar(destino);
            totalAristas--;
        }
    }

    public void eliminarVertice(String v) {
        TablaHash<String, Arista> salientes = adyacencia.eliminar(v);
        if (salientes == null) return;
        totalAristas -= salientes.tamano();
        for (TablaHash<String, Arista> otras : adyacencia.valores()) {
            if (otras.eliminar(v) != null) totalAristas--;
        }
    }

    public ListaEnlazada<String> vertices() { return adyacencia.claves(); }

    public ListaEnlazada<Arista> vecinos(String v) {
        TablaHash<String, Arista> salientes = adyacencia.obtener(v);
        return salientes == null ? new ListaEnlazada<>() : salientes.valores();
    }

    public ListaEnlazada<Arista> aristas() {
        ListaEnlazada<Arista> todas = new ListaEnlazada<>();
        for (TablaHash<String, Arista> salientes : adyacencia.valores())
            for (Arista a : salientes.valores()) todas.agregar(a);
        return todas;
    }

    public int totalVertices() { return adyacencia.tamano(); }
    public int totalAristas() { return totalAristas; }

    /** Camino con menos saltos entre dos vértices (BFS), o lista vacía si no hay. */
    public ListaEnlazada<String> rutaMasCorta(String origen, String destino) {
        ListaEnlazada<String> ruta = new ListaEnlazada<>();
        if (!adyacencia.contiene(origen) || !adyacencia.contiene(destino)) return ruta;
        TablaHash<String, String> anterior = new TablaHash<>();
        Cola<String> porVisitar = new Cola<>();
        porVisitar.encolar(origen);
        anterior.poner(origen, origen);
        while (!porVisitar.estaVacia() && !anterior.contiene(destino)) {
            String actual = porVisitar.desencolar();
            for (Arista a : vecinos(actual)) {
                if (anterior.contiene(a.destino)) continue;
                anterior.poner(a.destino, actual);
                porVisitar.encolar(a.destino);
            }
        }
        if (!anterior.contiene(destino)) return ruta;
        for (String v = destino; !v.equals(origen); v = anterior.obtener(v)) ruta.agregarAlInicio(v);
        ruta.agregarAlInicio(origen);
        return ruta;
    }

    /**
     * Ciclos simples de hasta {@code longitudMaxima} vértices (DFS con retroceso).
     * Cada ciclo se reporta una sola vez, empezando por su vértice menor.
     */
    public ListaEnlazada<ListaEnlazada<String>> ciclos(int longitudMaxima, int limite) {
        ListaEnlazada<ListaEnlazada<String>> encontrados = new ListaEnlazada<>();
        String[] camino = new String[longitudMaxima];
        for (String inicio : vertices()) {
            camino[0] = inicio;
            buscarCiclos(inicio, 1, camino, encontrados, limite);
        }
        return encontrados;
    }

    private void buscarCiclos(String inicio, int largo, String[] camino,
                              ListaEnlazada<ListaEnlazada<String>> encontrados, int limite) {
        for (Arista a : vecinos(camino[largo - 1])) {
            if (encontrados.tamano() >= limite) return;
            String siguiente = a.destino;
            if (siguiente.equals(inicio)) {
                if (largo < 2) continue; // una billetera no se transfiere a sí misma
                ListaEnlazada<String> ciclo = new ListaEnlazada<>();
                for (int i = 0; i < largo; i++) ciclo.agregar(camino[i]);
                encontrados.agregar(ciclo);
                continue;
            }
            // Solo se avanza hacia vértices mayores que el inicio: evita repetir el mismo ciclo rotado.
            if (largo == camino.length || siguiente.compareTo(inicio) < 0 || enCamino(camino, largo, siguiente)) continue;
            camino[largo] = siguiente;
            buscarCiclos(inicio, largo + 1, camino, encontrados, limite);
        }
    }

    private boolean enCamino(String[] camino, int largo, String v) {
        for (int i = 0; i < largo; i++) if (camino[i].equals(v)) return true;
        return false;
    }
}
