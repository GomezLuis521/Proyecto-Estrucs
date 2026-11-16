package billeteras.modelo;

import java.time.LocalDateTime;

/** Registro del historial especial de auditoría: una regla de detección que se disparó. */
public record EventoAuditoria(String id, LocalDateTime fecha, String usuarioId, String transaccionId,
                              String regla, Riesgo riesgo, String detalle) { }
