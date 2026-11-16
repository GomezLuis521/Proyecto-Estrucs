package billeteras.servicio;

import billeteras.modelo.Beneficio;
import billeteras.modelo.Nivel;
import billeteras.modelo.TipoTransaccion;

/** Política de puntos de fidelización y catálogo de beneficios canjeables. */
public final class Recompensas {

    public static final String BONO_50 = "BONO-50";
    public static final String BONO_150 = "BONO-150";
    public static final String SIN_COMISION = "SIN-COMISION";

    public static final Beneficio[] CATALOGO = {
        new Beneficio(SIN_COMISION, "3 transferencias sin comisión",
            "Tus próximas tres transferencias a otros usuarios no pagan comisión.", 150),
        new Beneficio(BONO_50, "Bono de $50",
            "Abona 50 unidades a tu primera billetera activa.", 200),
        new Beneficio(BONO_150, "Bono de $150",
            "Abona 150 unidades a tu primera billetera activa.", 500),
    };

    private Recompensas() { }

    /**
     * Recarga 1, retiro 2 y transferencia 3 puntos por cada 100 unidades,
     * multiplicados por el bono de puntos del nivel del usuario.
     */
    public static long puntos(TipoTransaccion tipo, long valor, Nivel nivel) {
        return (valor / 100) * tipo.puntosPorCien() * nivel.bonoPuntosPorcentaje() / 100;
    }

    public static Beneficio beneficio(String codigo) {
        for (Beneficio b : CATALOGO) if (b.codigo().equals(codigo)) return b;
        throw new ErrorNegocio("No existe el beneficio " + codigo + ".");
    }
}
