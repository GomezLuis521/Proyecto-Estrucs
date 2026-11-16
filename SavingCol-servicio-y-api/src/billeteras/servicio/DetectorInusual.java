package billeteras.servicio;

import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.TablaHash;
import billeteras.modelo.Billetera;
import billeteras.modelo.EstadoTransaccion;
import billeteras.modelo.Riesgo;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import java.time.LocalDateTime;

/**
 * Detección de comportamiento financiero inusual.
 * Compara cada salida de dinero (retiro o transferencia) con el historial del usuario
 * en una sola pasada por su lista de movimientos: O(n).
 */
public class DetectorInusual {

    public record Hallazgo(String regla, Riesgo riesgo, String detalle) { }

    public ListaEnlazada<Hallazgo> evaluar(Transaccion t, Usuario u, TablaHash<String, Billetera> billeteras) {
        ListaEnlazada<Hallazgo> hallazgos = new ListaEnlazada<>();
        if (t.tipo() != TipoTransaccion.RETIRO && t.tipo() != TipoTransaccion.TRANSFERENCIA) return hallazgos;

        LocalDateTime cuando = t.fecha();
        int rafaga = 0, mismoDestino = 0, salidasPrevias = 0, ultimas24h = 0, semanaPrevia = 0;
        int previas = 0, previasDeMadrugada = 0;
        long sumaSalidasPrevias = 0;
        ListaEnlazada<String> origenesRecientes = new ListaEnlazada<>();

        for (Transaccion x : u.historial()) {
            if (!x.usuarioId().equals(u.id()) || x.estado() == EstadoTransaccion.RECHAZADA || !x.tipo().reversible()) continue;
            boolean esTransferencia = x.tipo() == TipoTransaccion.TRANSFERENCIA;

            if (esTransferencia && !x.fecha().isBefore(cuando.minusMinutes(10))) rafaga++;
            if (esTransferencia && x.destino().equals(t.destino()) && !x.fecha().isBefore(cuando.minusMinutes(30))) mismoDestino++;
            if (esTransferencia && !x.fecha().isBefore(cuando.minusMinutes(15)) && esHaciaOtroUsuario(x, u, billeteras)
                    && origenesRecientes.buscar(x.origen()::equals) == null) origenesRecientes.agregar(x.origen());
            if (!x.fecha().isBefore(cuando.minusHours(24))) ultimas24h++;
            else if (!x.fecha().isBefore(cuando.minusDays(8))) semanaPrevia++;

            if (x == t) continue; // lo que sigue es la línea base: solo cuenta lo anterior
            previas++;
            if (x.fecha().getHour() < 5) previasDeMadrugada++;
            if (x.tipo() != TipoTransaccion.RECARGA) { salidasPrevias++; sumaSalidasPrevias += x.valor(); }
        }

        if (rafaga >= 4) hallazgos.agregar(new Hallazgo("Ráfaga de transferencias", rafaga >= 6 ? Riesgo.ALTO : Riesgo.MEDIO,
            rafaga + " transferencias en menos de 10 minutos."));

        if (salidasPrevias >= 5) {
            long promedio = sumaSalidasPrevias / salidasPrevias;
            if (promedio > 0 && t.valor() >= 3 * promedio) hallazgos.agregar(new Hallazgo("Monto inusualmente alto",
                t.valor() >= 6 * promedio ? Riesgo.ALTO : Riesgo.MEDIO,
                "Salida de " + Plataforma.dinero(t.valor()) + " frente a un promedio de " + Plataforma.dinero(promedio) + "."));
        }

        if (mismoDestino >= 3) hallazgos.agregar(new Hallazgo("Destino repetido", Riesgo.MEDIO,
            mismoDestino + " transferencias a " + t.destino() + " en menos de 30 minutos."));

        if (origenesRecientes.tamano() >= 3) hallazgos.agregar(new Hallazgo("Fragmentación de montos", Riesgo.ALTO,
            "Envíos a otros usuarios desde " + origenesRecientes.tamano() + " billeteras propias en menos de 15 minutos."));

        int umbralFrecuencia = Math.max(8, 3 * semanaPrevia / 7 + 1);
        if (ultimas24h == umbralFrecuencia && u.fechaRegistro().isBefore(cuando.minusDays(2)))
            hallazgos.agregar(new Hallazgo("Cambio brusco de frecuencia", Riesgo.MEDIO,
                ultimas24h + " operaciones en 24 horas; en la semana anterior hizo " + semanaPrevia + "."));

        if (cuando.getHour() < 5 && previas >= 10 && previasDeMadrugada * 20 <= previas)
            hallazgos.agregar(new Hallazgo("Horario no habitual", Riesgo.BAJO,
                "Operación a las " + String.format("%02d:%02d", cuando.getHour(), cuando.getMinute())
                    + "; solo " + previasDeMadrugada + " de sus " + previas + " operaciones anteriores fueron de madrugada."));

        return hallazgos;
    }

    private boolean esHaciaOtroUsuario(Transaccion x, Usuario u, TablaHash<String, Billetera> billeteras) {
        Billetera destino = billeteras.obtener(x.destino());
        return destino != null && !destino.propietarioId().equals(u.id());
    }
}
