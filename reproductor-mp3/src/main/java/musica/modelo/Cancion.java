package musica.modelo;

/**
 * Datos de una canción tal como los manda el servidor (leídos de las etiquetas ID3v1/ID3v2).
 *
 * @param id          identificador único que usa el servidor para pedir el archivo
 *                    (puede ser el nombre del archivo o un número).
 * @param titulo      título de la canción.
 * @param artista     artista / intérprete.
 * @param album       álbum (puede venir vacío).
 * @param genero      género musical.
 * @param duracionSeg duración en segundos (0 si el servidor no la conoce).
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
