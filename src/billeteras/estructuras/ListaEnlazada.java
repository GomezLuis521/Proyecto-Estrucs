package billeteras.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Lista simplemente enlazada con referencia al último nodo.
 * Insertar al inicio o al final es O(1); buscar y recorrer es O(n).
 * Se usa para historiales, donde lo normal es agregar al final y recorrer en orden.
 */
public class ListaEnlazada<T> implements Iterable<T> {

    private static final class Nodo<T> {
        T dato;
        Nodo<T> siguiente;
        Nodo(T dato) { this.dato = dato; }
    }

    private Nodo<T> cabeza;
    private Nodo<T> cola;
    private int tamano;

    public void agregar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (cola == null) cabeza = nuevo; else cola.siguiente = nuevo;
        cola = nuevo;
        tamano++;
    }

    public void agregarAlInicio(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        nuevo.siguiente = cabeza;
        cabeza = nuevo;
        if (cola == null) cola = nuevo;
        tamano++;
    }

    public T eliminarPrimero() {
        if (cabeza == null) throw new NoSuchElementException("La lista está vacía");
        T dato = cabeza.dato;
        cabeza = cabeza.siguiente;
        if (cabeza == null) cola = null;
        tamano--;
        return dato;
    }

    public T eliminarUltimo() {
        if (cabeza == null) throw new NoSuchElementException("La lista está vacía");
        if (cabeza == cola) return eliminarPrimero();
        Nodo<T> anterior = cabeza;
        while (anterior.siguiente != cola) anterior = anterior.siguiente;
        T dato = cola.dato;
        anterior.siguiente = null;
        cola = anterior;
        tamano--;
        return dato;
    }

    /** Elimina el primer elemento que cumple la condición. */
    public boolean eliminarSi(Predicate<T> condicion) {
        Nodo<T> anterior = null;
        for (Nodo<T> n = cabeza; n != null; anterior = n, n = n.siguiente) {
            if (!condicion.test(n.dato)) continue;
            if (anterior == null) cabeza = n.siguiente; else anterior.siguiente = n.siguiente;
            if (n == cola) cola = anterior;
            tamano--;
            return true;
        }
        return false;
    }

    public T buscar(Predicate<T> condicion) {
        for (T dato : this) if (condicion.test(dato)) return dato;
        return null;
    }

    public ListaEnlazada<T> filtrar(Predicate<T> condicion) {
        ListaEnlazada<T> resultado = new ListaEnlazada<>();
        for (T dato : this) if (condicion.test(dato)) resultado.agregar(dato);
        return resultado;
    }

    public T primero() { return cabeza == null ? null : cabeza.dato; }
    public T ultimo() { return cola == null ? null : cola.dato; }
    public int tamano() { return tamano; }
    public boolean estaVacia() { return tamano == 0; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Nodo<T> actual = cabeza;
            @Override public boolean hasNext() { return actual != null; }
            @Override public T next() {
                if (actual == null) throw new NoSuchElementException();
                T dato = actual.dato;
                actual = actual.siguiente;
                return dato;
            }
        };
    }
}
