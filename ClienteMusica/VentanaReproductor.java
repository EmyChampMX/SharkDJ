import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;

/**
 * Ventana principal estilo reproductor de musica.
 *
 *  - Arriba: busqueda por nombre / artista / genero.
 *  - Izquierda: filtro por genero.
 *  - Centro: tabla con las canciones que mando el servidor.
 *  - Abajo: controles de reproduccion y progreso de la descarga.
 *
 * Los metodos publicos de abajo (mostrarCatalogo, progresoDescarga, descargaCompleta, ...)
 * los llama el hilo de ClienteMusica; todos se pasan al hilo de Swing con invokeLater.
 */
public class VentanaReproductor extends JFrame {

    private static final String TODOS_LOS_GENEROS = "Todos los generos";

    private final ReproductorMP3 reproductor = new ReproductorMP3();

    private List<Cancion> catalogo = List.of();
    private List<Cancion> visibles = List.of();
    private Cancion cancionActual;
    private boolean descargando;

    // --- componentes ---
    private final JTextField txtBusqueda = new JTextField(24);
    private final JComboBox<String> cmbCriterio =
            new JComboBox<>(new String[]{"Todo", "Nombre", "Artista", "Genero"});
    private final JButton btnActualizar = new JButton("Actualizar lista");
    private final DefaultListModel<String> modeloGeneros = new DefaultListModel<>();
    private final JList<String> listaGeneros = new JList<>(modeloGeneros);
    private final ModeloCanciones modeloTabla = new ModeloCanciones();
    private final JTable tabla = new JTable(modeloTabla);

    private final JLabel lblAhoraSuena = new JLabel("Selecciona una cancion");
    private final JProgressBar barraReproduccion = new JProgressBar(0, 1000);
    private final JLabel lblTiempo = new JLabel("00:00 / 00:00");
    private final JButton btnReproducir = new JButton("Reproducir");
    private final JButton btnPausa = new JButton("Pausar");
    private final JButton btnDetener = new JButton("Detener");
    private final JButton btnCancelar = new JButton("Cancelar descarga");
    private final JProgressBar barraDescarga = new JProgressBar(0, 100);
    private final JLabel lblEstado = new JLabel(" ");
    private final Timer temporizador = new Timer(250, e -> refrescarProgresoReproduccion());

    public VentanaReproductor(List<Cancion> catalogoInicial) {
        super("Reproductor MP3 - Practica 2");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                reproductor.detener();
                ClienteMusica.cerrar();
            }
        });

        construirInterfaz();
        conectarEventos();

        setSize(900, 560);
        setMinimumSize(new Dimension(720, 420));
        setLocationRelativeTo(null);

        catalogo = catalogoInicial;
        poblarGeneros();
        aplicarFiltros();
        lblEstado.setText(catalogo.size() + " canciones disponibles en el servidor.");
    }

    // =====================================================================
    //  Metodos que llama ClienteMusica (cualquier hilo)
    // =====================================================================

    /** Llego una lista nueva (por "Actualizar lista"). */
    public void mostrarCatalogo(List<Cancion> lista) {
        SwingUtilities.invokeLater(() -> {
            catalogo = lista;
            poblarGeneros();
            aplicarFiltros();
            btnActualizar.setEnabled(true);
            lblEstado.setText(catalogo.size() + " canciones disponibles en el servidor.");
        });
    }

    public void catalogoFallo(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            btnActualizar.setEnabled(true);
            lblEstado.setText("No se pudo obtener la lista de canciones.");
            mostrarError("Lista de canciones", mensaje);
        });
    }

    public void progresoDescarga(long recibidos, long totales) {
        SwingUtilities.invokeLater(() -> {
            if (!descargando || cancionActual == null) return;
            if (totales > 0) {
                int pct = (int) Math.min(100, recibidos * 100 / totales);
                barraDescarga.setIndeterminate(false);
                barraDescarga.setValue(pct);
                barraDescarga.setString(pct + " %");
                lblEstado.setText(String.format("Recibiendo \"%s\": %d KB de %d KB",
                        cancionActual.titulo(), recibidos / 1024, totales / 1024));
            } else {
                barraDescarga.setIndeterminate(true);
                lblEstado.setText(String.format("Recibiendo \"%s\": %d KB",
                        cancionActual.titulo(), recibidos / 1024));
            }
        });
    }

    public void descargaCompleta(Cancion c, File mp3) {
        SwingUtilities.invokeLater(() -> {
            if (!descargando) return;
            descargando = false;
            barraDescarga.setIndeterminate(false);
            barraDescarga.setValue(100);
            barraDescarga.setString("Completa");
            iniciarReproduccion(c, mp3);
            actualizarBotones();
        });
    }

    public void descargaFallida(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            if (!descargando) return;
            descargando = false;
            barraDescarga.setIndeterminate(false);
            barraDescarga.setValue(0);
            barraDescarga.setString("Error");
            lblAhoraSuena.setText("Selecciona una cancion");
            lblEstado.setText("Fallo la descarga.");
            actualizarBotones();
            mostrarError("No se pudo recibir la cancion", mensaje);
        });
    }

    // =====================================================================
    //  Construccion de la interfaz
    // =====================================================================

    private void construirInterfaz() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ----- Norte: busqueda -----
        JPanel norte = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        norte.add(new JLabel("Buscar:"));
        norte.add(txtBusqueda);
        norte.add(new JLabel("en"));
        norte.add(cmbCriterio);
        norte.add(btnActualizar);
        add(norte, BorderLayout.NORTH);

        // ----- Oeste: generos -----
        listaGeneros.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane spGeneros = new JScrollPane(listaGeneros);
        spGeneros.setBorder(BorderFactory.createTitledBorder("Generos"));
        spGeneros.setPreferredSize(new Dimension(170, 0));
        add(spGeneros, BorderLayout.WEST);

        // ----- Centro: tabla -----
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(24);
        tabla.setFillsViewportHeight(true);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(220);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(160);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(160);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(90);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(60);
        JScrollPane spTabla = new JScrollPane(tabla);
        spTabla.setBorder(BorderFactory.createTitledBorder("Canciones disponibles"));
        add(spTabla, BorderLayout.CENTER);

        // ----- Sur: reproductor -----
        JPanel sur = new JPanel();
        sur.setLayout(new BoxLayout(sur, BoxLayout.Y_AXIS));
        sur.setBorder(BorderFactory.createTitledBorder("Reproductor"));

        lblAhoraSuena.setFont(lblAhoraSuena.getFont().deriveFont(Font.BOLD, 14f));
        lblAhoraSuena.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(lblAhoraSuena);
        sur.add(Box.createVerticalStrut(4));

        JPanel filaTiempo = new JPanel(new BorderLayout(8, 0));
        filaTiempo.add(barraReproduccion, BorderLayout.CENTER);
        filaTiempo.add(lblTiempo, BorderLayout.EAST);
        filaTiempo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(filaTiempo);
        sur.add(Box.createVerticalStrut(6));

        JPanel filaBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filaBotones.add(btnReproducir);
        filaBotones.add(btnPausa);
        filaBotones.add(btnDetener);
        filaBotones.add(btnCancelar);
        filaBotones.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(filaBotones);
        sur.add(Box.createVerticalStrut(6));

        JPanel filaDescarga = new JPanel(new BorderLayout(8, 0));
        filaDescarga.add(new JLabel("Descarga:"), BorderLayout.WEST);
        barraDescarga.setStringPainted(true);
        filaDescarga.add(barraDescarga, BorderLayout.CENTER);
        filaDescarga.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(filaDescarga);
        sur.add(Box.createVerticalStrut(4));

        lblEstado.setAlignmentX(Component.LEFT_ALIGNMENT);
        sur.add(lblEstado);

        add(sur, BorderLayout.SOUTH);
    }

    private void conectarEventos() {
        txtBusqueda.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltros(); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltros(); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltros(); }
        });
        cmbCriterio.addActionListener(e -> aplicarFiltros());
        listaGeneros.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) aplicarFiltros();
        });

        btnActualizar.addActionListener(e -> {
            btnActualizar.setEnabled(false);
            lblEstado.setText("Solicitando la lista de canciones al servidor...");
            ClienteMusica.pedirLista();
        });

        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) solicitarSeleccionada();
            }
        });

        btnReproducir.addActionListener(e -> solicitarSeleccionada());
        btnPausa.addActionListener(e -> alternarPausa());
        btnDetener.addActionListener(e -> detenerReproduccion());
        btnCancelar.addActionListener(e -> cancelarDescarga());
    }

    // =====================================================================
    //  Busqueda / filtros
    // =====================================================================

    private void poblarGeneros() {
        String previo = listaGeneros.getSelectedValue();
        modeloGeneros.clear();
        modeloGeneros.addElement(TODOS_LOS_GENEROS);
        catalogo.stream()
                .map(Cancion::genero)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(modeloGeneros::addElement);
        int idx = previo == null ? 0 : modeloGeneros.indexOf(previo);
        listaGeneros.setSelectedIndex(Math.max(idx, 0));
    }

    private void aplicarFiltros() {
        final String texto = normalizar(txtBusqueda.getText());
        final String criterio = (String) cmbCriterio.getSelectedItem();
        final String genero = listaGeneros.getSelectedValue();

        visibles = catalogo.stream()
                .filter(c -> genero == null || genero.equals(TODOS_LOS_GENEROS)
                        || genero.equalsIgnoreCase(c.genero()))
                .filter(c -> texto.isEmpty() || coincide(c, texto, criterio))
                .toList();
        modeloTabla.setDatos(visibles);
        actualizarBotones();
    }

    private static boolean coincide(Cancion c, String texto, String criterio) {
        boolean nombre  = normalizar(c.titulo()).contains(texto);
        boolean artista = normalizar(c.artista()).contains(texto);
        boolean genero  = normalizar(c.genero()).contains(texto);
        return switch (criterio) {
            case "Nombre"  -> nombre;
            case "Artista" -> artista;
            case "Genero"  -> genero;
            default        -> nombre || artista || genero;
        };
    }

    /** Minusculas y sin acentos, para que "sabor a mi" encuentre "Sabor a Mi". */
    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase().trim();
    }

    // =====================================================================
    //  Pedir cancion al servidor y reproducir
    // =====================================================================

    private Cancion cancionSeleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) return null;
        return visibles.get(tabla.convertRowIndexToModel(fila));
    }

    private void solicitarSeleccionada() {
        Cancion c = cancionSeleccionada();
        if (c == null || descargando) return;

        detenerReproduccion();
        cancionActual = c;
        descargando = true;
        lblAhoraSuena.setText("Solicitando: " + c.titulo() + " - " + c.artista());
        lblEstado.setText("Solicitando \"" + c.titulo() + "\" al servidor...");
        barraDescarga.setValue(0);
        barraDescarga.setString(null);
        barraDescarga.setIndeterminate(true);
        actualizarBotones();

        ClienteMusica.pedirCancion(c);   // de aqui en adelante responde el hilo del cliente
    }

    private void cancelarDescarga() {
        if (!descargando) return;
        ClienteMusica.cancelar();
        descargando = false;
        barraDescarga.setIndeterminate(false);
        barraDescarga.setValue(0);
        barraDescarga.setString("Cancelada");
        lblAhoraSuena.setText("Descarga cancelada");
        lblEstado.setText("Descarga cancelada por el usuario.");
        actualizarBotones();
    }

    private void iniciarReproduccion(Cancion c, File mp3) {
        try {
            reproductor.reproducir(mp3, () -> SwingUtilities.invokeLater(this::alTerminarCancion));
            lblAhoraSuena.setText("Reproduciendo: " + c.titulo() + " - " + c.artista());
            lblEstado.setText("Cancion recibida. Reproduciendo.");
            temporizador.start();
        } catch (IOException e) {
            lblAhoraSuena.setText("Selecciona una cancion");
            mostrarError("No se pudo reproducir el archivo", e.getMessage());
        }
    }

    // =====================================================================
    //  Controles de reproduccion
    // =====================================================================

    private void alternarPausa() {
        try {
            switch (reproductor.estado()) {
                case REPRODUCIENDO -> {
                    reproductor.pausar();
                    lblEstado.setText("En pausa.");
                }
                case PAUSADO -> {
                    reproductor.reanudar();
                    lblEstado.setText("Reproduciendo.");
                }
                default -> { }
            }
        } catch (IOException e) {
            mostrarError("No se pudo reanudar la reproduccion", e.getMessage());
        }
        actualizarBotones();
    }

    private void detenerReproduccion() {
        reproductor.detener();
        temporizador.stop();
        barraReproduccion.setValue(0);
        lblTiempo.setText("00:00 / " + (cancionActual != null ? cancionActual.duracionFormateada() : "00:00"));
        actualizarBotones();
    }

    private void alTerminarCancion() {
        temporizador.stop();
        barraReproduccion.setValue(0);
        lblAhoraSuena.setText("Reproduccion finalizada");
        lblEstado.setText("La cancion termino.");
        actualizarBotones();
    }

    private void refrescarProgresoReproduccion() {
        if (cancionActual == null) return;
        long posSeg = reproductor.posicionMs() / 1000;
        int total = cancionActual.duracionSeg();
        if (total > 0) {
            barraReproduccion.setValue((int) Math.min(1000, posSeg * 1000L / total));
        }
        lblTiempo.setText(Cancion.formatearTiempo(posSeg) + " / " + cancionActual.duracionFormateada());
    }

    private void actualizarBotones() {
        ReproductorMP3.Estado est = reproductor.estado();
        boolean hayFila = tabla.getSelectedRow() >= 0;
        btnReproducir.setEnabled(hayFila && !descargando);
        btnPausa.setEnabled(est != ReproductorMP3.Estado.DETENIDO);
        btnPausa.setText(est == ReproductorMP3.Estado.PAUSADO ? "Reanudar" : "Pausar");
        btnDetener.setEnabled(est != ReproductorMP3.Estado.DETENIDO);
        btnCancelar.setEnabled(descargando);
    }

    private void mostrarError(String titulo, String detalle) {
        if (detalle == null || detalle.isBlank()) detalle = "Error desconocido";
        JOptionPane.showMessageDialog(this, detalle, titulo, JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  Modelo de la tabla
    // =====================================================================

    private static class ModeloCanciones extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Titulo", "Artista", "Album", "Genero", "Duracion"};
        private List<Cancion> datos = List.of();

        void setDatos(List<Cancion> nuevos) {
            this.datos = nuevos;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return datos.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int c) { return COLUMNAS[c]; }

        @Override public Object getValueAt(int fila, int col) {
            Cancion c = datos.get(fila);
            return switch (col) {
                case 0 -> c.titulo();
                case 1 -> c.artista();
                case 2 -> c.album();
                case 3 -> c.genero();
                default -> c.duracionFormateada();
            };
        }
    }
}
