import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final int PORT = 12345;
    private static final Map<String, byte[]> fileStorage = new ConcurrentHashMap<>();
    private static final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private static final Queue<Socket> waitingClients = new ConcurrentLinkedQueue<>();
    private static final TrustedHelper trustedHelper = new TrustedHelper();
    private static final int maxClients = 2; // default Cs=2
    private static final FailureSimulator failureSimulator = new FailureSimulator(5, 0.3); // default T=5, P=0.3

    public static void main(String[] args) throws IOException {
        loadFiles();

        // Ajouter manuellement des clients de confiance pour test
        trustedHelper.addTrustedClient("FileA.txt", "trusted1");
        trustedHelper.addTrustedClient("FileA.txt", "trusted2");

        failureSimulator.start();
        ServerSocket serverSocket = new ServerSocket(PORT);
        logger.info("Server started on port: " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();

            if (activeClients.size() < maxClients) {
                activeClients.add(clientSocket);
                failureSimulator.track(clientSocket);
                new Thread(new ClientHandler(clientSocket)).start();
            } else {
                // Tentative de délégation à un client de confiance
                String fileId = "FileA.txt"; // À ce stade, on suppose le fichier connu en avance pour la délégation
                Optional<String> helper = trustedHelper.requestHelp(fileId, 0.8);
                if (helper.isPresent()) {
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    logger.warning("Redirecting client to helper: " + helper.get());
                    out.println("USE_HELPER " + helper.get());
                    clientSocket.close();
                } else {
                    logger.warning("Server full. Queuing client: " + clientSocket);
                    waitingClients.offer(clientSocket);
                }
            }
        }
    }

    private static void loadFiles() {
        try {
            File dir = new File("files");
            for (File f : Objects.requireNonNull(dir.listFiles())) {
                byte[] content = Files.readAllBytes(f.toPath());
                fileStorage.put(f.getName(), content);
            }
        } catch (IOException e) {
            logger.severe("Failed to load files: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                writer.println("WELCOME");
                String request = reader.readLine();
                if (request == null || !request.startsWith("REQUEST ")) {
                    return;
                }

                String fileId = request.split(" ")[1];
                byte[] fileData = fileStorage.get(fileId);
                if (fileData == null) {
                    writer.println("ERROR File not found");
                    return;
                }

                int blockSize = 1024;
                int totalBlocks = (int) Math.ceil(fileData.length / (double) blockSize);
                writer.println(totalBlocks);

                for (int i = 0; i < totalBlocks; i++) {
                    String line = reader.readLine();
                    if (line != null && line.startsWith("BLOCK ")) {
                        int blockIndex = Integer.parseInt(line.split(" ")[2]);
                        int start = blockIndex * blockSize;
                        int end = Math.min(start + blockSize, fileData.length);
                        socket.getOutputStream().write(fileData, start, end - start);
                    }
                }

                String md5Line = reader.readLine();
                if (md5Line != null && md5Line.startsWith("MD5 ")) {
                    logger.info("Received MD5 from client: " + md5Line.split(" ")[1]);
                }

                // Ajouter ce client comme trusted une fois qu’il a tout fini
                String clientAddress = socket.getInetAddress().getHostAddress();
                trustedHelper.addTrustedClient(fileId, clientAddress);

            } catch (Exception e) {
                logger.warning("Exception with client: " + socket + ", Error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                activeClients.remove(socket);
                failureSimulator.untrack(socket);
            }
        }
    }
}
