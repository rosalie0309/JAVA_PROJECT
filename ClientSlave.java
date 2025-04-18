import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class ClientSlave implements Runnable {
    private final Socket socket;
    private final File[] files;
    private final CopyOnWriteArrayList<String> trustedClients;
    private static final Logger logger = Log.setup("ClientSlave", "client_slave.log");


    public ClientSlave(Socket socket, File[] files, CopyOnWriteArrayList<String> trustedClients) {
        this.socket = socket;
        this.files = files;
        this.trustedClients = trustedClients;
    }

    @Override
    public void run() {
        // Logique de traitement pour CLIENT_MAIN
        // LIST, HASH, etc.
        try (
            DataOutputStream outputClient = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputClient = new DataInputStream(socket.getInputStream())
        ) {
            String listCommande = inputClient.readUTF();
            logger.info(listCommande + " : " + socket.getInetAddress().getHostAddress());
            if ("LIST".equals(listCommande)) {
                // Envoyer la liste des fichiers disponibles au client
                sendFileList(outputClient);
            }

            String hashCommande = inputClient.readUTF();
            if (hashCommande.startsWith("HASH")) {
                // Si la commande est de type "HASH", on gère la vérification du hash
                handleFileHash(inputClient, outputClient, hashCommande);
            } 

        } catch(Exception e) {
            e.printStackTrace();
            logger.warning("Erreur dans ClientSlave : " + e.getMessage());
        }
        
    }

    private void sendFileList(DataOutputStream outputClient) throws IOException {
        // Envoyer la liste des fichiers disponibles au client
        logger.info("Envoi de la liste des fichiers disponibles au client.");
        outputClient.writeInt(files.length);  // Envoi du nombre de fichiers
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                outputClient.writeUTF(files[i].getName()); // Nom du fichier
                outputClient.writeLong(files[i].length()); // Taille du fichier
                logger.info(files[i].getName() + " : " + files[i].length() + " octets");
            }
        }
    }

    private void handleFileHash(DataInputStream inputClient, DataOutputStream outputClient, String commande) throws IOException {
        // Recevoir et traiter le hash envoyé par le client
        logger.info("Traitement de la commande de hash.");
        String fileIndex = commande.split(" ")[1];  // Extraire l'index du fichier
        logger.info(commande);
        int index = Integer.parseInt(fileIndex);
        File fichier = files[index];

        String hashServeur;
        try {
            hashServeur = bytesToHex(Digest.md5(fichier.getPath()));
            logger.info("hashServ : " + hashServeur);
            String hashClient = inputClient.readUTF(); // Hash reçu du client
            logger.info("hashCli : " + hashClient);

        // Comparer les hashes
        if (hashServeur.equals(hashClient)) {
            System.out.println("Le fichier a bien été téléchargé et vérifié.");
            // Ajout du client à la liste des clients de confiance
            String clientInfo = socket.getInetAddress().getHostAddress();
            synchronized (trustedClients) {
                trustedClients.add(clientInfo);
                logger.info("Ajouté à la liste des clients de confiance : " + clientInfo);
                System.out.println("Ajouté à la liste des clients de confiance : " + clientInfo);
            }            
            outputClient.writeUTF("OK");
        } else {
            System.out.println("Le fichier a été corrompu pendant le transfert.");
            logger.warning("Le fichier a été corrompu pendant le transfert.");
            outputClient.writeUTF("ERROR");
        }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
