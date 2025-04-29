import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

/**
 * Classe du serveur qui lance le serveur et gère les connexions des clients et des blocs de téléchargement.
 */
public class Server {
    private final int port;
    private final ExecutorService pool;
    private final File[] files;
    private ArrayList<String> trustedClients;
    private final Semaphore clientSemaphore;
    private static final Logger logger = Log.setup("Server", "server.log");
    private FailureSimulator failureSimulator;
    private double probability;

    /**
     * Constructeur de la classe Server.
     * @param port Le port sur lequel le serveur écoute les connexions.
     * @param poolSize La taille du pool de threads pour gérer les connexions.
     * @param failureSimulator Le simulateur de déconnexions à utiliser.
     * @param probability La probabilité de déconnexion.
     */
    public Server(int port, int poolSize, FailureSimulator failureSimulator, double probability) {
        this.port = port;
        this.pool = Executors.newFixedThreadPool(poolSize);
        this.trustedClients = new ArrayList<>();
        this.clientSemaphore = new Semaphore(poolSize/4, true);
        this.failureSimulator = failureSimulator;
        this.probability = probability;

        // Charge les fichiers à partir du répertoire "fichiers"
        File dir = new File("Fichiers");
        if (!dir.exists()) {
            dir.mkdir(); // Crée le répertoire s'il n'existe pas
        }
        this.files = dir.listFiles(); // Liste les fichiers dans le répertoire
    }

    /**
     * Démarre le serveur et écoute les connexions entrantes. Redirige les connexions vers la fonction de gestionnaire de connexion.
     * @throws IOException Si une erreur d'entrée/sortie se produit lors de l'ouverture du socket.
     */
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        logger.info("Serveur démarré sur le port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleConnection(socket)).start();
        }
    }

    /**
     * Gère la connexion d'un client ou d'un bloc de téléchargement.
     * @param socket Le socket de la connexion entrante
     */
    private void handleConnection(Socket socket) {
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            String type = input.readUTF();

            if ("CLIENT_MAIN".equals(type)) {
                clientSemaphore.acquire();
                logger.info("-----------CLIENT_MAIN connecté : " + socket.getInetAddress());
            
                pool.execute(() -> {
                    failureSimulator.registerTransfer(socket); 
                    try {
                        new ClientSlave(socket, files, trustedClients).run();
                        //new ClientSlave(socket, files).run();
                    } catch (Exception e) {
                        logger.warning("Erreur CLIENT_MAIN : " + e.getMessage());
                    } finally {
                        clientSemaphore.release();
                        failureSimulator.unregisterTransfer(socket); 
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        logger.info("-----------CLIENT_MAIN terminé : " + socket.getInetAddress());
                    }
                });
            } else if ("BLOCK_DOWNLOAD".equals(type)) {
                logger.info("-----------BLOCK_DOWNLOAD connecté : " + socket.getInetAddress());
            
                pool.execute(() -> {
                    try {
                        new BlockSlave(socket, files).run();
                    } catch (Exception e) {
                        logger.warning("Erreur BLOCK_DOWNLOAD : " + e.getMessage());
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        logger.info("-----------BLOCK_DOWNLOAD terminé : " + socket.getInetAddress());
                    }
                });
            }
             else {
                logger.warning("Client inconnu rejeté : " + socket.getInetAddress());
                socket.close();
            }

        } catch (IOException | InterruptedException e) {
            logger.warning("Erreur de lecture de type client : " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Fonction principale pour démarrer le serveur et le simulateur de déconnexion.
     * @param args Les arguments de la ligne de commande.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        int port = 12345; // Port du serveur
        int poolSize = 16;  // Taille du pool de threads
        double failureProbability = 0.2; // Probabilité de défaillance

        for(String arg : args) {
            if(arg.startsWith("--P=")) failureProbability = Double.parseDouble((arg.split("=")[1]).replace(",", "."));
        }

        //FailureSimulator failureSimulator = new FailureSimulator(1.0, 4); 
        FailureSimulator failureSimulator = new FailureSimulator(failureProbability, 5);// 20% de chances toutes les 5 secondes    
        failureSimulator.start();

        Server server = new Server(port, poolSize, failureSimulator, failureProbability);
        server.start();


    }
}
