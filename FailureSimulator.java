import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Simule aléatoirement des déconnexions de clients toutes les T secondes avec probabilité P.
 */
public class FailureSimulator {
    private static final Logger logger = Logger.getLogger(FailureSimulator.class.getName());

    private final List<Socket> trackedSockets = new CopyOnWriteArrayList<>();
    private final int intervalSeconds;
    private final double probability;
    private final Timer timer = new Timer();

    public FailureSimulator(int intervalSeconds, double probability) {
        this.intervalSeconds = intervalSeconds;
        this.probability = probability;
    }

    public void track(Socket socket) {
        trackedSockets.add(socket);
    }

    public void untrack(Socket socket) {
        trackedSockets.remove(socket);
    }

    public void start() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (trackedSockets.isEmpty()) return;

                if (Math.random() < probability) {
                    Socket victim = trackedSockets.get(new Random().nextInt(trackedSockets.size()));
                    try {
                        logger.warning("[FAILURE SIMULATOR] Forcing disconnection: " + victim);
                        victim.close();
                        trackedSockets.remove(victim);
                    } catch (Exception e) {
                        logger.severe("Error while disconnecting socket: " + e.getMessage());
                    }
                }
            }
        }, intervalSeconds * 1000L, intervalSeconds * 1000L);
    }

    public void stop() {
        timer.cancel();
        trackedSockets.clear();
    }
}
