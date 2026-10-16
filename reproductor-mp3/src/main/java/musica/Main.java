package musica;

import musica.red.ServicioMusica;
import musica.red.ServicioMusicaSimulado;
import musica.ui.VentanaReproductor;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) { }

            // ------------------------------------------------------------
            // Cambiar esta línea cuando el servicio UDP esté listo:
            //   ServicioMusica servicio = new ServicioMusicaUDP("localhost", 5000);
            // ------------------------------------------------------------
            ServicioMusica servicio = new ServicioMusicaSimulado();

            new VentanaReproductor(servicio).setVisible(true);
        });
    }
}
