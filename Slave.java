import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class Slave extends Thread {
    private static final Logger logger = Logger.getLogger(Slave.class.getName());

    private String host;
    private int port;
    private String fileId;
    private int blockIndex;
    private byte[][] blocks;
    private int blockSize = 1024;

    public Slave(String host, int port, String fileId, int blockIndex, byte[][] blocks) {
        this.host = host;
        this.port = port;
        this.fileId = fileId;
        this.blockIndex = blockIndex;
        this.blocks = blocks;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(host, port);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            writer.write("BLOCK " + fileId + " " + blockIndex + "\n");
            writer.flush();

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[blockSize];
            int read = in.read(buffer);

            if (read > 0) {
                blocks[blockIndex] = new byte[read];
                System.arraycopy(buffer, 0, blocks[blockIndex], 0, read);
                logger.info("Block " + blockIndex + " received successfully.");
            } else {
                logger.warning("Block " + blockIndex + " was empty or not received.");
            }

            socket.close();

        } catch (IOException e) {
            logger.severe("Error in Slave thread for block " + blockIndex + ": " + e.getMessage());
        }
    }
} 
