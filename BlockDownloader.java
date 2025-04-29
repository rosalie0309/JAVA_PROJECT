import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class BlockDownloader {
    private final Socket socket;
    private final String fileId;
    private final int totalBlocks;
    private final int dc;
    private final int blockSize;
    private final Logger logger;
    private final int maxRetries = 3;

    public BlockDownloader(Socket socket, String fileId, int totalBlocks, int dc, int blockSize, Logger logger) {
        this.socket = socket;
        this.fileId = fileId;
        this.totalBlocks = totalBlocks;
        this.dc = dc;
        this.blockSize = blockSize;
        this.logger = logger;
    }

    public byte[][] downloadAllBlocks() throws Exception {
        byte[][] blocks = new byte[totalBlocks][];
        ExecutorService executor = Executors.newFixedThreadPool(dc);
        for (int i = 0; i < totalBlocks; i++) {
            final int blockIndex = i;
            executor.submit(() -> {
                try {
                    downloadBlock(blockIndex, blocks);
                } catch (Exception e) {
                    logger.warning("Failed to download block " + blockIndex + ": " + e.getMessage());
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        return blocks;
    }

    private void downloadBlock(int index, byte[][] blocks) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());

        int attempts = 0;
        boolean success = false;

        while (attempts < maxRetries && !success) {
            try {
                writer.write("BLOCK " + fileId + " " + index + "\n");
                writer.flush();

                byte[] buffer = new byte[blockSize];
                int bytesRead = input.read(buffer);

                if (bytesRead == -1) {
                    throw new IOException("Connection closed during block download");
                }

                blocks[index] = Arrays.copyOf(buffer, bytesRead);
                logger.info("Block " + index + " downloaded successfully.");
                success = true;
            } catch (IOException e) {
                attempts++;
                logger.warning("Retrying block " + index + ", attempt " + attempts);
                Thread.sleep(500);
            }
        }

        if (!success) {
            throw new IOException("Failed to download block " + index + " after " + maxRetries + " attempts.");
        }
    }
}
