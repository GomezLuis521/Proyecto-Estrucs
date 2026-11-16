package billeteras.servicio;

import billeteras.estructuras.ListaEnlazada;
import billeteras.modelo.Billetera;
import billeteras.modelo.Periodicidad;
import billeteras.modelo.TipoBilletera;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Usuario;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Datos de ejemplo para la sustentación: ocho usuarios ficticios y un mes de movimientos
 * simulados, con casos preparados de reversión, canje y comportamiento inusual.
 * Usa las mismas operaciones públicas de la plataforma, así que todo pasa por las validaciones.
 */
public final class DatosDemo {

    private static final String[][] PERSONAS = {
        {"1094900101", "Valentina Ospina Marín", "valentina.ospina@correo.example", "3105550101"},
        {"1094900202", "Santiago Cardona Ríos", "santiago.cardona@correo.example", "3105550202"},
        {"1094900303", "Mariana Giraldo Henao", "mariana.giraldo@correo.example", "3105550303"},
        {"1094900404", "Juan Esteban Londoño", "juan.londono@correo.example", "3105550404"},
        {"1094900505", "Laura Sofía Arias", "laura.arias@correo.example", "3105550505"},
        {"1094900606", "Andrés Felipe Quintero", "andres.quintero@correo.example", "3105550606"},
        {"1094900707", "Camila Buitrago Salazar", "camila.buitrago@correo.example", "3105550707"},
    };

    private static final Object[][][] BILLETERAS = {
        {{"Día a día", TipoBilletera.GASTOS_DIARIOS}, {"Ahorro casa", TipoBilletera.AHORRO}, {"Mercado", TipoBilletera.COMPRAS}, {"Fondo de inversión", TipoBilletera.INVERSION}},
        {{"Gastos", TipoBilletera.GASTOS_DIARIOS}, {"Ahorro viaje", TipoBilletera.AHORRO}, {"Bus y metro", TipoBilletera.TRANSPORTE}},
        {{"Diario", TipoBilletera.GASTOS_DIARIOS}, {"Compras", TipoBilletera.COMPRAS}, {"Ahorro", TipoBilletera.AHORRO}},
        {{"Efectivo", TipoBilletera.GASTOS_DIARIOS}, {"Compras en línea", TipoBilletera.COMPRAS}, {"Transporte", TipoBilletera.TRANSPORTE}, {"Ahorro moto", TipoBilletera.AHORRO}},
        {{"Diario", TipoBilletera.GASTOS_DIARIOS}, {"Transporte", TipoBilletera.TRANSPORTE}, {"Ahorro universidad", TipoBilletera.AHORRO}},
        {{"Gastos", TipoBilletera.GASTOS_DIARIOS}, {"Inversión", TipoBilletera.INVERSION}},
        {{"Diario", TipoBilletera.GASTOS_DIARIOS}, {"Compras", TipoBilletera.COMPRAS}, {"Ahorro", TipoBilletera.AHORRO}},
    };

    /** Probabilidad de operar en cada hora del día y monto máximo, por usuario: define qué nivel alcanza. */
    private static final double[] ACTIVIDAD = {0.36, 0.17, 0.10, 0.14, 0.06, 0.12, 0.09};
    private static final int[] MONTO_MAXIMO = {4900, 2500, 1500, 2000, 1200, 1800, 1500};

    private DatosDemo() { }

    public static Plataforma crear(LocalDateTime fin) {
        LocalDateTime inicio = fin.minusDays(30).withHour(6).withMinute(0);
        Plataforma p = new Plataforma(inicio);
        Random azar = new Random(2026);

        for (int i = 0; i < PERSONAS.length; i++) {
            String[] d = PERSONAS[i];
            p.registrarUsuario(d[0], d[1], d[2], d[3]);
            for (Object[] b : BILLETERAS[i]) p.crearBilletera(d[0], (String) b[0], (TipoBilletera) b[1]);
        }

        // Un mes de actividad normal, de 7:00 a 21:00, hasta dos días antes del cierre.
        LocalDateTime corte = fin.minusDays(2);
        for (LocalDateTime hora = inicio.plusHours(1); hora.isBefore(corte); hora = hora.plusHours(1)) {
            if (hora.getHour() < 7 || hora.getHour() > 21) continue;
            for (int i = 0; i < PERSONAS.length; i++) {
                if (azar.nextDouble() >= ACTIVIDAD[i]) continue;
                p.fijarReloj(hora.plusMinutes(i * 8L + azar.nextInt(7)));
                operacionAlAzar(p, azar, i);
            }
        }

        casosPreparados(p, fin);
        p.fijarReloj(fin);
        programadas(p, fin);
        return p;
    }

    private static void operacionAlAzar(Plataforma p, Random azar, int i) {
        String id = PERSONAS[i][0];
        Usuario u = p.usuario(id);
        long monto = 50L * (2 + azar.nextInt(MONTO_MAXIMO[i] / 50 - 1));
        Billetera cualquiera = billetera(u, azar.nextInt(u.billeteras().tamano()));
        Billetera masLlena = cualquiera;
        for (Billetera b : u.billeteras()) if (b.saldo() > masLlena.saldo()) masLlena = b;
        double dado = azar.nextDouble();
        try {
            if (masLlena.saldo() < 400 || dado < 0.30) {
                p.recargar(id, cualquiera.codigo(), monto);
            } else if (dado < 0.45) {
                p.retirar(id, masLlena.codigo(), Math.min(monto, masLlena.saldo() / 2));
            } else if (dado < 0.63 && cualquiera != masLlena) {
                p.transferir(id, masLlena.codigo(), cualquiera.codigo(), Math.min(monto, masLlena.saldo() / 2));
            } else {
                // Cada quien le envía sobre todo a su contacto siguiente: así aparecen rutas y ciclos en el grafo.
                double contacto = azar.nextDouble();
                int receptor = contacto < 0.5 ? (i + 1) % PERSONAS.length
                    : contacto < 0.75 ? (i + 3) % PERSONAS.length
                    : (i + 1 + azar.nextInt(PERSONAS.length - 1)) % PERSONAS.length;
                Usuario otro = p.usuario(PERSONAS[receptor][0]);
                p.transferir(id, masLlena.codigo(), billetera(otro, 0).codigo(), Math.min(monto, masLlena.saldo() / 2));
            }
        } catch (ErrorNegocio rechazo) {
            // Un rechazo ocasional (por ejemplo, superar el límite del nivel) es parte del historial de ejemplo.
        }
    }

    private static void casosPreparados(Plataforma p, LocalDateTime fin) {
        String andres = PERSONAS[5][0], camila = PERSONAS[6][0], juan = PERSONAS[3][0], laura = PERSONAS[4][0];
        String mariana = PERSONAS[2][0], santiago = PERSONAS[1][0], valentina = PERSONAS[0][0];

        // Ráfaga y destino repetido: cinco transferencias al mismo destino en seis minutos.
        LocalDateTime t = fin.minusHours(40);
        p.fijarReloj(t);
        p.recargar(andres, codigo(p, andres, 0), 2000);
        for (int k = 1; k <= 5; k++) {
            p.fijarReloj(t.plusMinutes(k));
            p.transferir(andres, codigo(p, andres, 0), codigo(p, camila, 0), 300);
        }

        // Monto inusualmente alto frente al promedio del usuario.
        t = fin.minusHours(36);
        p.fijarReloj(t);
        p.recargar(camila, codigo(p, camila, 2), 4800);
        p.fijarReloj(t.plusMinutes(20));
        p.retirar(camila, codigo(p, camila, 2), 4700);

        // Fragmentación: envíos al mismo usuario desde tres billeteras propias en ocho minutos.
        t = fin.minusHours(32);
        for (int k = 0; k < 3; k++) {
            p.fijarReloj(t.plusMinutes(k));
            p.recargar(juan, codigo(p, juan, k), 700);
        }
        for (int k = 0; k < 3; k++) {
            p.fijarReloj(t.plusMinutes(10 + k * 4L));
            p.transferir(juan, codigo(p, juan, k), codigo(p, laura, 0), 650);
        }

        // Reversión de la última operación y canje de beneficios.
        t = fin.minusHours(28);
        p.fijarReloj(t);
        p.recargar(santiago, codigo(p, santiago, 0), 900);
        p.fijarReloj(t.plusMinutes(3));
        p.transferir(santiago, codigo(p, santiago, 0), codigo(p, mariana, 0), 800);
        p.fijarReloj(t.plusMinutes(9));
        p.revertirUltima(santiago);
        p.fijarReloj(t.plusMinutes(30));
        p.canjear(valentina, Recompensas.SIN_COMISION);
        p.canjear(valentina, Recompensas.BONO_150);

        // Horario no habitual: una transferencia de madrugada.
        LocalDateTime madrugada = fin.withHour(3).withMinute(20);
        if (!madrugada.isBefore(fin.minusMinutes(1))) madrugada = madrugada.minusDays(1);
        p.fijarReloj(madrugada.minusMinutes(5));
        p.recargar(mariana, codigo(p, mariana, 0), 400);
        p.fijarReloj(madrugada);
        p.transferir(mariana, codigo(p, mariana, 0), codigo(p, andres, 0), 350);

        // Un usuario recién registrado, todavía en Bronce.
        String daniel = "1094900808";
        p.registrarUsuario(daniel, "Daniel Montoya Zapata", "daniel.montoya@correo.example", "3105550808");
        p.crearBilletera(daniel, "Gastos diarios", TipoBilletera.GASTOS_DIARIOS);
        p.crearBilletera(daniel, "Transporte", TipoBilletera.TRANSPORTE);
        p.recargar(daniel, codigo(p, daniel, 0), 600);
        p.transferir(daniel, codigo(p, daniel, 0), codigo(p, daniel, 1), 150);
    }

    private static void programadas(Plataforma p, LocalDateTime fin) {
        String valentina = PERSONAS[0][0], santiago = PERSONAS[1][0], mariana = PERSONAS[2][0], juan = PERSONAS[3][0];
        String laura = PERSONAS[4][0], camila = PERSONAS[6][0], daniel = "1094900808";
        LocalDateTime manana = fin.plusDays(1).withHour(6).withMinute(0);

        p.programar(santiago, TipoTransaccion.RETIRO, codigo(p, santiago, 0), null, 45,
            fin.plusHours(5), Periodicidad.MENSUAL, "Suscripción de streaming");
        p.programar(mariana, TipoTransaccion.TRANSFERENCIA, codigo(p, mariana, 0), codigo(p, camila, 0), 250,
            fin.plusHours(9), Periodicidad.UNICA, "Abono a Camila");
        p.programar(laura, TipoTransaccion.RECARGA, null, codigo(p, laura, 1), 80,
            manana, Periodicidad.DIARIA, "Recarga automática de transporte");
        p.programar(valentina, TipoTransaccion.TRANSFERENCIA, codigo(p, valentina, 0), codigo(p, valentina, 1), 300,
            manana.plusDays(1).withHour(8), Periodicidad.SEMANAL, "Ahorro automático semanal");
        p.programar(daniel, TipoTransaccion.RETIRO, codigo(p, daniel, 0), null, 4500,
            manana.plusDays(2).withHour(9), Periodicidad.UNICA, "Pago de arriendo");
        p.programar(juan, TipoTransaccion.TRANSFERENCIA, codigo(p, juan, 0), codigo(p, juan, 3), 150,
            manana.plusDays(3).withHour(8), Periodicidad.SEMANAL, "Ahorro para la moto");
    }

    private static String codigo(Plataforma p, String usuarioId, int posicion) {
        return billetera(p.usuario(usuarioId), posicion).codigo();
    }

    private static Billetera billetera(Usuario u, int posicion) {
        ListaEnlazada<Billetera> billeteras = u.billeteras();
        int i = 0;
        for (Billetera b : billeteras) if (i++ == posicion) return b;
        throw new IllegalArgumentException("El usuario " + u.id() + " no tiene billetera en la posición " + posicion);
    }
}
