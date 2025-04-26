import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private String fileId;
    private int port;
    private String host;
    private int DC;
    private int blockSize = 1024;
    private volatile boolean isDisconnected = false;

    public Client(String host, int port, String fileId, int DC) {
        this.host = host;
        this.port = port;
        this.fileId = fileId;
        this.DC = DC;
    }

    public void start() {
        try {
            Socket socket = new Socket(host, port);
            logger.info("Connected to server: " + socket);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line = reader.readLine();
            if (line == null || line.equals("DISCONNECT")) {
                logger.warning("Disconnected by server before start.");
                return;
            }

            if (line.startsWith("USE_HELPER")) {
                String helperName = line.split(" ", 2)[1];
                logger.warning("Server is full. You are redirected to helper client: " + helperName);
                return; // in a real system we would now reconnect to helper
            }

            if (!"WELCOME".equals(line)) {
                logger.warning("Unexpected server message: " + line);
                return;
            }

            writer.write("REQUEST " + fileId + "\n");
            writer.flush();

            String response = reader.readLine();
            if (response == null || response.equals("DISCONNECT")) {
                logger.warning("Disconnected by server after request.");
                return;
            }

            if (response.startsWith("ERROR")) {
                logger.severe("Server responded with error: " + response);
                return;
            }

            int totalBlocks = Integer.parseInt(response);
            logger.info("Total blocks to download: " + totalBlocks);

            byte[][] blocks = new byte[totalBlocks][];
            ExecutorService executor = Executors.newFixedThreadPool(DC);
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalBlocks; i++) {
                final int blockIndex = i;
                futures.add(executor.submit(() -> {
                    if (!isDisconnected) downloadBlock(blockIndex, blocks);
                    return null;
                }));
            }

            for (Future<Void> f : futures) f.get();
            executor.shutdown();

            if (isDisconnected) {
                logger.warning("Client interrupted during download, exiting.");
                return;
            }

            File downloadDir = new File("download");
            if (!downloadDir.exists()) downloadDir.mkdir();

            FileOutputStream fos = new FileOutputStream("download/downloaded-" + fileId + ".bin");
            for (byte[] b : blocks) fos.write(b);
            fos.close();
            logger.info("Download complete.");

            String md5 = Digest.computeMD5("download/downloaded-" + fileId + ".bin");
            writer.write("MD5 " + md5 + "\n");
            writer.flush();

            logger.info("Sent MD5: " + md5);

        } catch (IOException e) {
            isDisconnected = true;
            logger.severe("Client IO error: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Client error: " + e.getMessage());
        }
    }

    private void downloadBlock(int index, byte[][] blocks) {
        try {
            Socket blockSocket = new Socket(host, port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(blockSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(blockSocket.getOutputStream()));

            writer.write("BLOCK " + fileId + " " + index + "\n");
            writer.flush();

            InputStream in = blockSocket.getInputStream();
            byte[] buffer = new byte[blockSize];
            int read = in.read(buffer);
            if (read == -1) throw new IOException("Server closed connection");
            blocks[index] = Arrays.copyOf(buffer, read);

            logger.info("Received block " + index);

            blockSocket.close();
        } catch (IOException e) {
            isDisconnected = true;
            logger.warning("Failed to download block " + index + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        String fileId = null;
        int DC = 4;

        for (String arg : args) {
            if (arg.startsWith("--file=")) fileId = arg.split("=")[1];
            else if (arg.startsWith("--DC=")) DC = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--host=")) host = arg.split("=")[1];
        }

        if (fileId == null) {
            System.err.println("No file specified. Use --file=filename");
            return;
        }

        Client client = new Client(host, port, fileId, DC);
        client.start();
    }
}
