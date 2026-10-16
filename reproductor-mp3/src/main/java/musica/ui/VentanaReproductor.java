package musica.ui;

import musica.audio.ReproductorMP3;
import musica.modelo.Cancion;
import musica.red.ServicioMusica;

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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Ventana principal del cliente: estilo reproductor de música.
 *
 *  - Arriba: búsqueda por nombre / artista / género.
 *  - Izquierda: filtro rápido por género.
 *  - Centro: tabla con las canciones que mandó el servidor.
 *  - Abajo: controles de reproducción y barra de progreso de la descarga.
 *
 * Sólo depende de {@link ServicioMusica}; no sabe nada de UDP.
 */
public class VentanaReproductor extends JFrame {

    private static final String TODOS_LOS_GENEROS = "Todos los géneros";

    private final ServicioMusica servicio;
    private final ReproductorMP3 reproductor = new ReproductorMP3();

    private List<Cancion> catalogo = List.of();
    private List<Cancion> visibles = List.of();
    private Cancion cancionActual;
    private boolean descargando;
    private SwingWorker<File, long[]> descarga;

    // --- componentes ---
    private final JTextField txtBusqueda = new JTextField(24);
    private final JComboBox<String> cmbCriterio =
            new JComboBox<>(new String[]{"Todo", "Nombre", "Artista", "Género"});
    private final JButton btnActualizar = new JButton("Actualizar lista");
    private final DefaultListModel<String> modeloGeneros = new DefaultListModel<>();
    private final JList<String> listaGeneros = new JList<>(modeloGeneros);
    private final ModeloCanciones modeloTabla = new ModeloCanciones();
    private final JTable tabla = new JTable(modeloTabla);

    private final JLabel lblAhoraSuena = new JLabel("Selecciona una canción");
    private final JProgressBar barraReproduccion = new JProgressBar(0, 1000);
    private final JLabel lblTiempo = new JLabel("00:00 / 00:00");
    private final JButton btnReproducir = new JButton("Reproducir");
    private final JButton btnPausa = new JButton("Pausar");
    private final JButton btnDetener = new JButton("Detener");
    private final JButton btnCancelar = new JButton("Cancelar descarga");
    private final JProgressBar barraDescarga = new JProgressBar(0, 100);
    private final JLabel lblEstado = new JLabel(" ");
    private final Timer temporizador = new Timer(250, e -> refrescarProgresoReproduccion());

    public VentanaReproductor(ServicioMusica servicio) {
        super("Reproductor MP3 - Práctica 2");
        this.servicio = servicio;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                reproductor.detener();
                if (descarga != null) descarga.cancel(true);
                servicio.cerrar();
            }
        });

        construirInterfaz();
        conectarEventos();
        actualizarBotones();

        setSize(900, 560);
        setMinimumSize(new Dimension(720, 420));
        setLocationRelativeTo(null);

        cargarCatalogo();
    }

    // =====================================================================
    //  Construcción de la interfaz
    // =====================================================================

    private void construirInterfaz() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ----- Norte: búsqueda -----
        JPanel norte = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        norte.add(new JLabel("Buscar:"));
        norte.add(txtBusqueda);
        norte.add(new JLabel("en"));
        norte.add(cmbCriterio);
        norte.add(btnActualizar);
        add(norte, BorderLayout.NORTH);

        // ----- Oeste: géneros -----
        listaGeneros.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane spGeneros = new JScrollPane(listaGeneros);
        spGeneros.setBorder(BorderFactory.createTitledBorder("Géneros"));
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
        // Búsqueda en vivo
        txtBusqueda.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { aplicarFiltros(); }
            public void removeUpdate(DocumentEvent e)  { aplicarFiltros(); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltros(); }
        });
        cmbCriterio.addActionListener(e -> aplicarFiltros());
        listaGeneros.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) aplicarFiltros();
        });
        btnActualizar.addActionListener(e -> cargarCatalogo());

        // Selección y doble clic en la tabla
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) solicitarYReproducirSeleccionada();
            }
        });

        // Controles
        btnReproducir.addActionListener(e -> solicitarYReproducirSeleccionada());
        btnPausa.addActionListener(e -> alternarPausa());
        btnDetener.addActionListener(e -> detenerReproduccion());
        btnCancelar.addActionListener(e -> { if (descarga != null) descarga.cancel(true); });
    }

    // =====================================================================
    //  Catálogo (lista de canciones del servidor)
    // =====================================================================

    private void cargarCatalogo() {
        btnActualizar.setEnabled(false);
        lblEstado.setText("Solicitando la lista de canciones al servidor...");

        new SwingWorker<List<Cancion>, Void>() {
            @Override protected List<Cancion> doInBackground() throws Exception {
                return servicio.obtenerCatalogo();
            }

            @Override protected void done() {
                try {
                    catalogo = get();
                    poblarGeneros();
                    aplicarFiltros();
                    lblEstado.setText(catalogo.size() + " canciones disponibles en el servidor.");
                } catch (ExecutionException e) {
                    lblEstado.setText("No se pudo obtener la lista de canciones.");
                    mostrarError("No se pudo obtener la lista del servidor", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    btnActualizar.setEnabled(true);
                }
            }
        }.execute();
    }

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

    // =====================================================================
    //  Búsqueda / filtros
    // =====================================================================

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
            case "Género"  -> genero;
            default        -> nombre || artista || genero;
        };
    }

    /** Minúsculas y sin acentos, para que "sabor a mi" encuentre "Sabor a Mí". */
    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase().trim();
    }

    // =====================================================================
    //  Solicitar canción al servidor + reproducir
    // =====================================================================

    private Cancion cancionSeleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) return null;
        return visibles.get(tabla.convertRowIndexToModel(fila));
    }

    private void solicitarYReproducirSeleccionada() {
        final Cancion c = cancionSeleccionada();
        if (c == null || descargando) return;

        detenerReproduccion();
        cancionActual = c;
        descargando = true;
        lblAhoraSuena.setText("Solicitando: " + c.titulo() + " — " + c.artista());
        lblEstado.setText("Solicitando \"" + c.titulo() + "\" al servidor...");
        barraDescarga.setValue(0);
        barraDescarga.setString(null);
        barraDescarga.setIndeterminate(true);
        actualizarBotones();

        descarga = new SwingWorker<File, long[]>() {
            @Override protected File doInBackground() throws Exception {
                // Aquí entra el trabajo del compañero: UDP + Ventana Deslizante + Retroceder-N.
                return servicio.descargarCancion(c, (rec, tot) -> publish(new long[]{rec, tot}));
            }

            @Override protected void process(List<long[]> trozos) {
                long[] ultimo = trozos.get(trozos.size() - 1);
                mostrarProgresoDescarga(ultimo[0], ultimo[1]);
            }

            @Override protected void done() {
                descargando = false;
                barraDescarga.setIndeterminate(false);
                try {
                    File mp3 = get();
                    barraDescarga.setValue(100);
                    barraDescarga.setString("Completa");
                    iniciarReproduccion(c, mp3);
                } catch (CancellationException e) {
                    barraDescarga.setValue(0);
                    barraDescarga.setString("Cancelada");
                    lblAhoraSuena.setText("Descarga cancelada");
                    lblEstado.setText("Descarga cancelada por el usuario.");
                } catch (ExecutionException e) {
                    barraDescarga.setValue(0);
                    barraDescarga.setString("Error");
                    lblAhoraSuena.setText("Selecciona una canción");
                    lblEstado.setText("Falló la descarga de \"" + c.titulo() + "\".");
                    mostrarError("No se pudo recibir la canción", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    actualizarBotones();
                }
            }
        };
        descarga.execute();
    }

    private void mostrarProgresoDescarga(long recibidos, long totales) {
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
    }

    private void iniciarReproduccion(Cancion c, File mp3) {
        try {
            reproductor.reproducir(mp3, () -> SwingUtilities.invokeLater(this::alTerminarCancion));
            lblAhoraSuena.setText("Reproduciendo: " + c.titulo() + " — " + c.artista());
            lblEstado.setText("Canción recibida. Reproduciendo.");
            temporizador.start();
        } catch (IOException e) {
            lblAhoraSuena.setText("Selecciona una canción");
            mostrarError("No se pudo reproducir el archivo", e);
        }
    }

    // =====================================================================
    //  Controles de reproducción
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
            mostrarError("No se pudo reanudar la reproducción", e);
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
        lblAhoraSuena.setText("Reproducción finalizada");
        lblEstado.setText("La canción terminó.");
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
        btnPausa.setText(reproductor.estado() == ReproductorMP3.Estado.PAUSADO ? "Reanudar" : "Pausar");
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

    private void mostrarError(String titulo, Throwable causa) {
        String detalle = causa == null ? "Error desconocido" : causa.getMessage();
        if (detalle == null || detalle.isBlank()) detalle = causa.getClass().getSimpleName();
        JOptionPane.showMessageDialog(this, detalle, titulo, JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  Modelo de la tabla
    // =====================================================================

    private static class ModeloCanciones extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Título", "Artista", "Álbum", "Género", "Duración"};
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
