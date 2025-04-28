import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class BlockDownloader {
    private final String host;
    private final int port;
    private final String fileId;
    private final int totalBlocks;
    private final int dc;
    private final int blockSize;
    private final Logger logger;
    private final int maxRetries = 3;
    private final int socketTimeoutMillis = 5000;

    public BlockDownloader(String host, int port, String fileId, int totalBlocks, int dc, int blockSize, Logger logger) {
        this.host = host;
        this.port = port;
        this.fileId = fileId;
        this.totalBlocks = totalBlocks;
        this.dc = dc;
        this.blockSize = blockSize;
        this.logger = logger;
    }

    public byte[][] downloadAllBlocks() throws Exception {
        byte[][] blocks = new byte[totalBlocks][];
        ExecutorService executor = Executors.newFixedThreadPool(dc);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalBlocks; i++) {
            final int blockIndex = i;
            futures.add(executor.submit(() -> {
                downloadBlockWithRetry(blockIndex, blocks);
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();
        return blocks;
    }

    private void downloadBlockWithRetry(int index, byte[][] blocks) throws Exception {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                downloadBlock(index, blocks);
                return;
            } catch (Exception e) {
                attempts++;
                logger.warning("Retry " + attempts + " for block " + index + " after error: " + e.getMessage());
                Thread.sleep(500);
            }
        }
        throw new IOException("Failed to download block " + index + " after " + maxRetries + " retries.");
    }

    private void downloadBlock(int index, byte[][] blocks) throws IOException {
        String currentHost = host;
        int currentPort = port;
        Socket socket = null;

        try {
            while (true) {
                socket = new Socket(currentHost, currentPort);
                socket.setSoTimeout(socketTimeoutMillis);

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                String line = reader.readLine();
                if (line == null) throw new IOException("No response from server");

                if (line.startsWith("USE_HELPER")) {
                    // Redirection vers helper
                    String[] parts = line.split(" ");
                    currentHost = parts[1];
                    currentPort = Integer.parseInt(parts[2]);
                    logger.info("Redirected to helper at " + currentHost + ":" + currentPort);
                    socket.close();
                    continue; // Reconnexion immédiate
                }

                if (!line.startsWith("WELCOME") && !line.startsWith("WELCOME_FROM_TRUSTED")) {
                    throw new IOException("Unexpected server response: " + line);
                }

                // Si on est sur un trusted helper avec token, pas besoin de renvoyer le token ici : déjà fait en amont

                writer.write("BLOCK " + fileId + " " + index + "\n");
                writer.flush();

                // Après la demande du bloc : lecture vigilante
                InputStream in = socket.getInputStream();

                // IMPORTANT : lire un premier octet pour vérifier si ce n'est pas un message texte !
                socket.setSoTimeout(socketTimeoutMillis);
                PushbackInputStream pushbackIn = new PushbackInputStream(in, blockSize);

                byte[] buffer = new byte[blockSize];
                int read = pushbackIn.read(buffer, 0, 1);
                if (read == -1) throw new IOException("Connection closed unexpectedly after request.");

                // Vérifier si premier octet est un caractère texte ?
                if (buffer[0] == 'U' || buffer[0] == 'E' || buffer[0] == 'D') { // "USE_HELPER" / "ERROR" / "DISCONNECT"
                    byte[] tempBuffer = new byte[1024];
                    int textRead = pushbackIn.read(tempBuffer);
                    String textMessage = (char) buffer[0] + new String(tempBuffer, 0, textRead);

                    throw new IOException("Received unexpected control message during block download: " + textMessage.trim());
                }

                // Sinon, push back le premier octet et lire normalement
                pushbackIn.unread(buffer, 0, 1);

                int totalRead = pushbackIn.read(buffer);
                if (totalRead == -1) throw new IOException("Connection closed during block download");

                blocks[index] = Arrays.copyOf(buffer, totalRead);
                logger.info("Block " + index + " downloaded successfully.");
                break;
            }

        } catch (SocketTimeoutException e) {
            throw new IOException("Timeout while downloading block " + index);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
