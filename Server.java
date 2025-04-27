import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class Server {
    private final int port;
    private final ExecutorService pool;
    private final File[] files;
    private ArrayList<String> trustedClients;
    private final Semaphore clientSemaphore;
    private static final Logger logger = Log.setup("Server", "server.log");
    private FailureSimulator failureSimulator;
    private double probability;

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

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("Serveur démarré sur le port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleConnection(socket)).start();
        }
    }

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

    public static void main(String[] args) throws IOException {

        int port = 12345; // Port du serveur
        int poolSize = 16;  // Taille du pool de threads
        double failureProbability = 0.2; // Probabilité de défaillance

        for(String arg : args) {
            if(arg.startsWith("--P=")) failureProbability = Double.parseDouble((arg.split("=")[1]).replace(",", "."));
        }

        //FailureSimulator failureSimulator = new FailureSimulator(1.0, 4); 
        FailureSimulator failureSimulator = new FailureSimulator(failureProbability, 5);// 30% de chances toutes les 10 secondes    
        failureSimulator.start();

        Server server = new Server(port, poolSize, failureSimulator, failureProbability);
        server.start();


    }
}
