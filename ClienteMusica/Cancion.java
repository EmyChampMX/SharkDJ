/**
 * Datos de una canción tal como los manda el servidor (etiquetas ID3).
 *
 * id          = identificador que usa el servidor para pedir el archivo (p. ej. el nombre del .mp3)
 * duracionSeg = duración en segundos (0 si el servidor no la conoce)
 */
public record Cancion(String id, String titulo, String artista, String album,
                      String genero, int duracionSeg) {

    public String duracionFormateada() {
        return formatearTiempo(duracionSeg);
    }

    public static String formatearTiempo(long segundos) {
        return String.format("%02d:%02d", segundos / 60, segundos % 60);
    }
}
