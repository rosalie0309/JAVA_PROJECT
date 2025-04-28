import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/*
 * La classe permettant de récupérer les logs d'exécution des clients dans le système 
 * Les logs sont stockés dans le dossier logs dans des fichiers dont les noms sont sous 
 * le format client-clientID.log (clientId sera fixé 
 * dans la classe de Test)
 */

public class ClientLogger {
    public static Logger createLogger(int clientId) {
        Logger logger = Logger.getLogger("Client-" + clientId);
        try {
            Files.createDirectories(Paths.get("logs"));
            FileHandler fh = new FileHandler("logs/client-" + clientId + ".log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logger;
    }
}
