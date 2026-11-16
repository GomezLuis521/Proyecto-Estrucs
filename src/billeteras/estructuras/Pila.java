package billeteras.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Pila (LIFO) sobre nodos enlazados: apilar, desapilar y ver la cima son O(1).
 * Se usa para deshacer: la última operación hecha es la primera que se puede revertir.
 */
public class Pila<T> implements Iterable<T> {

    private final ListaEnlazada<T> elementos = new ListaEnlazada<>();

    public void apilar(T dato) { elementos.agregarAlInicio(dato); }

    public T desapilar() {
        if (estaVacia()) throw new NoSuchElementException("La pila está vacía");
        return elementos.eliminarPrimero();
    }

    public T cima() { return elementos.primero(); }
    public int tamano() { return elementos.tamano(); }
    public boolean estaVacia() { return elementos.estaVacia(); }

    /** Recorre desde la cima hacia el fondo. */
    @Override
    public Iterator<T> iterator() { return elementos.iterator(); }
}
