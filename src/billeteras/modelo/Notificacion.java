package billeteras.modelo;

import java.time.LocalDateTime;

public record Notificacion(String id, String usuarioId, TipoNotificacion tipo, String mensaje, LocalDateTime fecha) { }
