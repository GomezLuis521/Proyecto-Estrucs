package billeteras.api;

import java.util.Map;


final class Json {

    private Json() { }

    static String de(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                texto(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escribir(sb, e.getValue());
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> elementos) {
            sb.append('[');
            boolean primero = true;
            for (Object elemento : elementos) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(sb, elemento);
            }
            sb.append(']');
        } else if (valor instanceof Enum<?> e) {
            texto(sb, e.name());
        } else {
            texto(sb, valor.toString()); 
        }
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
