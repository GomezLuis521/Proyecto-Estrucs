package billeteras.estructuras;

/**
 * Tabla hash con encadenamiento separado y redimensionamiento al 75 % de carga.
 * Poner, obtener y eliminar son O(1) en promedio.
 * Se usa para llegar a un usuario por su identificación o a una billetera por su código
 * sin recorrer todo el conjunto.
 */
public class TablaHash<K, V> {

    private static final class Entrada<K, V> {
        final K clave;
        V valor;
        Entrada<K, V> siguiente;
        Entrada(K clave, V valor, Entrada<K, V> siguiente) {
            this.clave = clave;
            this.valor = valor;
            this.siguiente = siguiente;
        }
    }

    private Entrada<K, V>[] cubetas = crear(16);
    private int tamano;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <K, V> Entrada<K, V>[] crear(int capacidad) { return new Entrada[capacidad]; }

    private int indice(Object clave, int capacidad) {
        int h = clave.hashCode();
        h ^= (h >>> 16); // mezcla los bits altos para repartir mejor las claves parecidas
        return (h & 0x7fffffff) % capacidad;
    }

    public void poner(K clave, V valor) {
        int i = indice(clave, cubetas.length);
        for (Entrada<K, V> e = cubetas[i]; e != null; e = e.siguiente) {
            if (e.clave.equals(clave)) { e.valor = valor; return; }
        }
        cubetas[i] = new Entrada<>(clave, valor, cubetas[i]);
        if (++tamano > cubetas.length * 3 / 4) redimensionar();
    }

    public V obtener(K clave) {
        if (clave == null) return null;
        for (Entrada<K, V> e = cubetas[indice(clave, cubetas.length)]; e != null; e = e.siguiente) {
            if (e.clave.equals(clave)) return e.valor;
        }
        return null;
    }

    public boolean contiene(K clave) { return obtener(clave) != null; }

    public V eliminar(K clave) {
        if (clave == null) return null;
        int i = indice(clave, cubetas.length);
        Entrada<K, V> anterior = null;
        for (Entrada<K, V> e = cubetas[i]; e != null; anterior = e, e = e.siguiente) {
            if (!e.clave.equals(clave)) continue;
            if (anterior == null) cubetas[i] = e.siguiente; else anterior.siguiente = e.siguiente;
            tamano--;
            return e.valor;
        }
        return null;
    }

    private void redimensionar() {
        Entrada<K, V>[] nuevas = crear(cubetas.length * 2);
        for (Entrada<K, V> cubeta : cubetas) {
            for (Entrada<K, V> e = cubeta; e != null; ) {
                Entrada<K, V> siguiente = e.siguiente;
                int i = indice(e.clave, nuevas.length);
                e.siguiente = nuevas[i];
                nuevas[i] = e;
                e = siguiente;
            }
        }
        cubetas = nuevas;
    }

    public ListaEnlazada<K> claves() {
        ListaEnlazada<K> resultado = new ListaEnlazada<>();
        for (Entrada<K, V> cubeta : cubetas)
            for (Entrada<K, V> e = cubeta; e != null; e = e.siguiente) resultado.agregar(e.clave);
        return resultado;
    }

    public ListaEnlazada<V> valores() {
        ListaEnlazada<V> resultado = new ListaEnlazada<>();
        for (Entrada<K, V> cubeta : cubetas)
            for (Entrada<K, V> e = cubeta; e != null; e = e.siguiente) resultado.agregar(e.valor);
        return resultado;
    }

    public int tamano() { return tamano; }
    public int capacidad() { return cubetas.length; }

    /** Cantidad de entradas que comparten cubeta con otra anterior. */
    public int colisiones() {
        int ocupadas = 0;
        for (Entrada<K, V> cubeta : cubetas) if (cubeta != null) ocupadas++;
        return tamano - ocupadas;
    }
}
