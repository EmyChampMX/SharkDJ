import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Cliente del reproductor MP3 (misma estructura que ClienteBatalla):
 * un hilo que escucha el DatagramSocket y métodos estáticos que envían.
 *
 * Lado RECEPTOR de Ventana Deslizante + Retroceder-N:
 *   - sólo acepta el paquete cuyo seq == esperado
 *   - cualquier otro (adelantado o repetido) se descarta
 *   - por cada DATOS que llega responde ACK(esperado)  [acumulativo]
 */
public class ClienteMusica extends Thread {

    static DatagramSocket cl;
    static InetAddress dirServidor;
    static int ptoServidor;

    static VentanaConexion ventanaConexion;
    static VentanaReproductor ventanaReproductor;
    static boolean reproductorAbierto = false;

    // ---- catálogo ----
    static Cancion[] catalogoParcial;
    static int catalogoRecibidas;
    static boolean esperandoLista;
    static List<Cancion> ultimoCatalogo = new ArrayList<>();

    // ---- descarga de una canción ----
    static Cancion cancionPedida;
    static boolean recibiendo;       // hay una descarga en curso
    static boolean terminada;        // la última descarga ya llegó completa (para re-ACK)
    static int esperado;             // siguiente seq que necesito
    static int totalPaquetes;
    static long tamArchivo;
    static long bytesRecibidos;
    static File archivoTemporal;
    static FileOutputStream salida;

    // ---- temporizador de reintentos ----
    static long ultimaActividad;
    static int reintentos;

    public static void main(String[] args){
        try{
            // opcional: java ClienteMusica 0.2   -> simula 20% de paquetes DATOS perdidos
            if(args.length > 0){
                Protocolo.PROB_PERDIDA = Double.parseDouble(args[0]);
            }
            cl = new DatagramSocket();
            cl.setSoTimeout(Protocolo.TIMEOUT_MS);

            if(hayGUI()){
                try{
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }catch(Exception e){ }
                SwingUtilities.invokeLater(() -> {
                    ventanaConexion = new VentanaConexion();
                    ventanaConexion.setVisible(true);
                });
            }

            ClienteMusica hilo = new ClienteMusica();
            hilo.start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void run(){
        for(;;){
            try{
                byte[] b = new byte[Protocolo.TAM_BUFFER];
                DatagramPacket p = new DatagramPacket(b, b.length);
                cl.receive(p);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData(), 0, p.getLength()));
                int tipo = dis.readInt();

                if(tipo == Protocolo.INFO_CANCION){

                    int indice = dis.readInt();
                    int total = dis.readInt();
                    String id = dis.readUTF();
                    String titulo = dis.readUTF();
                    String artista = dis.readUTF();
                    String album = dis.readUTF();
                    String genero = dis.readUTF();
                    int duracion = dis.readInt();
                    infoCancion(indice, total, new Cancion(id, titulo, artista, album, genero, duracion));

                }else if(tipo == Protocolo.DATOS){

                    int seq = dis.readInt();
                    int total = dis.readInt();
                    long tam = dis.readLong();
                    int len = dis.readInt();
                    byte[] datos = new byte[len];
                    dis.readFully(datos);
                    paqueteDatos(seq, total, tam, datos);

                }else if(tipo == Protocolo.ERROR){

                    errorServidor(dis.readUTF());
                }

            }catch(SocketTimeoutException e){
                revisarTiempos();
            }catch(SocketException e){
                break;   // el socket se cerró (cerró la ventana)
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    // =====================================================================
    //  Lo que llama la interfaz
    // =====================================================================

    static void conectar(String ip, int puerto){
        try{
            dirServidor = InetAddress.getByName(ip);
            ptoServidor = puerto;
            ventanaConexion.setEstado("Solicitando la lista de canciones a "+dirServidor+":"+ptoServidor+"...");
            pedirLista();
        }catch(Exception e){
            e.printStackTrace();
            ventanaConexion.error("No se pudo conectar: "+e.getMessage());
        }
    }

    static synchronized void pedirLista(){
        try{
            catalogoParcial = null;
            catalogoRecibidas = 0;
            esperandoLista = true;
            ultimaActividad = System.currentTimeMillis();
            reintentos = 0;
            enviarSimple(Protocolo.SOLICITUD_LISTA);
        }catch(Exception e){
            esperandoLista = false;
            e.printStackTrace();
            avisarFalloLista("No se pudo enviar la solicitud: "+e.getMessage());
        }
    }

    static synchronized void pedirCancion(Cancion c){
        try{
            limpiarDescarga();
            cancionPedida = c;
            archivoTemporal = Files.createTempFile("cliente_", ".mp3").toFile();
            archivoTemporal.deleteOnExit();
            salida = new FileOutputStream(archivoTemporal);
            recibiendo = true;
            terminada = false;
            esperado = 0;
            totalPaquetes = -1;
            tamArchivo = -1;
            bytesRecibidos = 0;
            ultimaActividad = System.currentTimeMillis();
            reintentos = 0;
            enviarPedidoCancion();
            System.out.println("Solicitud de \""+c.titulo()+"\" enviada");
        }catch(Exception e){
            recibiendo = false;
            e.printStackTrace();
            avisarFallo("No se pudo solicitar la canción: "+e.getMessage());
        }
    }

    static synchronized void cancelar(){
        if(!recibiendo) return;
        try{
            enviarSimple(Protocolo.CANCELAR);
        }catch(Exception e){
            e.printStackTrace();
        }
        limpiarDescarga();
    }

    static void cerrar(){
        cancelar();
        if(cl != null) cl.close();
    }

    // =====================================================================
    //  Catálogo
    // =====================================================================

    static synchronized void infoCancion(int indice, int total, Cancion c){
        if(!esperandoLista) return;   // duplicado de una lista que ya completé

        ultimaActividad = System.currentTimeMillis();
        reintentos = 0;

        if(total <= 0){
            listaCompleta(new ArrayList<>());
            return;
        }
        if(catalogoParcial == null || catalogoParcial.length != total){
            catalogoParcial = new Cancion[total];
            catalogoRecibidas = 0;
        }
        if(indice >= 0 && indice < total && catalogoParcial[indice] == null){
            catalogoParcial[indice] = c;
            catalogoRecibidas++;
        }
        if(catalogoRecibidas == total){
            listaCompleta(new ArrayList<>(Arrays.asList(catalogoParcial)));
        }
    }

    static void listaCompleta(List<Cancion> lista){
        esperandoLista = false;
        ultimoCatalogo = lista;
        System.out.println("Catálogo recibido: "+lista.size()+" canciones");
        if(!hayGUI()) return;

        if(!reproductorAbierto){
            reproductorAbierto = true;
            SwingUtilities.invokeLater(() -> {
                ventanaConexion.dispose();
                ventanaReproductor = new VentanaReproductor(lista);
                ventanaReproductor.setVisible(true);
            });
        }else if(ventanaReproductor != null){
            ventanaReproductor.mostrarCatalogo(lista);
        }
    }

    // =====================================================================
    //  Receptor Retroceder-N
    // =====================================================================

    static synchronized void paqueteDatos(int seq, int total, long tam, byte[] datos) throws Exception {

        if(!recibiendo){
            // Descarga ya terminada: seguramente mi último ACK se perdió, lo repito.
            if(terminada) enviarAck(esperado);
            return;
        }

        if(Math.random() < Protocolo.PROB_PERDIDA){
            System.out.println("[simulado] se pierde el paquete "+seq);
            return;
        }

        ultimaActividad = System.currentTimeMillis();
        reintentos = 0;

        if(totalPaquetes < 0){
            totalPaquetes = total;
            tamArchivo = tam;
        }

        if(seq == esperado){
            salida.write(datos);
            bytesRecibidos += datos.length;
            esperado++;
            if(esperado % 16 == 0 || esperado == totalPaquetes){
                avisarProgreso(bytesRecibidos, tamArchivo);
            }
        }else{
            System.out.println("Paquete "+seq+" fuera de orden (esperaba "+esperado+"): se descarta");
        }

        enviarAck(esperado);

        if(esperado == totalPaquetes){
            terminarDescarga();
        }
    }

    static void terminarDescarga() throws Exception {
        salida.close();
        salida = null;
        recibiendo = false;
        terminada = true;

        if(archivoTemporal.length() != tamArchivo){
            avisarFallo("El archivo llegó incompleto ("+archivoTemporal.length()+" de "+tamArchivo+" bytes)");
            return;
        }
        System.out.println("Canción recibida completa: "+archivoTemporal);
        avisarCompleta(cancionPedida, archivoTemporal);
    }

    static synchronized void errorServidor(String mensaje){
        System.out.println("El servidor respondió con error: "+mensaje);
        if(esperandoLista){
            esperandoLista = false;
            avisarFalloLista(mensaje);
        }else if(recibiendo){
            limpiarDescarga();
            avisarFallo(mensaje);
        }
    }

    // =====================================================================
    //  Reintentos por tiempo
    // =====================================================================

    static synchronized void revisarTiempos(){
        long ahora = System.currentTimeMillis();
        if(ahora - ultimaActividad < Protocolo.TIMEOUT_MS) return;
        try{
            if(esperandoLista){
                if(++reintentos > Protocolo.MAX_REINTENTOS){
                    esperandoLista = false;
                    avisarFalloLista("El servidor no responde. Revisa la IP y el puerto.");
                }else{
                    System.out.println("Sin respuesta: reenvío la solicitud de lista ("+reintentos+")");
                    ultimaActividad = ahora;
                    enviarSimple(Protocolo.SOLICITUD_LISTA);
                }
            }else if(recibiendo){
                if(++reintentos > Protocolo.MAX_REINTENTOS){
                    limpiarDescarga();
                    avisarFallo("Se perdió la comunicación con el servidor.");
                }else{
                    ultimaActividad = ahora;
                    if(totalPaquetes < 0){
                        System.out.println("Sin respuesta: reenvío la solicitud de canción ("+reintentos+")");
                        enviarPedidoCancion();
                    }else{
                        System.out.println("Silencio del servidor: repito ACK("+esperado+")");
                        enviarAck(esperado);
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // =====================================================================
    //  Envío
    // =====================================================================

    static void enviarSimple(int tipo) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(tipo);
        dos.flush();
        enviar(baos.toByteArray());
    }

    static void enviarPedidoCancion() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(Protocolo.SOLICITUD_CANCION);
        dos.writeUTF(cancionPedida.id());
        dos.flush();
        enviar(baos.toByteArray());
    }

    static void enviarAck(int siguienteEsperado) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(Protocolo.ACK);
        dos.writeInt(siguienteEsperado);
        dos.flush();
        enviar(baos.toByteArray());
    }

    static void enviar(byte[] b) throws Exception {
        DatagramPacket p = new DatagramPacket(b, b.length, dirServidor, ptoServidor);
        cl.send(p);
    }

    // =====================================================================
    //  Auxiliares
    // =====================================================================

    /** Cierra el archivo temporal de una descarga a medias y la deja inactiva. */
    static void limpiarDescarga(){
        recibiendo = false;
        terminada = false;
        try{
            if(salida != null){
                salida.close();
                salida = null;
                if(archivoTemporal != null) archivoTemporal.delete();
            }
        }catch(Exception e){ }
    }

    /** En modo headless (pruebas sin ventanas) sólo se imprime en consola. */
    static boolean hayGUI(){
        return !GraphicsEnvironment.isHeadless();
    }

    static void avisarProgreso(long rec, long tot){
        if(hayGUI() && ventanaReproductor != null) ventanaReproductor.progresoDescarga(rec, tot);
    }

    static void avisarCompleta(Cancion c, File mp3){
        if(hayGUI() && ventanaReproductor != null) ventanaReproductor.descargaCompleta(c, mp3);
    }

    static void avisarFallo(String mensaje){
        System.out.println("Fallo: "+mensaje);
        if(hayGUI() && ventanaReproductor != null) ventanaReproductor.descargaFallida(mensaje);
    }

    static void avisarFalloLista(String mensaje){
        System.out.println("Fallo: "+mensaje);
        if(!hayGUI()) return;
        if(reproductorAbierto && ventanaReproductor != null) ventanaReproductor.catalogoFallo(mensaje);
        else if(ventanaConexion != null) ventanaConexion.error(mensaje);
    }
}
