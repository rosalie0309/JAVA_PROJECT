import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Gère les clients de confiance et la délégation entre clients.
 */
public class TrustedHelper {
    private static final Logger logger = Logger.getLogger(TrustedHelper.class.getName());

    // Map des clients de confiance par fichier : fileId -> Set<clientAddress>
    private final Map<String, Set<String>> trustedClients = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * Ajoute un client de confiance pour un fichier donné.
     */
    public void addTrustedClient(String fileId, String clientAddress) {
        trustedClients.putIfAbsent(fileId, ConcurrentHashMap.newKeySet());
        trustedClients.get(fileId).add(clientAddress);
        logger.info("Trusted client added: " + clientAddress + " for file: " + fileId);
    }

    /**
     * Tente d'obtenir un client de confiance pour aider au téléchargement.
     */
    public Optional<String> requestHelp(String fileId, double acceptanceProbability) {
        Set<String> clients = trustedClients.getOrDefault(fileId, Collections.emptySet());
        if (clients.isEmpty()) return Optional.empty();
    
        List<String> list = new ArrayList<>(clients);
        Collections.shuffle(list);
    
        for (String c : list) {
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
