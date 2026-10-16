package musica.red;

import musica.modelo.Cancion;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * CONTRATO entre la interfaz gráfica (cliente) y la capa de red.
 *
 * La GUI sólo conoce esta interfaz. Todo lo de sockets de datagramas, Ventana
 * Deslizante y Retroceder-N vive en la clase que la implemente
 * (por ejemplo, {@code ServicioMusicaUDP}) y la GUI no se entera.
 *
 * IMPORTANTE: la GUI llama a estos métodos desde un hilo de fondo
 * (SwingWorker), nunca desde el hilo de la interfaz, así que pueden bloquearse
 * esperando datagramas sin congelar la ventana.
 */
public interface ServicioMusica {

    /**
     * Pide al servidor el listado de canciones con su información
     * (título, artista, álbum, género, duración).
     * Bloquea hasta tener la lista completa.
     */
    List<Cancion> obtenerCatalogo() throws IOException;

    /**
     * Solicita la canción al servidor, la recibe completa (con Ventana
     * Deslizante + Retroceder-N) y la guarda en un archivo local .mp3.
     *
     * Debe llamar a {@code progreso.alProgresar(...)} conforme avance la
     * recepción. Si el hilo es interrumpido (el usuario pulsó "Cancelar"),
     * debe dejar de recibir y lanzar {@link java.io.InterruptedIOException}.
     *
     * @return el archivo .mp3 ya completo, listo para reproducirse
     */
    File descargarCancion(Cancion cancion, ProgresoListener progreso) throws IOException;

    /** Libera sockets y recursos. Se llama al cerrar la ventana. */
    void cerrar();
}
