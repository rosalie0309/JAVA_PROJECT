import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.logging.Logger;

public class TrustedClient {
    private final int port;
    private final String expectedToken;
    private final String fileId;
    private final Logger logger;
    private final double acceptanceProbability = 0.7; // 70% acceptation

    public TrustedClient(int port, TrustedHelperInfo helperInfo) {
        this.port = port;
        this.expectedToken = helperInfo.getToken();
        this.fileId = helperInfo.getFileId();
        this.logger = ServerLogger.createLogger();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(30000); // Timeout : 30s max

            logger.info("TrustedClient ready on port: " + port);

            Socket clientSocket = serverSocket.accept(); // attendre une vraie connexion

            handleClient(clientSocket);

        } catch (SocketTimeoutException e) {
            logger.info("No client connected, TrustedClient shutting down...");
        } catch (IOException e) {
            logger.severe("TrustedClient error: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            writer.write("WELCOME_FROM_TRUSTED\n");
            writer.flush();

            String tokenMessage = reader.readLine();
            if (tokenMessage == null || !tokenMessage.startsWith("TOKEN ")) {
                writer.write("REFUSE\n");
                writer.flush();
                clientSocket.close();
                logger.warning("Client disconnected before sending TOKEN.");
                return;
            }


            String receivedToken = tokenMessage.substring(6).trim();
            if (!receivedToken.equals(expectedToken)) {
                writer.write("REFUSE\n");
                writer.flush();
                clientSocket.close();
                logger.warning("Invalid token received.");
                return;
            }

            if (!shouldAccept()) {
                writer.write("REFUSE\n");
                writer.flush();
                clientSocket.close();
                logger.info("TrustedClient refused connection (random choice).");
                return;
            }

            writer.write("WELCOME_FROM_TRUSTED\n");
            writer.flush();

            String request = reader.readLine();
            if (request == null || !request.startsWith("REQUEST ")) {
                clientSocket.close();
                return;
            }

            byte[] fileData = Server.getFileContent(fileId);
            int blockSize = 1024;
            int totalBlocks = (int) Math.ceil((double) fileData.length / blockSize);

            writer.write(String.valueOf(totalBlocks) + "\n");
            writer.flush();

            while (true) {
                String blockRequest = reader.readLine();
                if (blockRequest == null) break;

                if (blockRequest.startsWith("BLOCK ")) {
                    int blockIndex = Integer.parseInt(blockRequest.split(" ")[2]);
                    int start = blockIndex * blockSize;
                    int end = Math.min(fileData.length, start + blockSize);

                    clientSocket.getOutputStream().write(fileData, start, end - start);
                }
            }

        } catch (IOException e) {
            logger.warning("TrustedClient connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private boolean shouldAccept() {
        return Math.random() <= acceptanceProbability;
    }
}
