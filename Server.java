import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.lang.Thread;


public class Server {
    private final int port;
    private final ExecutorService pool;
    private final File[] files;
    private final CopyOnWriteArrayList<String> trustedClients;
    private final Semaphore clientSemaphore;
    private final Logger logger = Logger.getLogger("Server");
    private FailureSimulator failureSimulator;
    private final Map<String, String> tokenMap = new ConcurrentHashMap<>();
    private final Set<InetAddress> clientsDeconnectes = ConcurrentHashMap.newKeySet();
    private final Map<Socket, Long> connectedAt = new ConcurrentHashMap<>();




    public Server(int port, int poolSize, FailureSimulator failureSimulator) {
        this.port = port;
        this.pool = Executors.newFixedThreadPool(poolSize);
        this.trustedClients = new CopyOnWriteArrayList<>();
        this.clientSemaphore = new Semaphore(poolSize/4);
        this.failureSimulator = failureSimulator;

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

    public void afficherClientsDeConfiance() {
        System.out.println("\nListe des clients de confiance :");
        for (String ip : trustedClients) {
            System.out.println(" - " + ip);
        }
    }

    private String choisirClientTrusted() {
        if (trustedClients.isEmpty()) return null;
        int index = new Random().nextInt(trustedClients.size());
        return trustedClients.get(index);
    }

    private boolean envoyerDemandeDelegation(String trustedIp) {
        double Pc = 0.7;
        try (
            Socket trustedSocket = new Socket(trustedIp, 12346);
            DataOutputStream out = new DataOutputStream(trustedSocket.getOutputStream());
            DataInputStream in = new DataInputStream(trustedSocket.getInputStream());
        ) {
            out.writeUTF("HELP_REQUEST");
            out.flush();

            String reponse = in.readUTF();
            return "YES".equals(reponse);
        } catch (IOException e) {
            logger.warning("Erreur lors de la demande de délégation à " + trustedIp + " : " + e.getMessage());
            return false;
        }
    }

    private void envoyerTokenAuClient(Socket socket, String trustedIp) {
        try {
            String token = UUID.randomUUID().toString();
            tokenMap.put(token, trustedIp);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("TOKEN " + token);
            out.flush();

            logger.info("Token envoyé au client " + socket.getInetAddress() + " via " + trustedIp);
        } catch (IOException e) {
            logger.warning("Erreur lors de l’envoi du token au client : " + e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            DataInputStream input = new DataInputStream(socket.getInputStream());
            String fullHeader = input.readUTF();
            String[] split = fullHeader.split(" ");
            String type = split[0];
            String clientId = split.length > 1 ? split[1] : socket.getRemoteSocketAddress().toString();
            

            if ("CLIENT_MAIN".equals(type)) {
                if (!clientSemaphore.tryAcquire()) {
                    logger.warning("Capacité atteinte, tentative de délégation au client trusted...");
                    logger.warning("Capacité atteinte : " + clientSemaphore.availablePermits() + " places restantes");
    
                    String trustedIp = choisirClientTrusted();
                    if (trustedIp != null && envoyerDemandeDelegation(trustedIp)) {
                        envoyerTokenAuClient(socket, trustedIp);
                        return;
                    }
    
                    logger.warning("Aucun client trusted disponible ou délégation refusée. Connexion rejetée.");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    output.writeUTF("SERVER_BUSY");
                    output.flush();
                    socket.close();
                    return;
                } else {
                    logger.info("Slot de téléchargement acquis pour " + clientId);
                }
    
                output.writeUTF("WELCOME");
                output.flush();
                connectedAt.put(socket, System.currentTimeMillis());

                logger.info("-----------CLIENT_MAIN connecté : " + clientId);

                pool.execute(() -> {
                    failureSimulator.registerTransfer(clientId); 
                    try {
                        new ClientSlave(socket, files, trustedClients).run();
                    } catch (Exception e) {
                        logger.warning("Erreur CLIENT_MAIN : " + e.getMessage());
                    } finally {
                        logger.info("Libération de la place dans le pool pour " + clientId);
                        clientSemaphore.release();
                        failureSimulator.unregisterTransfer(clientId);
                    
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        logger.info("-----------CLIENT_MAIN terminé : " + clientId);
                    }
                    
                });
            } else if ("BLOCK_DOWNLOAD".equals(type)) {
                if (failureSimulator.isDisconnected(clientId)) {
                    logger.warning("Requête BLOCK_DOWNLOAD refusée : le client " + clientId + " a été déconnecté.");
                    try {
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeInt(-1); // signal d’erreur
                        out.flush();
                    } catch (IOException ignored) {}
                    try { socket.close(); } catch (IOException ignored) {}
                    return;
                }
                
                
                logger.info("-----------BLOCK_DOWNLOAD connecté : " + clientId);
            
                pool.execute(() -> {
                    try {
                        new BlockSlave(socket, files).run();
                    } catch (Exception e) {
                        logger.warning("Erreur BLOCK_DOWNLOAD : " + e.getMessage());
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        logger.info("-----------BLOCK_DOWNLOAD terminé : " + clientId);
                    }
                });
            }
            else {
                logger.warning("Client inconnu rejeté : " + clientId);
                socket.close();
            }

        } catch (IOException e) {
            logger.warning("Erreur de lecture de type client : " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 12345;
        int poolSize = 16;

        FailureSimulator failureSimulator = new FailureSimulator(0.1, 2);
        failureSimulator.start();

        Server server = new Server(port, poolSize, failureSimulator);
        server.start();

        server.afficherClientsDeConfiance();
    }
}
