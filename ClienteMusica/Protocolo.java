/**
 * PROTOCOLO cliente <-> servidor (todo con DataOutputStream / DataInputStream).
 * Cada datagrama empieza con un int "tipo" y después los campos que se indican.
 *
 * Cliente -> Servidor
 *   SOLICITUD_LISTA                                   (sin campos)
 *   SOLICITUD_CANCION  UTF id
 *   ACK                int siguienteEsperado          (ACK acumulativo: ya llegaron todos los paquetes < n)
 *   CANCELAR                                          (sin campos)
 *
 * Servidor -> Cliente
 *   INFO_CANCION  int indice, int total, UTF id, UTF titulo, UTF artista, UTF album, UTF genero, int duracionSeg
 *                 (un datagrama por canción; si no hay canciones: un solo datagrama con total = 0)
 *   DATOS         int seq, int totalPaquetes, long tamArchivo, int len, byte[len]
 *                 (seq empieza en 0; cada paquete lleva totalPaquetes y tamArchivo para no depender de un "inicio")
 *   ERROR         UTF mensaje
 *
 * Retroceder-N:  el cliente sólo acepta el paquete seq == siguienteEsperado; cualquier otro lo descarta
 * y vuelve a mandar ACK(siguienteEsperado). El servidor, al vencer su temporizador, retransmite desde la base.
 *
 * IMPORTANTE: el servidor debe responder desde el MISMO puerto al que llegó la solicitud.
 */
public class Protocolo {

    // Cliente -> Servidor
    public static final int SOLICITUD_LISTA   = 1;
    public static final int SOLICITUD_CANCION = 2;
    public static final int ACK               = 3;
    public static final int CANCELAR          = 4;

    // Servidor -> Cliente
    public static final int INFO_CANCION = 10;
    public static final int DATOS        = 11;
    public static final int ERROR        = 12;

    public static final int TAM_DATOS  = 1024;   // bytes de MP3 por datagrama
    public static final int TAM_BUFFER = 2048;   // buffer de recepción (> TAM_DATOS + cabecera)

    public static final int TIMEOUT_MS     = 1000;  // espera antes de reintentar
    public static final int MAX_REINTENTOS = 5;

    /** Probabilidad (0..1) de descartar a propósito un paquete DATOS recibido, para demostrar Retroceder-N. */
    public static double PROB_PERDIDA = 0.0;
}
