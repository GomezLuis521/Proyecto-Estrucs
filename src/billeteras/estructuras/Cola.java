package billeteras.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Cola (FIFO) sobre nodos enlazados: encolar y desencolar son O(1).
 * Se usa para despachar notificaciones en el mismo orden en que se generaron.
 */
public class Cola<T> implements Iterable<T> {

    private final ListaEnlazada<T> elementos = new ListaEnlazada<>();

    public void encolar(T dato) { elementos.agregar(dato); }

    public T desencolar() {
        if (estaVacia()) throw new NoSuchElementException("La cola está vacía");
        return elementos.eliminarPrimero();
    }

    public T frente() { return elementos.primero(); }
    public int tamano() { return elementos.tamano(); }
    public boolean estaVacia() { return elementos.estaVacia(); }

    /** Recorre desde el frente hacia el final. */
    @Override
    public Iterator<T> iterator() { return elementos.iterator(); }
}
