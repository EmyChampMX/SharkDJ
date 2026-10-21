
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Envoltura sencilla sobre JLayer (AdvancedPlayer) con reproducir / pausar /
 * reanudar / detener. JLayer no trae "pausa", así que se simula: al pausar se
 * recuerda el tiempo transcurrido y al reanudar se salta a ese punto.
 *
 * Es independiente de la red: sólo recibe un {@link File} .mp3 ya completo.
 */
public class ReproductorMP3 {

    public enum Estado { DETENIDO, REPRODUCIENDO, PAUSADO }

    /** Duración aproximada de un frame MP3 a 44.1 kHz (1152 muestras). */
    private static final double MS_POR_FRAME = 26.12;

    private File archivo;
    private Runnable alTerminar;
    private AdvancedPlayer actual;      // null cuando no hay nada sonando
    private volatile Estado estado = Estado.DETENIDO;
    private long offsetMs;              // punto desde el que se (re)inició
    private long inicioNano;            // momento en que se (re)inició

    /** Empieza a reproducir {@code f} desde el principio. */
    public synchronized void reproducir(File f, Runnable alTerminar) throws IOException {
        detener();
        this.archivo = f;
        this.alTerminar = alTerminar;
        iniciarDesde(0);
    }

    public synchronized void pausar() {
        if (estado != Estado.REPRODUCIENDO) return;
        offsetMs = posicionMs();
        estado = Estado.PAUSADO;
        detenerPlayer();
    }

    public synchronized void reanudar() throws IOException {
        if (estado != Estado.PAUSADO) return;
        iniciarDesde(offsetMs);
    }

    public synchronized void detener() {
        estado = Estado.DETENIDO;
        offsetMs = 0;
        detenerPlayer();
    }

    public Estado estado() {
        return estado;
    }

    /** Milisegundos reproducidos hasta ahora. */
    public synchronized long posicionMs() {
        if (estado == Estado.REPRODUCIENDO) {
            return offsetMs + (System.nanoTime() - inicioNano) / 1_000_000L;
        }
        return offsetMs;
    }

    // ------------------------------------------------------------------

    private void iniciarDesde(long desdeMs) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(archivo));
        final AdvancedPlayer p;
        try {
            p = new AdvancedPlayer(in);
        } catch (JavaLayerException e) {
            in.close();
            throw new IOException("No se pudo decodificar el MP3: " + e.getMessage(), e);
        }
        final int frameInicial = (int) (desdeMs / MS_POR_FRAME);
        actual = p;
        offsetMs = desdeMs;
        inicioNano = System.nanoTime();
        estado = Estado.REPRODUCIENDO;

        Thread hilo = new Thread(() -> {
            try {
                p.play(frameInicial, Integer.MAX_VALUE);
            } catch (JavaLayerException e) {
                e.printStackTrace();
            } finally {
                try { in.close(); } catch (IOException ignorada) { }
            }
            Runnable aviso = null;
            synchronized (ReproductorMP3.this) {
                // Sólo es "fin natural" si nadie pausó/detuvo (eso pone actual = null).
                if (actual == p) {
                    actual = null;
                    estado = Estado.DETENIDO;
                    offsetMs = 0;
                    aviso = alTerminar;
                }
            }
            if (aviso != null) aviso.run();
        }, "reproductor-mp3");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void detenerPlayer() {
        AdvancedPlayer p = actual;
        actual = null;
        if (p != null) p.stop();
    }
}
