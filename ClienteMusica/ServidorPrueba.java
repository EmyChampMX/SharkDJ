import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * SERVIDOR DE PRUEBA (solo para probar el cliente; el real lo hace tu companero).
 * Sirve de referencia del lado emisor de Ventana Deslizante + Retroceder-N.
 *
 *   java ServidorPrueba [puerto] [carpeta] [ventana] [probPerdida]
 *   java ServidorPrueba 5000 mp3_servidor 8 0.1
 *
 * Genero = nombre de la subcarpeta (mp3_servidor/Rock/cancion.mp3). Si el archivo trae ID3v1 se usa
 * su titulo/artista/album; si no, se usa el nombre del archivo.
 */
public class ServidorPrueba {

    static DatagramSocket socket;
    static List<File> archivos = new ArrayList<>();
    static List<Cancion> catalogo = new ArrayList<>();
    static int ventana = 8;
    static double probPerdida = 0.0;
    static final int TIMEOUT_ACK_MS = 300;

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        File carpeta = new File(args.length > 1 ? args[1] : "mp3_servidor");
        ventana = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        probPerdida = args.length > 3 ? Double.parseDouble(args[3]) : 0.0;

        cargarArchivos(carpeta);
        socket = new DatagramSocket(puerto);
        System.out.println("Servidor de prueba en el puerto " + puerto + ", " + catalogo.size()
                + " canciones, ventana=" + ventana + ", perdida=" + probPerdida);

        for (;;) {
            socket.setSoTimeout(0);
            byte[] b = new byte[Protocolo.TAM_BUFFER];
            DatagramPacket p = new DatagramPacket(b, b.length);
            socket.receive(p);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData(), 0, p.getLength()));
            int tipo = dis.readInt();

            if (tipo == Protocolo.SOLICITUD_LISTA) {
                enviarLista(p.getAddress(), p.getPort());
            } else if (tipo == Protocolo.SOLICITUD_CANCION) {
                enviarCancion(dis.readUTF(), p.getAddress(), p.getPort());
            }
        }
    }

    // ------------------------------------------------------------------ catalogo

    static void cargarArchivos(File carpeta) throws IOException {
        if (!carpeta.isDirectory()) return;
        Path raiz = carpeta.toPath();
        List<Path> mp3s;
        try (Stream<Path> s = Files.walk(raiz)) {
            mp3s = s.filter(x -> x.toString().toLowerCase().endsWith(".mp3")).sorted().collect(Collectors.toList());
        }
        for (Path p : mp3s) {
            File f = p.toFile();
            String id = raiz.relativize(p).toString().replace('\\', '/');
            Path padre = raiz.relativize(p).getParent();
            String genero = padre == null ? "Sin clasificar" : padre.getFileName().toString();
            String nombre = f.getName().substring(0, f.getName().length() - 4);
            String titulo = nombre, artista = "Desconocido", album = "";

            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                if (raf.length() >= 128) {
                    byte[] tag = new byte[128];
                    raf.seek(raf.length() - 128);
                    raf.readFully(tag);
                    if (tag[0] == 'T' && tag[1] == 'A' && tag[2] == 'G') {
                        String t = texto(tag, 3, 30), a = texto(tag, 33, 30), al = texto(tag, 63, 30);
                        if (!t.isEmpty()) titulo = t;
                        if (!a.isEmpty()) artista = a;
                        album = al;
                    }
                }
            }
            int duracion = (int) (f.length() * 8 / 128000);   // estimada a 128 kbps
            archivos.add(f);
            catalogo.add(new Cancion(id, titulo, artista, album, genero, duracion));
        }
    }

    static String texto(byte[] b, int ini, int len) {
        return new String(b, ini, len, java.nio.charset.StandardCharsets.ISO_8859_1).replace('\0', ' ').trim();
    }

    static void enviarLista(InetAddress dir, int pto) throws Exception {
        System.out.println("Lista solicitada por " + dir + ":" + pto);
        if (catalogo.isEmpty()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(Protocolo.INFO_CANCION);
            dos.writeInt(-1);
            dos.writeInt(0);
            for (int i = 0; i < 5; i++) dos.writeUTF("");
            dos.writeInt(0);
            enviar(baos.toByteArray(), dir, pto);
            return;
        }
        for (int i = 0; i < catalogo.size(); i++) {
            Cancion c = catalogo.get(i);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(Protocolo.INFO_CANCION);
            dos.writeInt(i);
            dos.writeInt(catalogo.size());
            dos.writeUTF(c.id());
            dos.writeUTF(c.titulo());
            dos.writeUTF(c.artista());
            dos.writeUTF(c.album());
            dos.writeUTF(c.genero());
            dos.writeInt(c.duracionSeg());
            dos.flush();
            enviar(baos.toByteArray(), dir, pto);
            Thread.sleep(2);
        }
    }

    // ------------------------------------------------------------------ Retroceder-N (emisor)

    static void enviarCancion(String id, InetAddress dir, int pto) throws Exception {
        int idx = -1;
        for (int i = 0; i < catalogo.size(); i++) if (catalogo.get(i).id().equals(id)) idx = i;
        if (idx < 0) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(Protocolo.ERROR);
            dos.writeUTF("No existe la cancion '" + id + "'");
            dos.flush();
            enviar(baos.toByteArray(), dir, pto);
            return;
        }

        byte[] datos = Files.readAllBytes(archivos.get(idx).toPath());
        int total = (datos.length + Protocolo.TAM_DATOS - 1) / Protocolo.TAM_DATOS;
        System.out.println("Enviando '" + id + "' (" + datos.length + " bytes, " + total + " paquetes)");

        int base = 0, siguiente = 0, timeouts = 0, retransmisiones = 0;
        socket.setSoTimeout(TIMEOUT_ACK_MS);

        while (base < total) {
            while (siguiente < base + ventana && siguiente < total) {
                enviarDatos(siguiente, total, datos, dir, pto);
                siguiente++;
            }
            try {
                byte[] b = new byte[Protocolo.TAM_BUFFER];
                DatagramPacket p = new DatagramPacket(b, b.length);
                socket.receive(p);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData(), 0, p.getLength()));
                int tipo = dis.readInt();
                if (tipo == Protocolo.ACK) {
                    int ack = dis.readInt();
                    if (ack > base) {
                        base = ack;
                        timeouts = 0;
                        if (siguiente < base) siguiente = base;
                    }
                } else if (tipo == Protocolo.CANCELAR) {
                    System.out.println("Transferencia cancelada por el cliente");
                    return;
                }
            } catch (SocketTimeoutException e) {
                if (++timeouts > 15) {
                    System.out.println("El cliente dejo de responder; abandono la transferencia");
                    return;
                }
                System.out.println("Timeout: retransmito desde el paquete " + base + " (ventana " + ventana + ")");
                retransmisiones++;
                siguiente = base;
            }
        }
        System.out.println("Transferencia completa (" + retransmisiones + " retransmisiones de ventana)");
    }

    static void enviarDatos(int seq, int total, byte[] datos, InetAddress dir, int pto) throws Exception {
        if (Math.random() < probPerdida) {
            System.out.println("[simulado] el servidor pierde el paquete " + seq);
            return;
        }
        int ini = seq * Protocolo.TAM_DATOS;
        int len = Math.min(Protocolo.TAM_DATOS, datos.length - ini);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(Protocolo.DATOS);
        dos.writeInt(seq);
        dos.writeInt(total);
        dos.writeLong(datos.length);
        dos.writeInt(len);
        dos.write(datos, ini, len);
        dos.flush();
        enviar(baos.toByteArray(), dir, pto);
    }

    static void enviar(byte[] b, InetAddress dir, int pto) throws Exception {
        socket.send(new DatagramPacket(b, b.length, dir, pto));
    }
}
