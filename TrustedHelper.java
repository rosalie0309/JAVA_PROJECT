
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Gère les clients de confiance et la délégation entre clients.
 * 
 * Le serveur utilise cette classe pour gérer la liste des trusted clients,
 *  et choisir aléatoirement qui aidera (avec probabilité).
 */
public class TrustedHelper {
    private static final Logger logger = Logger.getLogger(TrustedHelper.class.getName());

    // Map des clients de confiance par fichier : fileId -> Set<HelperInfo>
    private final Map<String, Set<HelperInfo>> trustedClients = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * Informations sur un helper
     */
    public static class HelperInfo {
        public final String ip;
        public final int port;

        public HelperInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }

    /**
     * Ajoute un client de confiance pour un fichier donné.
     */
    public void addTrustedClient(String fileId, String ip, int port) {
        trustedClients.putIfAbsent(fileId, ConcurrentHashMap.newKeySet());
        trustedClients.get(fileId).add(new HelperInfo(ip, port));
        logger.info("Trusted client added: " + ip + ":" + port + " for file: " + fileId);
    }

    /**
     * Tente d'obtenir un client de confiance pour aider au téléchargement.
     */
    public Optional<HelperInfo> requestHelp(String fileId, double acceptanceProbability) {
        Set<HelperInfo> clients = trustedClients.getOrDefault(fileId, Collections.emptySet());
        if (clients.isEmpty()) return Optional.empty();

        List<HelperInfo> list = new ArrayList<>(clients);
        Collections.shuffle(list);

        for (HelperInfo c : list) {
            if (random.nextDouble() < acceptanceProbability) {
                logger.info("Client " + c + " accepte d'aider pour file " + fileId);
                return Optional.of(c);
            } else {
                logger.info("Client " + c + " refuse d'aider pour file " + fileId);
            }
        }

        return Optional.empty();
    }

    public void clear() {
        trustedClients.clear();
    }
}
