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
        try (
            DataOutputStream outputClient = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputClient = new DataInputStream(socket.getInputStream())
        ) {
            String listCommande = inputClient.readUTF();
            logger.info(listCommande + " : " + socket.getInetAddress().getHostAddress());

            if ("LIST".equals(listCommande)) {
                outputClient.writeInt(1);  // Confirmation fictive
                outputClient.flush();
                logger.info("Confirmation de vie envoyée au client avant LIST.");
                sendFileList(outputClient);
            }

            String hashCommande = inputClient.readUTF();
            if (hashCommande.startsWith("HASH")) {
                handleFileHash(inputClient, outputClient, hashCommande);
            }

        } catch (IOException e) {
            logger.warning("Déconnexion brutale de " + socket.getInetAddress() + " : " + e.getMessage());
            System.out.println("Connexion interrompue avant la fin.");
        }
    }

    private void sendFileList(DataOutputStream outputClient) throws IOException {
        logger.info("Envoi de la liste des fichiers disponibles au client.");
        outputClient.writeInt(files.length);
        for (File file : files) {
            if (file.isFile()) {
                outputClient.writeUTF(file.getName());
                outputClient.writeLong(file.length());
                logger.info(file.getName() + " : " + file.length() + " octets");
            }
        }
    }

    private void handleFileHash(DataInputStream inputClient, DataOutputStream outputClient, String commande) throws IOException {
        logger.info("Traitement de la commande de hash.");
        int index = Integer.parseInt(commande.split(" ")[1]);
        File fichier = files[index];

        try {
            String hashServeur = bytesToHex(Digest.md5(fichier.getPath()));
            String hashClient = inputClient.readUTF();
            logger.info("hashServ : " + hashServeur);
            logger.info("hashCli : " + hashClient);

            if (hashServeur.equals(hashClient)) {
                System.out.println("Le fichier a bien été téléchargé et vérifié.");
                String clientIp = socket.getInetAddress().getHostAddress();
                synchronized (trustedClients) {
                    trustedClients.add(clientIp);
                    logger.info("Ajouté à la liste des clients de confiance : " + clientIp);
                }
                outputClient.writeUTF("OK");
            } else {
                System.out.println("Le fichier a été corrompu pendant le transfert.");
                logger.warning("Le fichier a été corrompu pendant le transfert.");
                outputClient.writeUTF("ERROR");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
