import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Server {
    private static final int PORT = 12345;
    private static final int MAX_CLIENTS = 2;
    private static final Logger logger = ServerLogger.createLogger();
    private static final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private static final Set<TrustedHelperInfo> trustedHelpers = ConcurrentHashMap.newKeySet();
    private static final Queue<Socket> waitingClients = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();
    private static ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private static int currentClients = 0;
    private static FailureSimulator failureSimulator;

    public static void main(String[] args) {
        loadFiles();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server started on port: " + PORT);

            failureSimulator = new FailureSimulator(15, 0.3); // Démarrage avec 15s de délai
            failureSimulator.start();

            while (true) {
                synchronized (Server.class) {
                    if (currentClients >= MAX_CLIENTS) {
                        try {
                            Thread.sleep(100); // Petite pause si serveur saturé
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                Socket clientSocket = serverSocket.accept();
                failureSimulator.track(clientSocket);
                clientExecutor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            logger.severe("Server error: " + e.getMessage());
        }
    }

    private static void loadFiles() {
        try {
            File directory = new File("files");
            for (File file : directory.listFiles()) {
                files.put(file.getName(), Files.readAllBytes(file.toPath()));
            }
            logger.info("Files loaded: " + files.keySet());
        } catch (IOException e) {
            logger.severe("Failed to load files: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            writer.write("WELCOME\n");
            writer.flush();

            String request = reader.readLine();
            if (request == null || !request.startsWith("REQUEST ")) {
                clientSocket.close();
                return;
            }

            String fileName = request.substring(8).trim();

            synchronized (Server.class) {
                if (currentClients >= MAX_CLIENTS) {
                    logger.warning("Server full, trying helper for client...");
                    if (!redirectToTrustedHelper(clientSocket, fileName)) {
                        logger.warning("No trusted helper available, client queued.");
                        waitingClients.add(clientSocket);
                    }
                    return;
                }
                currentClients++;
            }

            if (!files.containsKey(fileName)) {
                writer.write("ERROR File not found\n");
                writer.flush();
                clientSocket.close();
                decreaseClientCount();
                return;
            }

            byte[] fileData = files.get(fileName);
            int blockSize = 1024;
            int totalBlocks = (int) Math.ceil((double) fileData.length / blockSize);

            writer.write(String.valueOf(totalBlocks) + "\n");
            writer.flush();

            while (true) {
                String blockRequest = reader.readLine();
                if (blockRequest == null) break;

                if (blockRequest.startsWith("BLOCK ")) {
                    String[] parts = blockRequest.split(" ");
                    if (parts.length < 3) continue;

                    int blockIndex = Integer.parseInt(parts[2]);
                    int start = blockIndex * blockSize;
                    int end = Math.min(fileData.length, start + blockSize);

                    clientSocket.getOutputStream().write(fileData, start, end - start);
                    clientSocket.getOutputStream().flush();

                    logger.info("Sent block " + blockIndex + " to client.");
                } else if (blockRequest.startsWith("MD5 ")) {
                    logger.info("Received MD5 checksum: " + blockRequest.substring(4));
                    break;
                } else {
                    logger.warning("Unknown request received: " + blockRequest);
                }
            }

        } catch (IOException e) {
            logger.warning("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            decreaseClientCount();
            tryServeWaitingClients();
        }
    }

    private static boolean redirectToTrustedHelper(Socket clientSocket, String fileName) {
        try {
            String token = generateToken();
            int helperPort = 20000 + random.nextInt(5000);

            TrustedHelperInfo helperInfo = new TrustedHelperInfo(token, helperPort, fileName);
            trustedHelpers.add(helperInfo);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            writer.write("USE_HELPER 127.0.0.1 " + helperPort + " " + token + "\n");
            writer.flush();
            clientSocket.close();

            logger.info("Sent helper info to client: port=" + helperPort + " token=" + token);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to redirect client: " + e.getMessage());
            return false;
        }
    }

    private static void decreaseClientCount() {
        synchronized (Server.class) {
            currentClients--;
        }
    }

    private static void tryServeWaitingClients() {
        Socket waitingClient = waitingClients.poll();
        if (waitingClient != null) {
            clientExecutor.submit(() -> handleClient(waitingClient));
        }
    }

    private static String generateToken() {
        return UUID.randomUUID().toString();
    }

    public static byte[] getFileContent(String fileId) {
        return files.get(fileId);
    }
}
