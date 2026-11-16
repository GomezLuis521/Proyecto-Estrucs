# SavingCol · Plataforma fintech de billeteras digitales

Proyecto final de Estructuras de Datos (2026-2). Simula una plataforma de billeteras digitales:
usuarios, billeteras, recargas, retiros, transferencias, operaciones programadas, puntos y niveles,
reversión, alertas, analítica y detección de comportamiento inusual. La lógica y las estructuras
están en Java; la interfaz gráfica es una página web que el mismo programa sirve.

## Ejecutar

Requiere JDK 17 o superior (probado con JDK 23). No usa Maven ni librerías externas.

```
ejecutar.bat          compila, arranca el servidor y abre http://localhost:8085
probar.bat            corre los 87 casos de prueba
```

Los datos viven en memoria: al reiniciar el programa vuelven los datos de ejemplo
(8 usuarios ficticios y un mes de movimientos simulados).

## Organización

| Paquete | Responsabilidad |
|---|---|
| `billeteras.estructuras` | Estructuras programadas a mano: `ListaEnlazada`, `Pila`, `Cola`, `ColaPrioridad`, `ArbolAVL`, `TablaHash`, `Grafo`. |
| `billeteras.modelo` | Entidades: `Usuario`, `Billetera`, `Transaccion`, `OperacionProgramada`, `Notificacion`, `EventoAuditoria`, niveles y tipos. |
| `billeteras.servicio` | Reglas del negocio: `Plataforma`, `Recompensas`, `DetectorInusual`, `Analitica`, `ComparadorRendimiento`, `DatosDemo`. |
| `billeteras.api` | Servidor HTTP del JDK y conversión a JSON. Única capa que usa `java.util` (solo como formato de salida). |
| `web/` | Interfaz: `index.html`, `estilos.css`, `app.js` y `vitrina.html` (billeteras en 3D). No contiene lógica de negocio. |
| `test/` | `Pruebas.java`: casos de prueba ejecutables. |

## Estructuras de datos y por qué se eligieron

| Estructura | Dónde se usa | Justificación |
|---|---|---|
| Lista enlazada | Historial por usuario y por billetera, canjes, auditoría, operaciones de un período | Un historial solo crece por el final y se lee en orden: agregar es O(1). |
| Pila | Operaciones reversibles de cada usuario | Deshacer es LIFO: solo la última operación se revierte sin dejar saldos inconsistentes. O(1). |
| Cola | Notificaciones pendientes de despacho | Las alertas se entregan en el orden en que ocurrieron (FIFO). O(1). |
| Cola de prioridad (montículo) | Operaciones programadas | Siempre se necesita la de fecha más cercana: O(1) para verla, O(log n) para insertar o extraer. |
| Árbol AVL | Usuarios por puntos, transacciones por valor | Clasificación, búsqueda por rango O(log n + k) y mayores valores, sin degenerar aunque los datos lleguen ordenados. |
| Tabla hash | Usuarios por identificación, billeteras por código, configuración, conteos de analítica | Cada operación empieza buscando por clave: O(1) promedio. |
| Grafo dirigido ponderado | Transferencias entre usuarios y entre billeteras | Rutas frecuentes (aristas), ruta más corta (BFS) y ciclos de dinero (DFS) en O(V + A). |

En la vista **Analítica**, el panel *Comparar rendimiento de estructuras* mide su rendimiento real
(insertar, buscar y obtener los mayores) con la cantidad de elementos que se elija.

## Reglas principales

- Puntos: recarga 1, retiro 2, transferencia 3 por cada 100 unidades; +10 si la operación era programada.
- Niveles: Bronce 0–500, Plata 501–1000, Oro 1001–5000, Platino más de 5000. Cada nivel da bono de
  puntos, menor comisión al transferir a otros usuarios y mayor límite por operación (ver `Nivel.java`).
- Revertir devuelve el dinero y la comisión, descuenta los puntos generados y puede bajar el nivel.
- Detección de inusuales: ráfaga, monto alto, destino repetido, fragmentación, cambio de frecuencia
  y horario no habitual (ver `DetectorInusual.java`).

## Entregables

Todos juntos en Word: `entregables/SavingCol-entregables.docx`. Las fuentes en Markdown:

| Entregable | Archivo |
|---|---|
| Diagrama de clases | `entregables/diagrama-clases.md` |
| Explicación y justificación de las estructuras | `entregables/estructuras-de-datos.md` |
| Casos de prueba y ejemplos de ejecución | `entregables/casos-de-prueba.md` |
| Informe final técnico | `entregables/informe-tecnico.md` |
