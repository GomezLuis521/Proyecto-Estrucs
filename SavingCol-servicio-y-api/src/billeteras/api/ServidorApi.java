package billeteras.api;

import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.TablaHash;
import billeteras.modelo.OperacionProgramada;
import billeteras.modelo.Periodicidad;
import billeteras.modelo.TipoBilletera;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import billeteras.servicio.ComparadorRendimiento;
import billeteras.servicio.DatosDemo;
import billeteras.servicio.ErrorNegocio;
import billeteras.servicio.Plataforma;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;



public class ServidorApi {

    private final HttpServer servidor;
    private final Path carpetaWeb;
    private Plataforma plataforma;

    public ServidorApi(int puerto, Path carpetaWeb, Plataforma plataforma) throws IOException {
        this.carpetaWeb = carpetaWeb.toAbsolutePath().normalize();
        this.plataforma = plataforma;
        // Solo escucha en esta máquina: es una demostración local, no un servicio público.
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        servidor.createContext("/api/estado", x -> responder(x, () -> Json.de(estado(parametros(x.getRequestURI().getRawQuery())))));
        servidor.createContext("/api/rendimiento", x -> responder(x, () -> Json.de(rendimiento(parametros(x.getRequestURI().getRawQuery())))));
        servidor.createContext("/api/accion/", x -> responder(x, () -> {
            if (!"POST".equals(x.getRequestMethod())) throw new ErrorNegocio("Las acciones se envían por POST.");
            String nombre = x.getRequestURI().getPath().substring("/api/accion/".length());
            String cuerpo = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return Json.de(Vistas.mapa("ok", true, "mensaje", accion(nombre, parametros(cuerpo))));
        }));
        servidor.createContext("/", this::archivo);
    }

    public void iniciar() { servidor.start(); }

    private interface Respuesta { String producir() throws IOException; }

    
    private synchronized void responder(HttpExchange x, Respuesta respuesta) throws IOException {
        int codigo = 200;
        String json;
        try {
            json = respuesta.producir();
        } catch (ErrorNegocio e) {
            codigo = 422;
            json = Json.de(Vistas.mapa("ok", false, "error", e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            codigo = 500;
            json = Json.de(Vistas.mapa("ok", false, "error", "Error interno del servidor: " + e));
        }
        enviar(x, codigo, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private Object estado(TablaHash<String, String> q) {
        LocalDateTime desde = fechaDia(q.obtener("desde"), LocalDateTime.of(2000, 1, 1, 0, 0), false);
        LocalDateTime hasta = fechaDia(q.obtener("hasta"), plataforma.ahora(), true);
        return new Vistas(plataforma).estado(q.obtener("usuario"), desde, hasta, entero(q, "pmin"), entero(q, "pmax"));
    }

    private Object rendimiento(TablaHash<String, String> q) {
        Long n = entero(q, "n");
        ListaEnlazada<Object> filas = new ListaEnlazada<>();
        for (ComparadorRendimiento.Medicion m : ComparadorRendimiento.comparar(n == null ? 20_000 : n.intValue()))
            filas.agregar(Vistas.mapa("operacion", m.operacion(), "estructura", m.estructura(),
                "complejidad", m.complejidad(), "nanosegundos", m.nanosegundos()));
        return filas;
    }

    private String accion(String nombre, TablaHash<String, String> f) {
        Plataforma p = plataforma;
        String usuario = f.obtener("usuario");
        switch (nombre) {
            case "usuario.registrar": {
                Usuario u = p.registrarUsuario(f.obtener("id"), f.obtener("nombre"), f.obtener("correo"), f.obtener("telefono"));
                return "Usuario " + u.nombre() + " registrado.";
            }
            case "usuario.modificar": {
                Usuario u = p.modificarUsuario(f.obtener("id"), f.obtener("nombre"), f.obtener("correo"), f.obtener("telefono"));
                return "Datos de " + u.nombre() + " actualizados.";
            }
            case "usuario.eliminar":
                p.eliminarUsuario(f.obtener("id"));
                return "Usuario " + f.obtener("id") + " eliminado.";
            case "billetera.crear":
                return "Billetera " + p.crearBilletera(usuario, f.obtener("nombre"),
                    opcion(TipoBilletera.class, f.obtener("tipo"), "el tipo de billetera")).codigo() + " creada.";
            case "billetera.renombrar":
                p.renombrarBilletera(f.obtener("codigo"), f.obtener("nombre"));
                return "Billetera " + f.obtener("codigo") + " renombrada.";
            case "billetera.estado": {
                boolean activa = "true".equals(f.obtener("activa"));
                p.cambiarEstadoBilletera(f.obtener("codigo"), activa);
                return "Billetera " + f.obtener("codigo") + (activa ? " activada." : " desactivada.");
            }
            case "billetera.eliminar":
                p.eliminarBilletera(f.obtener("codigo"));
                return "Billetera " + f.obtener("codigo") + " eliminada.";
            case "operar.recargar":
                return hecho(p.recargar(usuario, f.obtener("destino"), valor(f)));
            case "operar.retirar":
                return hecho(p.retirar(usuario, f.obtener("origen"), valor(f)));
            case "operar.transferir":
                return hecho(p.transferir(usuario, f.obtener("origen"), f.obtener("destino"), valor(f)));
            case "operar.revertir": {
                Transaccion r = p.revertirUltima(usuario);
                return "Se deshizo " + r.referencia() + ": se devolvieron " + Plataforma.dinero(r.valor())
                    + " y se descontaron " + (-r.puntos()) + " puntos.";
            }
            case "programada.crear": {
                OperacionProgramada op = p.programar(usuario, opcion(TipoTransaccion.class, f.obtener("tipo"), "el tipo de operación"),
                    f.obtener("origen"), f.obtener("destino"), valor(f), fechaHora(f.obtener("fecha")),
                    opcion(Periodicidad.class, f.obtener("periodicidad"), "la periodicidad"), f.obtener("descripcion"));
                return "Operación " + op.id() + " programada.";
            }
            case "programada.cancelar":
                p.cancelarProgramada(f.obtener("id"));
                return "Operación " + f.obtener("id") + " cancelada.";
            case "reloj.avanzar": {
                Long horas = entero(f, "horas");
                Plataforma.ResultadoAvance r = p.avanzarReloj(horas == null ? 0 : horas);
                return r.ejecutadas() + r.rechazadas() == 0 ? "Reloj adelantado. No había operaciones programadas en ese lapso."
                    : "Reloj adelantado: " + r.ejecutadas() + " programadas ejecutadas y " + r.rechazadas() + " rechazadas.";
            }
            case "beneficio.canjear":
                return "Canjeaste «" + p.canjear(usuario, f.obtener("codigo")).beneficio().nombre() + "».";
            case "demo.reiniciar":
                plataforma = DatosDemo.crear(LocalDateTime.now().withSecond(0).withNano(0));
                return "Datos de ejemplo restaurados.";
            default:
                throw new ErrorNegocio("Acción desconocida: " + nombre);
        }
    }

    private static String hecho(Transaccion t) {
        return t.tipo().etiqueta() + " de " + Plataforma.dinero(t.valor()) + " aplicada (" + t.id() + "). Sumaste "
            + t.puntos() + " puntos" + (t.comision() > 0 ? "; comisión " + Plataforma.dinero(t.comision()) + "." : ".");
    }

    

    private static long valor(TablaHash<String, String> f) {
        Long v = entero(f, "valor");
        if (v == null) throw new ErrorNegocio("Escribe el valor de la operación.");
        return v;
    }

    private static Long entero(TablaHash<String, String> f, String campo) {
        String texto = f.obtener(campo);
        if (texto == null || texto.isBlank()) return null;
        try {
            return Long.parseLong(texto.trim());
        } catch (NumberFormatException e) {
            throw new ErrorNegocio("«" + texto + "» no es un número entero válido.");
        }
    }

    private static LocalDateTime fechaHora(String texto) {
        try {
            return LocalDateTime.parse(texto == null ? "" : texto);
        } catch (DateTimeParseException e) {
            throw new ErrorNegocio("Elige la fecha y la hora de ejecución.");
        }
    }

    private static LocalDateTime fechaDia(String texto, LocalDateTime porDefecto, boolean finDelDia) {
        if (texto == null || texto.isBlank()) return porDefecto;
        try {
            LocalDate dia = LocalDate.parse(texto);
            return finDelDia ? dia.atTime(23, 59, 59) : dia.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new ErrorNegocio("La fecha «" + texto + "» no es válida.");
        }
    }

    private static <E extends Enum<E>> E opcion(Class<E> tipo, String texto, String campo) {
        try {
            return Enum.valueOf(tipo, texto == null ? "" : texto);
        } catch (IllegalArgumentException e) {
            throw new ErrorNegocio("Elige " + campo + ".");
        }
    }

    
    private static TablaHash<String, String> parametros(String crudo) {
        TablaHash<String, String> campos = new TablaHash<>();
        if (crudo == null || crudo.isEmpty()) return campos;
        for (String par : crudo.split("&")) {
            int igual = par.indexOf('=');
            if (igual <= 0) continue;
            campos.poner(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
        }
        return campos;
    }

    private void archivo(HttpExchange x) throws IOException {
        String ruta = x.getRequestURI().getPath();
        Path archivo = carpetaWeb.resolve(ruta.equals("/") ? "index.html" : ruta.substring(1)).normalize();
       
        if (!archivo.startsWith(carpetaWeb) || !Files.isRegularFile(archivo)) {
            enviar(x, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String nombre = archivo.getFileName().toString();
        String tipo = nombre.endsWith(".html") ? "text/html; charset=utf-8"
            : nombre.endsWith(".css") ? "text/css; charset=utf-8"
            : nombre.endsWith(".js") ? "text/javascript; charset=utf-8"
            : nombre.endsWith(".svg") ? "image/svg+xml"
            : "application/octet-stream";
        enviar(x, 200, tipo, Files.readAllBytes(archivo));
    }

    private static void enviar(HttpExchange x, int codigo, String tipo, byte[] cuerpo) throws IOException {
        x.getResponseHeaders().set("Content-Type", tipo);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(codigo, cuerpo.length);
        try (var salida = x.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
