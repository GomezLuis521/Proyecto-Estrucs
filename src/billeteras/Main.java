package billeteras;

import billeteras.api.ServidorApi;
import billeteras.servicio.DatosDemo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** Punto de entrada: carga los datos de ejemplo y levanta la consola web. Uso: Main [puerto]. */
public class Main {

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 8085;
        Path web = Path.of("web");
        if (!Files.isRegularFile(web.resolve("index.html"))) {
            System.err.println("No se encontró web/index.html. Ejecuta el programa desde la carpeta del proyecto.");
            System.exit(1);
        }
        new ServidorApi(puerto, web, DatosDemo.crear(LocalDateTime.now().withSecond(0).withNano(0))).iniciar();
        System.out.println("SavingCol lista en http://localhost:" + puerto);
    }
}
