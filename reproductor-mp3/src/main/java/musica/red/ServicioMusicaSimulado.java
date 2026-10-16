package musica.red;

import musica.modelo.Cancion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Implementación FALSA de {@link ServicioMusica} para probar la interfaz sin
 * necesidad del servidor. Inventa un catálogo y "descarga" copiando cualquier
 * .mp3 que pongas en la carpeta {@code mp3_prueba/}.
 *
 * Se deja de usar cuando esté lista la versión con UDP.
 */
public class ServicioMusicaSimulado implements ServicioMusica {

    private static final File CARPETA_PRUEBA = new File("mp3_prueba");

    @Override
    public List<Cancion> obtenerCatalogo() throws IOException {
        dormir(700);
        return List.of(
                new Cancion("1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "Rock", 355),
                new Cancion("2", "Somebody to Love", "Queen", "A Day at the Races", "Rock", 296),
                new Cancion("3", "Hotel California", "Eagles", "Hotel California", "Rock", 391),
                new Cancion("4", "Billie Jean", "Michael Jackson", "Thriller", "Pop", 294),
                new Cancion("5", "Thriller", "Michael Jackson", "Thriller", "Pop", 357),
                new Cancion("6", "Sabor a Mí", "Los Panchos", "Amor", "Bolero", 190),
                new Cancion("7", "La Bamba", "Ritchie Valens", "Ritchie Valens", "Rock and Roll", 126),
                new Cancion("8", "Take Five", "Dave Brubeck", "Time Out", "Jazz", 324),
                new Cancion("9", "Clair de Lune", "Claude Debussy", "Suite bergamasque", "Clásica", 305));
    }

    @Override
    public File descargarCancion(Cancion cancion, ProgresoListener progreso) throws IOException {
        File[] mp3s = CARPETA_PRUEBA.listFiles((d, n) -> n.toLowerCase().endsWith(".mp3"));
        if (mp3s == null || mp3s.length == 0) {
            throw new IOException("Modo simulado: coloca al menos un archivo .mp3 en la carpeta '"
                    + CARPETA_PRUEBA.getName() + "/' para probar la reproducción.");
        }
        File origen = mp3s[0];
        long total = origen.length();
        File destino = Files.createTempFile("cliente_", ".mp3").toFile();
        destino.deleteOnExit();

        try (InputStream in = Files.newInputStream(origen.toPath());
             OutputStream out = Files.newOutputStream(destino.toPath())) {
            byte[] buffer = new byte[32 * 1024];
            long recibidos = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Descarga cancelada");
                }
                out.write(buffer, 0, n);
                recibidos += n;
                progreso.alProgresar(recibidos, total);
                dormir(30); // simula la latencia de la red
            }
        }
        return destino;
    }

    @Override
    public void cerrar() { /* nada que cerrar */ }

    private static void dormir(long ms) throws InterruptedIOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Operación cancelada");
        }
    }
}
