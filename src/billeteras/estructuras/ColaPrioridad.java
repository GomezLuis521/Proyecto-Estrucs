package billeteras.estructuras;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Cola de prioridad sobre un montículo binario mínimo guardado en un arreglo.
 * Insertar y extraer el menor son O(log n); ver el menor es O(1).
 * Se usa para las operaciones programadas: siempre sale primero la de fecha más cercana.
 */
public class ColaPrioridad<T> {

    private Object[] monticulo = new Object[16];
    private int tamano;
    private final Comparator<T> orden;

    public ColaPrioridad(Comparator<T> orden) { this.orden = orden; }

    public void insertar(T dato) {
        if (tamano == monticulo.length) {
            Object[] mayor = new Object[tamano * 2];
            System.arraycopy(monticulo, 0, mayor, 0, tamano);
            monticulo = mayor;
        }
        monticulo[tamano] = dato;
        subir(tamano++);
    }

    public T verMinimo() { return tamano == 0 ? null : en(0); }

    public T extraerMinimo() {
        if (tamano == 0) throw new NoSuchElementException("La cola de prioridad está vacía");
        T minimo = en(0);
        monticulo[0] = monticulo[--tamano];
        monticulo[tamano] = null;
        bajar(0);
        return minimo;
    }

    /** Quita los elementos que cumplen la condición y reconstruye el montículo en O(n). */
    public int eliminarSi(Predicate<T> condicion) {
        int conservados = 0;
        for (int i = 0; i < tamano; i++) {
            if (!condicion.test(en(i))) monticulo[conservados++] = monticulo[i];
        }
        int eliminados = tamano - conservados;
        for (int i = conservados; i < tamano; i++) monticulo[i] = null;
        tamano = conservados;
        for (int i = tamano / 2 - 1; i >= 0; i--) bajar(i);
        return eliminados;
    }

    /** Copia ordenada de menor a mayor, sin modificar la cola. */
    public ListaEnlazada<T> enOrden() {
        ColaPrioridad<T> copia = new ColaPrioridad<>(orden);
        copia.monticulo = monticulo.clone();
        copia.tamano = tamano;
        ListaEnlazada<T> resultado = new ListaEnlazada<>();
        while (!copia.estaVacia()) resultado.agregar(copia.extraerMinimo());
        return resultado;
    }

    public int tamano() { return tamano; }
    public boolean estaVacia() { return tamano == 0; }

    @SuppressWarnings("unchecked")
    private T en(int i) { return (T) monticulo[i]; }

    private void subir(int i) {
        while (i > 0) {
            int padre = (i - 1) / 2;
            if (orden.compare(en(i), en(padre)) >= 0) return;
            intercambiar(i, padre);
            i = padre;
        }
    }

    private void bajar(int i) {
        while (true) {
            int izq = 2 * i + 1, der = izq + 1, menor = i;
            if (izq < tamano && orden.compare(en(izq), en(menor)) < 0) menor = izq;
            if (der < tamano && orden.compare(en(der), en(menor)) < 0) menor = der;
            if (menor == i) return;
            intercambiar(i, menor);
            i = menor;
        }
    }

    private void intercambiar(int a, int b) {
        Object temporal = monticulo[a];
        monticulo[a] = monticulo[b];
        monticulo[b] = temporal;
    }
}
