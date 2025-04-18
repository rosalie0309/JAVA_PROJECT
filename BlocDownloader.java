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
        if (Thread.currentThread().isInterrupted()) {
            logger.warning("Thread interrompu avant de commencer le téléchargement du bloc " + blocIndex);
            return new byte[0];
        }
    
        try (
            Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            socket.setSoTimeout(2000); // 2 secondes max de lecture par bloc
    
            out.writeUTF("BLOCK_DOWNLOAD");
            out.flush();
    
            if (Thread.currentThread().isInterrupted()) {
                logger.warning("Thread interrompu juste avant REQUIRE " + blocIndex);
                return new byte[0];
            }
    
            out.writeUTF("REQUIRE " + fileIndex + " " + blocIndex);
            logger.info("Téléchargement du bloc " + blocIndex + " demandé.");
    
            int tailleBloc = in.readInt();
            // On vérifie la taille avant d'allouer la buffer 
            if (tailleBloc == -1) {
                logger.warning("Serveur a rejeté le téléchargement du bloc " + blocIndex);
                return new byte[0];
            }
            
            byte[] buffer = new byte[tailleBloc];
    
            in.readFully(buffer); // peut échouer si coupure
    
            if (Thread.currentThread().isInterrupted()) {
                logger.warning("Interruption détectée après réception du bloc " + blocIndex);
                return new byte[0];
            }
    
            return buffer;
    
        } catch (SocketTimeoutException e) {
            logger.warning("Timeout bloc " + blocIndex);
        } catch (SocketException e) {
            logger.warning("Socket fermée bloc " + blocIndex + " : " + e.getMessage());
        } catch (IOException e) {
            logger.warning("Erreur IO bloc " + blocIndex + " : " + e.getMessage());
        }
    
        return new byte[0];
    }
}
