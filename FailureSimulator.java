import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

interface ClientDisconnectionListener {
    void onClientDisconnected(String clientId);
}


public class FailureSimulator {

    private final Map<String, Long> activeTransfers = new ConcurrentHashMap<>();
    private final Map<String, Socket> clientSockets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final double probability;
    private final int intervalSeconds;
    private final Set<String> disconnectedClients = ConcurrentHashMap.newKeySet();
    private ClientDisconnectionListener disconnectionListener;

    public void setDisconnectionListener(ClientDisconnectionListener listener) {
        this.disconnectionListener = listener;
    }
    

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
    public void registerTransfer(String clientId, Socket socket) {
        activeTransfers.put(clientId, System.currentTimeMillis());
        clientSockets.put(clientId, socket);
    }

    public void unregisterTransfer(String clientId) {
        activeTransfers.remove(clientId);
        clientSockets.remove(clientId);  //optionnel mais propre
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
            if (disconnectionListener != null) {
                disconnectionListener.onClientDisconnected(clientId);
            }
    
            // On ferme le socket si on le retrouve
            Socket socket = clientSockets.get(clientId);
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Erreur lors de la fermeture du socket : " + e.getMessage());
                }
            }
    
            activeTransfers.remove(clientId);
            clientSockets.remove(clientId); // nettoie aussi
        }
    }
    
}
