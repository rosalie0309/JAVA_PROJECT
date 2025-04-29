import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Paths;


public class Client {
    private static Logger logger;
    private String fileId;
    private String host;
    private int port;
    private int DC;
    private int blockSize = 1024;
    private volatile boolean isDisconnected = false;
    private int clientId;

    public Client(String host, int port, String fileId, int DC, int clientId) {
        this.host = host;
        this.port = port;
        this.fileId = fileId;
        this.DC = DC;
        this.clientId = clientId;
        this.logger = ClientLogger.createLogger(clientId);
    }

    public void start() {
        try {
            boolean success = attemptDownload(this.host, this.port, null);
            if (success) {
                logger.info("Download successful. Becoming a TrustedClient...");
                becomeTrustedClient();
            }
        } catch (Exception e) {
            logger.severe("Client error: " + e.getMessage());
        }
    }

    private boolean attemptDownload(String host, int port, String token) throws Exception {
        Socket socket = null;
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            logger.info("Connected to: " + host + ":" + port);
    
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    
            String line = reader.readLine();
            if (line == null) {
                logger.warning("No response from server.");
                return false;
            }
    
            if (line.startsWith("USE_HELPER")) {
                // Traitement de la redirection
                String[] parts = line.split(" ");
                if (parts.length != 4) {
                    logger.severe("Malformed USE_HELPER message: " + line);
                    return false;
                }
                String helperIp = parts[1];
                int helperPort = Integer.parseInt(parts[2]);
                String helperToken = parts[3];
                logger.info("Redirecting to trusted helper at " + helperIp + ":" + helperPort);
    
                socket.close();
                return attemptDownload(helperIp, helperPort, helperToken);
            }
    
            if (line.startsWith("WELCOME") || line.startsWith("WELCOME_FROM_TRUSTED")) {
                if (token != null) {
                    // Envoyer le token si on est redirigé
                    writer.write("TOKEN " + token + "\n");
                    writer.flush();
                    String response = reader.readLine();
                    if (response == null || response.equals("REFUSE")) {
                        logger.warning("Trusted helper refused the connection.");
                        return false;
                    }
                }
    
                // Envoyer la demande de fichier
                writer.write("REQUEST " + fileId + "\n");
                writer.flush();
    
                String response = reader.readLine();
                if (response == null || response.startsWith("DISCONNECT")) {
                    logger.warning("Disconnected after request.");
                    return false;
                }
    
                if (response.startsWith("ERROR")) {
                    logger.severe("Server responded with error: " + response);
                    return false;
                }
    
                int totalBlocks = Integer.parseInt(response.trim());
                logger.info("Total blocks to download: " + totalBlocks);
    
                BlockDownloader downloader = new BlockDownloader(socket, fileId, totalBlocks, DC, blockSize, logger);
                byte[][] blocks = downloader.downloadAllBlocks();
    
                if (isDisconnected) {
                    logger.warning("Download interrupted.");
                    return false;
                }
    
                File downloadDir = new File("download");
                if (!downloadDir.exists()) downloadDir.mkdir();
    
                FileOutputStream fos = new FileOutputStream("download/downloaded-" + fileId + "-" + clientId + ".bin");
                for (byte[] b : blocks) fos.write(b);
                fos.close();
    
                logger.info("File downloaded successfully.");
    
                return true;
            }
    
            logger.warning("Unexpected server message: " + line);
            return false;
    
        } catch (SocketTimeoutException e) {
            logger.warning("Timeout: " + e.getMessage());
            return false;
        } catch (IOException e) {
            isDisconnected = true;
            logger.warning("IOException: " + e.getMessage());
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
    
    private void becomeTrustedClient() {
        try {
            int helperPort = 20000 + clientId;
            String token = "localtoken-" + clientId; // on génère un token factice ici
    
            TrustedHelperInfo helperInfo = new TrustedHelperInfo(token, helperPort, fileId);
            TrustedClient trustedClient = new TrustedClient(helperPort, helperInfo);
            new Thread(trustedClient::start).start();
            logger.info("Started TrustedClient on port " + helperPort);
    
        } catch (Exception e) {
            logger.severe("Failed to become TrustedClient: " + e.getMessage());
        }
    }
    

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        String fileId = null;
        int DC = 2;
        int clientId = -1;

        for (String arg : args) {
            if (arg.startsWith("--file=")) fileId = arg.split("=")[1];
            else if (arg.startsWith("--DC=")) DC = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--host=")) host = arg.split("=")[1];
            else if (arg.startsWith("--id=")) clientId = Integer.parseInt(arg.split("=")[1]);
        }

        if (fileId == null || clientId == -1) {
            System.err.println("Usage: --file=xxx --id=n");
            return;
        }

        Client client = new Client(host, port, fileId, DC, clientId);
        client.start();
    }
}
