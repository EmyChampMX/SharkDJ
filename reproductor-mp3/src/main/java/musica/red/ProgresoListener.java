package musica.red;

/**
 * Callback para avisarle a la interfaz cuánto se ha recibido de una canción.
 * Quien implemente la descarga por UDP lo invoca cada vez que llegue (y se
 * confirme) un paquete nuevo.
 */
@FunctionalInterface
public interface ProgresoListener {

    /**
     * @param bytesRecibidos bytes recibidos hasta ahora
     * @param bytesTotales   tamaño total del archivo, o -1 si todavía no se conoce
     */
    void alProgresar(long bytesRecibidos, long bytesTotales);
}
