package billeteras.estructuras;

/**
 * Clave compuesta para los árboles: ordena por un valor numérico (puntos o monto)
 * y desempata por identificador, así dos elementos con el mismo valor no se pisan.
 */
public record ClaveOrden(long valor, String id) implements Comparable<ClaveOrden> {

    /** Menor clave posible con ese valor: sirve como límite inferior de un rango. */
    public static ClaveOrden desde(long valor) { return new ClaveOrden(valor, ""); }

    /** Mayor clave posible con ese valor: sirve como límite superior de un rango. */
    public static ClaveOrden hasta(long valor) { return new ClaveOrden(valor, "￿"); }

    @Override
    public int compareTo(ClaveOrden otra) {
        int c = Long.compare(valor, otra.valor);
        return c != 0 ? c : id.compareTo(otra.id);
    }
}
