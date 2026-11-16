package billeteras.estructuras;

import java.util.function.Predicate;

/**
 * Árbol binario de búsqueda autobalanceado (AVL).
 * Insertar, eliminar y buscar son O(log n) porque la altura nunca pasa de ~1,44·log2(n).
 * El recorrido en orden entrega los datos ordenados, y una consulta por rango
 * solo visita las ramas que pueden contener resultados: O(log n + k).
 */
public class ArbolAVL<K extends Comparable<K>, V> {

    private static final class Nodo<K, V> {
        K clave;
        V valor;
        Nodo<K, V> izq, der;
        int altura = 1;
        Nodo(K clave, V valor) { this.clave = clave; this.valor = valor; }
    }

    private Nodo<K, V> raiz;
    private int tamano;

    public void insertar(K clave, V valor) { raiz = insertar(raiz, clave, valor); }

    private Nodo<K, V> insertar(Nodo<K, V> n, K clave, V valor) {
        if (n == null) { tamano++; return new Nodo<>(clave, valor); }
        int c = clave.compareTo(n.clave);
        if (c < 0) n.izq = insertar(n.izq, clave, valor);
        else if (c > 0) n.der = insertar(n.der, clave, valor);
        else { n.valor = valor; return n; }
        return balancear(n);
    }

    public V buscar(K clave) {
        Nodo<K, V> n = raiz;
        while (n != null) {
            int c = clave.compareTo(n.clave);
            if (c == 0) return n.valor;
            n = c < 0 ? n.izq : n.der;
        }
        return null;
    }

    public boolean eliminar(K clave) {
        int antes = tamano;
        raiz = eliminar(raiz, clave);
        return tamano < antes;
    }

    private Nodo<K, V> eliminar(Nodo<K, V> n, K clave) {
        if (n == null) return null;
        int c = clave.compareTo(n.clave);
        if (c < 0) n.izq = eliminar(n.izq, clave);
        else if (c > 0) n.der = eliminar(n.der, clave);
        else {
            if (n.izq == null || n.der == null) {
                tamano--;
                return n.izq != null ? n.izq : n.der;
            }
            // Dos hijos: se copia el sucesor (menor del subárbol derecho) y se elimina allá.
            Nodo<K, V> sucesor = n.der;
            while (sucesor.izq != null) sucesor = sucesor.izq;
            n.clave = sucesor.clave;
            n.valor = sucesor.valor;
            n.der = eliminar(n.der, sucesor.clave);
        }
        return balancear(n);
    }

    private int altura(Nodo<K, V> n) { return n == null ? 0 : n.altura; }

    private void actualizar(Nodo<K, V> n) { n.altura = 1 + Math.max(altura(n.izq), altura(n.der)); }

    private Nodo<K, V> rotarDerecha(Nodo<K, V> y) {
        Nodo<K, V> x = y.izq;
        y.izq = x.der;
        x.der = y;
        actualizar(y);
        actualizar(x);
        return x;
    }

    private Nodo<K, V> rotarIzquierda(Nodo<K, V> x) {
        Nodo<K, V> y = x.der;
        x.der = y.izq;
        y.izq = x;
        actualizar(x);
        actualizar(y);
        return y;
    }

    private Nodo<K, V> balancear(Nodo<K, V> n) {
        actualizar(n);
        int factor = altura(n.izq) - altura(n.der);
        if (factor > 1) {
            if (altura(n.izq.izq) < altura(n.izq.der)) n.izq = rotarIzquierda(n.izq);
            return rotarDerecha(n);
        }
        if (factor < -1) {
            if (altura(n.der.der) < altura(n.der.izq)) n.der = rotarDerecha(n.der);
            return rotarIzquierda(n);
        }
        return n;
    }

    /** Valores con clave entre minimo y maximo (ambos incluidos), de menor a mayor. */
    public ListaEnlazada<V> rango(K minimo, K maximo) {
        ListaEnlazada<V> resultado = new ListaEnlazada<>();
        rango(raiz, minimo, maximo, resultado);
        return resultado;
    }

    private void rango(Nodo<K, V> n, K minimo, K maximo, ListaEnlazada<V> resultado) {
        if (n == null) return;
        if (minimo.compareTo(n.clave) < 0) rango(n.izq, minimo, maximo, resultado);
        if (minimo.compareTo(n.clave) <= 0 && maximo.compareTo(n.clave) >= 0) resultado.agregar(n.valor);
        if (maximo.compareTo(n.clave) > 0) rango(n.der, minimo, maximo, resultado);
    }

    /** Todos los valores de menor a mayor. */
    public ListaEnlazada<V> enOrden() {
        ListaEnlazada<V> resultado = new ListaEnlazada<>();
        enOrden(raiz, resultado);
        return resultado;
    }

    private void enOrden(Nodo<K, V> n, ListaEnlazada<V> resultado) {
        if (n == null) return;
        enOrden(n.izq, resultado);
        resultado.agregar(n.valor);
        enOrden(n.der, resultado);
    }

    /** Los primeros {@code limite} valores de mayor a menor que cumplen el filtro. */
    public ListaEnlazada<V> mayores(int limite, Predicate<V> filtro) {
        ListaEnlazada<V> resultado = new ListaEnlazada<>();
        mayores(raiz, limite, filtro, resultado);
        return resultado;
    }

    private void mayores(Nodo<K, V> n, int limite, Predicate<V> filtro, ListaEnlazada<V> resultado) {
        if (n == null || resultado.tamano() >= limite) return;
        mayores(n.der, limite, filtro, resultado);
        if (resultado.tamano() < limite && filtro.test(n.valor)) resultado.agregar(n.valor);
        mayores(n.izq, limite, filtro, resultado);
    }

    public int tamano() { return tamano; }
    public int altura() { return altura(raiz); }
    public boolean estaVacio() { return tamano == 0; }
}
