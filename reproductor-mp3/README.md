# Práctica 2 – Control de flujo: Reproductor MP3 por UDP

Cliente con interfaz gráfica (Java Swing + JLayer). El servidor y el protocolo
(Ventana Deslizante + Retroceder-N sobre `DatagramSocket`) se desarrollan por separado.

## Requisitos
- JDK 17 o superior
- Maven (o importar el `pom.xml` en IntelliJ / Eclipse / NetBeans / VS Code)

## Ejecutar (modo simulado, sin servidor)
1. Copia cualquier `.mp3` a la carpeta `mp3_prueba/`.
2. `mvn compile exec:java`

## Estructura
```
src/main/java/musica/
├── Main.java                      ← arranca la GUI  (aquí se cambia Simulado → UDP)
├── modelo/Cancion.java            ← datos de una canción (ID3)
├── red/
│   ├── ServicioMusica.java        ← CONTRATO GUI ⇄ red  (no cambiar sin avisar)
│   ├── ProgresoListener.java      ← callback de progreso de descarga
│   ├── ServicioMusicaSimulado.java← falso, sólo para probar la GUI
│   └── ServicioMusicaUDP.java     ← (compañero) implementación real con UDP
├── audio/ReproductorMP3.java      ← reproducir/pausar/detener con JLayer
├── ui/VentanaReproductor.java     ← la interfaz
└── servidor/                      ← (compañero) servidor UDP + lectura ID3
```

## Qué debe implementar el compañero
Una clase `musica.red.ServicioMusicaUDP implements ServicioMusica`:

| Método | Qué hace |
|---|---|
| `List<Cancion> obtenerCatalogo()` | Pide al servidor la lista (título, artista, álbum, género, duración) y la devuelve. |
| `File descargarCancion(Cancion c, ProgresoListener p)` | Pide la canción, la recibe con Ventana Deslizante + Retroceder-N, la guarda en un `.mp3` local y devuelve el `File`. Llama a `p.alProgresar(recibidos, total)` mientras avanza. Si el hilo es interrumpido, lanza `InterruptedIOException`. |
| `void cerrar()` | Cierra el socket. |

Luego en `Main.java` se cambia una sola línea:
```java
ServicioMusica servicio = new ServicioMusicaUDP("localhost", 5000);
```

Notas:
- La GUI llama a estos métodos en un hilo de fondo, así que pueden bloquear sin problema.
- La búsqueda y el filtro por género se hacen en el cliente sobre la lista recibida;
  el protocolo sólo necesita 2 operaciones: "dame el catálogo" y "dame la canción X".
- Si más adelante el protocolo necesita otro dato en `Cancion` (p. ej. tamaño en bytes),
  se agrega al `record` y se avisa al otro.

## Flujo de trabajo con GitHub (sugerido)
- Ramas: `main` (estable), `gui` (cliente/interfaz), `servidor` (servidor y protocolo UDP).
- Cada quien trabaja sólo en sus paquetes (`ui/`, `audio/` vs. `servidor/`, `red/ServicioMusicaUDP.java`)
  para evitar conflictos de merge.
- Cambios al contrato (`ServicioMusica`, `Cancion`) → avisar antes y hacerlos en un commit aparte.
- Hacer `git pull` antes de empezar a trabajar y `Pull Request` hacia `main` al terminar un avance.
