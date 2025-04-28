import java.io.IOException;
import java.util.logging.*;

public class ServerLogger {
    public static Logger createLogger() {
        Logger logger = Logger.getLogger("ServerLogger");
        logger.setUseParentHandlers(false);

        try {
            // Créer le dossier logs s'il n'existe pas
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("logs"));

            FileHandler fh = new FileHandler("logs/server.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            System.err.println("Failed to initialize server logger: " + e.getMessage());
        }

        return logger;
    }
}
