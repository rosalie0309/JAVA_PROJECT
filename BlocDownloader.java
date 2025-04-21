import java.io.*;
import java.net.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class BlocDownloader implements Callable<byte[]> {
    private final String serverAddress;
    private final int serverPort;
    private final int fileIndex;
    private final int blocIndex;
    private static final Logger logger = Log.setup("BlocDownloader", "blocdownloader.log");

    public BlocDownloader(String serverAddress, int serverPort, int fileIndex, int blocIndex) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.fileIndex = fileIndex;
        this.blocIndex = blocIndex;
    }

    @Override
    public byte[] call() {
        try (
            Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            out.writeUTF("BLOCK_DOWNLOAD");
            out.flush();


            out.writeUTF("REQUIRE " + fileIndex + " " + blocIndex);
            logger.info("Téléchargement du bloc " + blocIndex + " du fichier " + fileIndex + " depuis le serveur " + serverAddress + ":" + serverPort);
            int tailleBloc = in.readInt();
            logger.info("Taille du bloc " + blocIndex + " : " + tailleBloc + " octets");
            byte[] buffer = new byte[tailleBloc];
            in.readFully(buffer);
            //Thread.sleep(5000);
            return buffer;
        } catch (Exception e) {
            System.err.println("Erreur lors du téléchargement du bloc " + blocIndex);
            logger.warning(serverAddress + ":" + serverPort + " - Erreur lors du téléchargement du bloc " + blocIndex + ": " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
    }
}
