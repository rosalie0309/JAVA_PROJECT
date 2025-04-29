import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Classe ClientSlave qui gère la communication avec un client.
 */
public class ClientSlave implements Runnable {
    private final Socket socket;
    private final File[] files;
    private final ArrayList<String> trustedClients;
    private static final Logger logger = Log.setup("ClientSlave", "client_slave.log");
    

    /**
     * Constructeur de la classe ClientSlave.
     * @param socket socket de connexion avec le client
     * @param files tableau de fichiers disponibles sur le serveur
     * @param trustedClients liste des clients de confiance
     */
    public ClientSlave(Socket socket, File[] files, ArrayList<String> trustedClients) {
        this.socket = socket;
        this.files = files;
        this.trustedClients = trustedClients;
    }

    /**
     * Méthode run qui gère la communication avec le client.
     */
    @Override
    public void run() {
        try (
        DataOutputStream outputClient = new DataOutputStream(socket.getOutputStream());
        DataInputStream inputClient = new DataInputStream(socket.getInputStream())
        ) {
            outputClient.writeUTF("CLIENT_SLAVE");
            outer : while(true) {
                String commande = inputClient.readUTF();
                
                if(!commande.equals("PING")) logger.info(commande + " : " + socket.getInetAddress().getHostAddress()); // Ne pas afficher les PING dans le log

                // Envoyer la liste des fichiers disponibles au client
                if ("LIST".equals(commande)) {
                    sendFileList(outputClient);
                }
                
                // Envoyer les informations (nom et taille) sur le fichier demandé au client
                if (commande.startsWith("GET_INFO")) {
                    infoOnFile(outputClient, commande);
                }
                
                // TEST déconnexion client
                if ("PING".equals(commande)) {
                    outputClient.writeUTF("PONG");
                }
                
                // Vérification du MD5 du fichier
                if (commande.startsWith("HASH")) {
                    handleFileHash(inputClient, outputClient, commande);
                    break outer;
                } 
            }
            
        } catch(IOException e) {
            System.out.println("Le client a ete deconnecte.");
            logger.warning("Client déconnecté");
        } catch(Exception e) {
            e.printStackTrace();
            logger.warning("Erreur dans ClientSlave : " + e.getMessage());
        }
        
    }
    
    /**
     * Envoie le nombre et la liste des fichiers disponibles au client.
     * @param outputClient flux de sortie vers le client
     * @throws IOException
     */
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
    
    /**
     * Envoie les informations (nom et taille) sur le fichier demandé au client.
     * @param outputClient flux de sortie vers le client
     * @param commande commande reçue du client
     * @throws IOException
     */
    private void infoOnFile(DataOutputStream outputClient, String commande) throws IOException {
        // Envoyer les informations sur le fichier demandé au client
        int fileIndex = Integer.parseInt(commande.split(" ")[1]);
        File fichier = files[fileIndex];
        long fileSize = fichier.length();
        String fileName = fichier.getName();
        outputClient.writeUTF(fileName);
        outputClient.writeLong(fileSize);
    }
    
    /**
     * Compare le MD5 reçu du fichier avec celui du serveur. Ajoute le client à la liste des clients de confiance si le hash est correct.
     * @param inputClient
     * @param outputClient
     * @param commande
     * @throws IOException
     */
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
            System.out.println("Hash du fichier sur le serveur : " + hashServeur);
            System.out.println("Hash du fichier sur le client : " + hashClient);
            
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
    
    /**
     * Convertit un tableau de bytes en une chaîne hexadécimale.
     * @param bytes tableau de bytes à convertir
     * @return chaîne hexadécimale représentant le tableau de bytes
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
