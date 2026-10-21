import javax.swing.*;
import java.awt.*;

/** Primera ventana: pide IP y puerto del servidor y solicita la lista de canciones. */
public class VentanaConexion extends JFrame {

    private final JTextField txtIp = new JTextField("localhost", 14);
    private final JTextField txtPuerto = new JTextField("5000", 6);
    private final JButton btnConectar = new JButton("Conectar");
    private final JLabel lblEstado = new JLabel("Escribe la IP y el puerto del servidor");

    public VentanaConexion() {
        super("Reproductor MP3 - Conexion");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;

        JLabel titulo = new JLabel("Reproductor de musica por UDP");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        panel.add(titulo, g);

        g.gridwidth = 1;
        g.gridx = 0; g.gridy = 1; panel.add(new JLabel("IP del servidor:"), g);
        g.gridx = 1;              panel.add(txtIp, g);
        g.gridx = 0; g.gridy = 2; panel.add(new JLabel("Puerto:"), g);
        g.gridx = 1;              panel.add(txtPuerto, g);

        g.gridx = 0; g.gridy = 3; g.gridwidth = 2;
        g.anchor = GridBagConstraints.CENTER;
        panel.add(btnConectar, g);

        g.gridy = 4;
        panel.add(lblEstado, g);

        add(panel);
        getRootPane().setDefaultButton(btnConectar);
        btnConectar.addActionListener(e -> conectar());

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void conectar() {
        String ip = txtIp.getText().trim();
        int puerto;
        try {
            puerto = Integer.parseInt(txtPuerto.getText().trim());
            if (puerto < 1 || puerto > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            lblEstado.setText("El puerto debe ser un numero entre 1 y 65535");
            return;
        }
        if (ip.isEmpty()) {
            lblEstado.setText("Escribe la IP del servidor");
            return;
        }
        btnConectar.setEnabled(false);
        ClienteMusica.conectar(ip, puerto);
    }

    /** Se puede llamar desde cualquier hilo. */
    public void setEstado(String mensaje) {
        SwingUtilities.invokeLater(() -> lblEstado.setText(mensaje));
    }

    /** Muestra un error y vuelve a habilitar el boton. Se puede llamar desde cualquier hilo. */
    public void error(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            lblEstado.setText(mensaje);
            btnConectar.setEnabled(true);
        });
    }
}
