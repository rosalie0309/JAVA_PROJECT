import java.util.*;
import java.util.concurrent.*;

public class FailureSimulator {

    private final Map<String, Long> activeTransfers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final double probability;
    private final int intervalSeconds;
    private final Set<String> disconnectedClients = ConcurrentHashMap.newKeySet();

    public FailureSimulator(double probability, int intervalSeconds) {
        this.probability = probability;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::maybeInterruptConnection, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    // Appelé côté serveur au moment où un CLIENT_MAIN démarre
    public void registerTransfer(String clientId) {
        activeTransfers.put(clientId, System.currentTimeMillis());
    }

    public void unregisterTransfer(String clientId) {
        activeTransfers.remove(clientId);
    }

    public boolean isDisconnected(String clientId) {
        if (probability == 0.0) return false;
        return disconnectedClients.contains(clientId);
    }

    private void maybeInterruptConnection() {
        System.out.println("Transferts actifs : " + activeTransfers.keySet());

        if (Math.random() < probability && !activeTransfers.isEmpty()) {
            List<String> clients = new ArrayList<>(activeTransfers.keySet());
            String clientId = clients.get(new Random().nextInt(clients.size()));
            long startTime = activeTransfers.get(clientId);
            long duration = System.currentTimeMillis() - startTime;

            if (duration < 3000) {
                System.out.println("Connexion trop récente pour coupure : " + clientId);
                return;
            }

            System.out.println(">>> Simulating failure: marking disconnected " + clientId);
            disconnectedClients.add(clientId);
            activeTransfers.remove(clientId);
        }
    }
}
