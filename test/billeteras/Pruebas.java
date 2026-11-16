package billeteras;

import billeteras.estructuras.ArbolAVL;
import billeteras.estructuras.Cola;
import billeteras.estructuras.ColaPrioridad;
import billeteras.estructuras.Grafo;
import billeteras.estructuras.ListaEnlazada;
import billeteras.estructuras.Pila;
import billeteras.estructuras.TablaHash;
import billeteras.modelo.Billetera;
import billeteras.modelo.EstadoTransaccion;
import billeteras.modelo.Nivel;
import billeteras.modelo.Periodicidad;
import billeteras.modelo.Riesgo;
import billeteras.modelo.TipoBilletera;
import billeteras.modelo.TipoTransaccion;
import billeteras.modelo.Transaccion;
import billeteras.modelo.Usuario;
import billeteras.servicio.DatosDemo;
import billeteras.servicio.ErrorNegocio;
import billeteras.servicio.Plataforma;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Casos de prueba sin dependencias. Ejecutar con: probar.bat
 * Cada caso imprime OK o lanza un error con la descripción de lo que falló.
 */
public class Pruebas {

    private static int casos;

    public static void main(String[] args) {
        estructuras();
        operacionesYPuntos();
        reversion();
        programadas();
        deteccion();
        usuariosYValidaciones();
        datosDemo();
        System.out.println("\n" + casos + " verificaciones correctas.");
    }

    private static void estructuras() {
        ListaEnlazada<Integer> lista = new ListaEnlazada<>();
        for (int i = 1; i <= 5; i++) lista.agregar(i);
        lista.agregarAlInicio(0);
        verificar(lista.tamano() == 6 && lista.primero() == 0 && lista.ultimo() == 5, "lista: agregar al inicio y al final");
        verificar(lista.eliminarSi(x -> x == 5) && lista.ultimo() == 4, "lista: eliminar el último actualiza la cola");
        verificar(lista.eliminarUltimo() == 4 && lista.eliminarPrimero() == 0 && lista.tamano() == 3, "lista: eliminar extremos");

        Pila<String> pila = new Pila<>();
        pila.apilar("a"); pila.apilar("b");
        verificar(pila.desapilar().equals("b") && pila.cima().equals("a"), "pila: sale primero lo último que entró");

        Cola<String> cola = new Cola<>();
        cola.encolar("a"); cola.encolar("b");
        verificar(cola.desencolar().equals("a") && cola.frente().equals("b"), "cola: sale primero lo primero que entró");

        ColaPrioridad<Integer> cp = new ColaPrioridad<Integer>(Comparator.naturalOrder());
        for (int x : new int[] {7, 3, 9, 1, 5, 8, 2}) cp.insertar(x);
        cp.eliminarSi(x -> x == 1);
        StringBuilder orden = new StringBuilder();
        while (!cp.estaVacia()) orden.append(cp.extraerMinimo());
        verificar(orden.toString().equals("235789"), "cola de prioridad: extrae en orden tras eliminar");

        ArbolAVL<Integer, Integer> arbol = new ArbolAVL<>();
        for (int i = 1; i <= 1000; i++) arbol.insertar(i, i); // insertar en orden degenera un ABB sin balanceo
        verificar(arbol.altura() <= 11, "AVL: 1000 claves en orden quedan con altura logarítmica (" + arbol.altura() + ")");
        for (int i = 1; i <= 1000; i += 2) arbol.eliminar(i);
        verificar(arbol.tamano() == 500 && arbol.buscar(2) == 2 && arbol.buscar(3) == null, "AVL: eliminar y buscar");
        verificar(arbol.rango(10, 20).tamano() == 6, "AVL: rango 10..20 entre los pares");
        verificar(arbol.mayores(3, x -> true).primero() == 1000, "AVL: mayores empieza por el máximo");

        TablaHash<String, Integer> tabla = new TablaHash<>();
        for (int i = 0; i < 500; i++) tabla.poner("clave" + i, i);
        tabla.poner("clave7", 70);
        verificar(tabla.tamano() == 500 && tabla.obtener("clave7") == 70 && tabla.capacidad() > 500, "hash: reemplaza y redimensiona");
        verificar(tabla.eliminar("clave7") == 70 && !tabla.contiene("clave7") && tabla.obtener("nada") == null, "hash: eliminar");

        Grafo g = new Grafo();
        g.agregarArista("A", "B", 10); g.agregarArista("B", "C", 10); g.agregarArista("C", "A", 10); g.agregarArista("A", "B", 5);
        verificar(g.totalAristas() == 3 && g.vecinos("A").primero().veces() == 2, "grafo: una arista repetida acumula usos");
        verificar(g.ciclos(4, 10).tamano() == 1, "grafo: encuentra el ciclo A-B-C una sola vez");
        verificar(g.rutaMasCorta("A", "C").tamano() == 3, "grafo: ruta más corta A-B-C");
        g.quitarArista("C", "A", 10);
        verificar(g.ciclos(4, 10).estaVacia() && g.rutaMasCorta("C", "A").estaVacia(), "grafo: sin la arista no hay ciclo ni ruta");
    }

    private static Plataforma nueva() {
        Plataforma p = new Plataforma(LocalDateTime.of(2026, 11, 2, 10, 0));
        p.registrarUsuario("10001", "Ana Prueba", "ana@correo.example", "3000000001");
        p.registrarUsuario("10002", "Beto Prueba", "beto@correo.example", "");
        p.crearBilletera("10001", "Diario", TipoBilletera.GASTOS_DIARIOS);  // BIL-0001
        p.crearBilletera("10001", "Ahorro", TipoBilletera.AHORRO);          // BIL-0002
        p.crearBilletera("10002", "Diario", TipoBilletera.GASTOS_DIARIOS);  // BIL-0003
        return p;
    }

    private static void operacionesYPuntos() {
        Plataforma p = nueva();
        Usuario ana = p.usuario("10001");
        verificar(p.recargar("10001", "BIL-0001", 1000).puntos() == 10, "recarga: 1 punto por cada 100");
        verificar(p.retirar("10001", "BIL-0001", 250).puntos() == 4, "retiro: 2 puntos por cada 100 (250 -> 4)");
        Transaccion t = p.transferir("10001", "BIL-0001", "BIL-0003", 500);
        verificar(t.puntos() == 15 && t.comision() == 5, "transferencia a otro usuario: 3 por cada 100 y comisión Bronce 1 %");
        verificar(p.billetera("BIL-0001").saldo() == 245 && p.billetera("BIL-0003").saldo() == 500, "saldos tras las tres operaciones");
        verificar(ana.puntosAcumulados() == 29 && ana.nivel() == Nivel.BRONCE, "puntos acumulados");
        verificar(ana.historial().tamano() == 3 && p.usuario("10002").historial().tamano() == 1, "historiales del emisor y del receptor");

        verificar(falla(() -> p.retirar("10001", "BIL-0001", 9999)).contains("por operación"), "el nivel Bronce limita cada operación a 5.000");
        String error = falla(() -> p.retirar("10001", "BIL-0001", 300));
        verificar(error.contains("Saldo insuficiente") && p.billetera("BIL-0001").saldo() == 245, "retiro sin saldo: se rechaza y no cambia el saldo");
        verificar(ana.historial().ultimo().estado() == EstadoTransaccion.RECHAZADA, "el rechazo queda en el historial");
        verificar(ana.notificaciones().primero().mensaje().contains("rechazada"), "el rechazo genera alerta");
        verificar(falla(() -> p.transferir("10001", "BIL-0003", "BIL-0001", 10)).contains("no pertenece"), "no se puede mover dinero de otro");
        verificar(falla(() -> p.recargar("10001", "BIL-0001", 0)).contains("mayor que cero"), "valor cero inválido");
        p.cambiarEstadoBilletera("BIL-0002", false);
        verificar(falla(() -> p.recargar("10001", "BIL-0002", 100)).contains("inactiva"), "billetera inactiva no opera");

        for (int i = 0; i < 4; i++) p.recargar("10001", "BIL-0001", 5000);  // 4 x 50 = 200 puntos
        for (int i = 0; i < 3; i++) p.retirar("10001", "BIL-0001", 5000);   // 3 x 100 = 300 puntos
        verificar(ana.puntosAcumulados() == 529 && ana.nivel() == Nivel.PLATA, "con 529 puntos sube a Plata");
        verificar(p.usuariosPorPuntos(501, 1000).tamano() == 1 && p.ranking().primero() == ana, "árbol: rango de puntos y ranking");
        verificar(p.recargar("10001", "BIL-0001", 1000).puntos() == 11, "Plata recibe 10 % más de puntos");
        p.canjear("10001", "SIN-COMISION");
        verificar(ana.puntosDisponibles() == 390 && ana.puntosAcumulados() == 540, "canjear descuenta disponibles, no el nivel");
        verificar(p.transferir("10001", "BIL-0001", "BIL-0003", 1000).comision() == 0 && ana.transferenciasSinComision() == 2, "beneficio sin comisión");
        verificar(falla(() -> p.canjear("10001", "BONO-150")).contains("Faltan"), "canje sin puntos suficientes");
    }

    private static void reversion() {
        Plataforma p = nueva();
        Usuario ana = p.usuario("10001");
        p.recargar("10001", "BIL-0001", 2000);
        p.transferir("10001", "BIL-0001", "BIL-0003", 1000);
        verificar(ana.puntosAcumulados() == 50 && p.grafoUsuarios().totalAristas() == 1, "antes de revertir");
        Transaccion r = p.revertirUltima("10001");
        verificar(r.tipo() == TipoTransaccion.REVERSION && r.puntos() == -30, "la reversión descuenta los puntos de la transferencia");
        verificar(p.billetera("BIL-0001").saldo() == 2000 && p.billetera("BIL-0003").saldo() == 0, "la reversión devuelve valor y comisión");
        verificar(ana.puntosAcumulados() == 20 && p.grafoUsuarios().totalAristas() == 0, "puntos recalculados y arista retirada");
        verificar(ana.deshacer().tamano() == 1 && ana.historial().tamano() == 3, "la pila baja y el historial conserva todo");
        p.transferir("10001", "BIL-0001", "BIL-0003", 1000);
        p.retirar("10002", "BIL-0003", 800); // el receptor ya gastó parte del dinero
        verificar(falla(() -> p.revertirUltima("10001")).contains("solo tiene") && ana.deshacer().tamano() == 2,
            "no se revierte una transferencia si el receptor ya no tiene el dinero");
    }

    private static void programadas() {
        Plataforma p = nueva();
        LocalDateTime t0 = p.ahora();
        p.recargar("10001", "BIL-0001", 1000);
        p.programar("10001", TipoTransaccion.TRANSFERENCIA, "BIL-0001", "BIL-0002", 100, t0.plusDays(2), Periodicidad.SEMANAL, "Ahorro");
        p.programar("10002", TipoTransaccion.RETIRO, "BIL-0003", null, 50, t0.plusHours(5), Periodicidad.UNICA, "Pago");
        p.programar("10001", TipoTransaccion.RECARGA, null, "BIL-0001", 30, t0.plusHours(3), Periodicidad.DIARIA, "Recarga");
        verificar(p.programadas().verMinimo().descripcion().equals("Recarga"), "la cola de prioridad pone primero la fecha más cercana");
        verificar(falla(() -> p.programar("10001", TipoTransaccion.RECARGA, null, "BIL-0001", 30, t0, Periodicidad.UNICA, "")).contains("posterior"), "no se programa en el pasado");

        Plataforma.ResultadoAvance r = p.avanzarReloj(6);
        verificar(r.ejecutadas() == 1 && r.rechazadas() == 1, "a las 6 h: se ejecuta la recarga y se rechaza el pago sin saldo");
        verificar(p.billetera("BIL-0001").saldo() == 1030 && p.programadas().tamano() == 2, "la diaria se vuelve a encolar y la única sale");
        Transaccion ultima = p.usuario("10001").historial().ultimo();
        verificar(ultima.programada() && ultima.puntos() == 10, "bono de 10 puntos por programada ejecutada (recarga de 30 no da base)");
        r = p.avanzarReloj(24 * 7);
        verificar(r.ejecutadas() == 8 && p.billetera("BIL-0002").saldo() == 100, "en una semana: 7 recargas diarias y 1 ahorro semanal");
        verificar(p.ahora().equals(t0.plusHours(6 + 24 * 7)), "el reloj queda en la hora pedida");
    }

    private static void deteccion() {
        Plataforma p = nueva();
        p.recargar("10001", "BIL-0001", 5000);
        Transaccion ultima = null;
        for (int i = 0; i < 4; i++) {
            p.fijarReloj(p.ahora().plusMinutes(1));
            ultima = p.transferir("10001", "BIL-0001", "BIL-0003", 100);
        }
        verificar(ultima.riesgo() == Riesgo.MEDIO && p.auditoria().tamano() >= 2, "4 transferencias en 4 minutos: ráfaga y destino repetido");
        verificar(p.usuario("10001").notificaciones().primero().mensaje().contains("seguridad"), "la detección genera alerta de seguridad");

        Plataforma q = nueva();
        q.recargar("10001", "BIL-0001", 5000);
        for (int i = 0; i < 5; i++) { q.fijarReloj(q.ahora().plusHours(3)); q.retirar("10001", "BIL-0001", 100); }
        q.fijarReloj(q.ahora().plusHours(3));
        verificar(q.retirar("10001", "BIL-0001", 700).riesgo() == Riesgo.ALTO, "retiro 7 veces mayor al promedio: riesgo alto");
    }

    private static void usuariosYValidaciones() {
        Plataforma p = nueva();
        verificar(falla(() -> p.registrarUsuario("10001", "Otra", "o@correo.example", "")).contains("Ya existe"), "identificación duplicada");
        verificar(falla(() -> p.registrarUsuario("abc", "Otra", "o@correo.example", "")).contains("dígitos"), "identificación inválida");
        verificar(falla(() -> p.registrarUsuario("10009", "Otra", "sin-arroba", "")).contains("correo"), "correo inválido");
        verificar(falla(() -> p.usuario("99999")).contains("No existe"), "usuario inexistente");
        verificar(p.modificarUsuario("10002", "Beto Nuevo", "beto@correo.example", "3000000002").nombre().equals("Beto Nuevo"), "modificar usuario");
        p.recargar("10002", "BIL-0003", 100);
        verificar(falla(() -> p.eliminarUsuario("10002")).contains("aún tiene"), "no se elimina un usuario con saldo");
        p.retirar("10002", "BIL-0003", 100);
        p.eliminarUsuario("10002");
        verificar(!p.usuarios().contiene("10002") && !p.billeteras().contiene("BIL-0003") && p.rankingPuntos().tamano() == 1, "eliminar limpia hash, billeteras y árbol");
        verificar(falla(() -> p.crearBilletera("10001", "diario", TipoBilletera.COMPRAS)).contains("ya tiene"), "nombre de billetera repetido");
        p.eliminarBilletera("BIL-0002");
        int restantes = 0;
        for (Billetera b : p.usuario("10001").billeteras()) restantes++;
        verificar(restantes == 1, "eliminar billetera vacía");
    }

    private static void datosDemo() {
        Plataforma p = DatosDemo.crear(LocalDateTime.of(2026, 11, 12, 20, 30));
        StringBuilder niveles = new StringBuilder();
        for (Usuario u : p.ranking()) niveles.append(String.format("%n   %-26s %6d pts  %-8s saldo %s", u.nombre(), u.puntosAcumulados(), u.nivel().etiqueta(), Plataforma.dinero(u.saldoTotal())));
        System.out.println("   Datos de ejemplo: " + p.transacciones().tamano() + " transacciones, " + p.auditoria().tamano()
            + " eventos de auditoría, " + p.grafoUsuarios().ciclos(4, 8).tamano() + " ciclos" + niveles);
        verificar(p.usuarios().tamano() == 8 && p.programadas().tamano() == 6, "datos de ejemplo: 8 usuarios y 6 programadas");
        verificar(p.auditoria().tamano() >= 4, "datos de ejemplo: los casos preparados disparan la detección");
        long total = 0;
        for (Billetera b : p.billeteras().valores()) { verificar(b.saldo() >= 0, "saldo no negativo en " + b.codigo()); total += b.saldo(); }
        verificar(total > 0, "hay dinero en la plataforma");
    }

    private static String falla(Runnable accion) {
        try {
            accion.run();
        } catch (ErrorNegocio e) {
            return e.getMessage();
        }
        throw new AssertionError("Se esperaba un rechazo y la operación pasó");
    }

    private static void verificar(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError("FALLÓ: " + descripcion);
        casos++;
        if (!descripcion.startsWith("saldo no negativo")) System.out.println("OK  " + descripcion);
    }
}
