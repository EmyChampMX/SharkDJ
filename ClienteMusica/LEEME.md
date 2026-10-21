# Cliente del reproductor MP3 (Práctica 2) — UDP + Ventana Deslizante / Retroceder-N

Misma estructura que `ClienteBatalla`: paquete por defecto, hilo receptor, métodos estáticos y clase `Protocolo`.

## Archivos
| Archivo | Qué hace |
|---|---|
| `ClienteMusica.java` | `main`, hilo que escucha el socket, receptor Retroceder-N, reintentos |
| `Protocolo.java` | Tipos de mensaje y formato de cada datagrama (**acordar con el compañero**) |
| `VentanaConexion.java` | IP + puerto, pide la lista de canciones |
| `VentanaReproductor.java` | Búsqueda, géneros, tabla, controles, progreso de descarga |
| `ReproductorMP3.java` | Reproducir / pausar / detener con JLayer |
| `Cancion.java` | Datos de una canción (ID3) |
| `ServidorPrueba.java` | Servidor de ejemplo SOLO para probar el cliente (el real es del compañero) |

## Compilar y ejecutar
Necesitas JDK 17+ y `jlayer-1.0.1.jar` (agregarlo como librería en el IDE, o en consola):
```
javac -encoding UTF-8 -cp jlayer.jar *.java
java -cp ".;jlayer.jar" ServidorPrueba 5000 mp3_servidor 8      (Linux/Mac: ".:jlayer.jar")
java -cp ".;jlayer.jar" ClienteMusica
```
Para probar con servidor propio: poner MP3 en `mp3_servidor/<Genero>/archivo.mp3`.

Simular pérdidas para demostrar Retroceder-N:
- cliente descarta paquetes recibidos: `java -cp ".;jlayer.jar" ClienteMusica 0.1`
- servidor descarta paquetes enviados: `java -cp ".;jlayer.jar" ServidorPrueba 5000 mp3_servidor 8 0.1`

## Lo que debe cumplir el servidor real
1. Responder **desde el mismo puerto** al que llegó la solicitud.
2. `SOLICITUD_LISTA` → un `INFO_CANCION` por canción (índice, total, id, título, artista, álbum, género, duración).
3. `SOLICITUD_CANCION` → paquetes `DATOS` con `seq` desde 0, cada uno con `totalPaquetes` y `tamArchivo`.
4. `ACK(n)` es acumulativo: "ya recibí todo lo anterior a n; mándame desde n".
   El cliente descarta cualquier paquete con `seq != n` y repite `ACK(n)`.
5. Si vence el temporizador sin avanzar la base, retransmitir toda la ventana desde la base (Retroceder-N).
6. Al recibir `CANCELAR`, dejar de enviar.
7. Errores (canción inexistente, etc.) → mensaje `ERROR` con texto.

Formato exacto de cada mensaje: comentario al inicio de `Protocolo.java`.
Si cambian algo, editar sólo `Protocolo.java` y el `readXxx/writeXxx` correspondiente.

## Git
Sugerencia: no subir MP3 (ver `.gitignore`); cambios a `Protocolo.java` en commit aparte y avisando al compañero.
