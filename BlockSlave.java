import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Classe BlockSlave qui gère le téléchargement de blocs de fichiers pour un client.
 */
public class BlockSlave implements Runnable {
    private final Socket socket;
    private final File[] files;
    private final int tailleBloc = 1024; // Taille d'un bloc de téléchargement (en octets)
    private static final Logger logger = Log.setup("BlocSlave", "bloc_slave.log");

    /**
     * Constructeur de la classe BlockSlave.
     * @param socket socket de connexion avec le blocdownloader
     * @param files tableau de fichiers disponibles sur le serveur
     */
    public BlockSlave(Socket socket, File[] files) {
        this.socket = socket;
        this.files = files;
    }

    /**
     * Gère la communication avec le blocdownloader.
     * Lit la commande du blocdownloader et traite les demandes de téléchargement de blocs.
     */
    @Override
    public void run() {
        // Logique de traitement pour REQUIRE <fichier> <bloc>
        try (
            DataOutputStream outputClient = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputClient = new DataInputStream(socket.getInputStream())
        ) {
            //Interruption thread
            // Lire la demande du client
            String commande = inputClient.readUTF();
            logger.info(commande + " : " + socket.getInetAddress().getHostAddress());

            // Si la commande est de type "REQUIRE", on gère le téléchargement d'un bloc
            if (commande.startsWith("REQUIRE")) {
                handleDownload(commande, outputClient);
                return;
            }

        } catch (IOException e) {
            logger.warning("Erreur Slave : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gère le téléchargement d'un bloc de fichier demandé par un blocdownloader.
     * @param commande Commande de téléchargement contenant l'index du fichier et l'index du bloc
     * @param outputClient Flux de sortie pour envoyer le bloc au blocdownloader
     * @throws IOException
     */
    private void handleDownload(String commande, DataOutputStream outputClient) throws IOException {
        // Extraire l'index du fichier et l'index du bloc à télécharger
        logger.info("Traitement de la commande de téléchargement.");
        String[] parts = commande.split(" ");
        int fileIndex = Integer.parseInt(parts[1]);
        int blockIndex = Integer.parseInt(parts[2]);
        logger.info(commande);

        File fichier = files[fileIndex];
        long fileSize = fichier.length();
        long startByte = blockIndex * tailleBloc;
        long endByte = Math.min(startByte + tailleBloc, fileSize);
        logger.info("Fichier : " + fichier.getName() + ", Bloc : " + blockIndex + ", Taille : " + (endByte - startByte) + " octets");

        // Envoi du nom et de la taille du bloc
        outputClient.writeInt((int)(endByte - startByte)); // Taille du bloc

        // Lecture du fichier et envoi du bloc au client
        try (FileInputStream fis = new FileInputStream(fichier)) {
            byte[] buffer = new byte[tailleBloc];
            fis.skip(startByte); // Sauter jusqu'à l'index du bloc
            int bytesRead = fis.read(buffer, 0, (int)(endByte - startByte));
            outputClient.write(buffer, 0, bytesRead); // Envoi du bloc au client
            System.out.println("Bloc " + blockIndex + " du fichier " + fichier.getName() + " envoyé.");
        }
    }
}
