import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class FailureSimulator {

    private final List<Socket> activeTransfers = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = Log.setup("FailureSimulator", "failuresimulator.log");


    private final double probability; // entre 0.0 et 1.0
    private final int intervalSeconds;
    private int count = 0;

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

    public void registerTransfer(Socket socket) {
        activeTransfers.add(socket);
    }

    public void unregisterTransfer(Socket socket) {
        activeTransfers.remove(socket);
    }

    private void maybeInterruptConnection() {
        System.out.println(activeTransfers);
        if (Math.random() < probability) {

            synchronized (activeTransfers) {
                if (!activeTransfers.isEmpty()) {

                    int index = new Random().nextInt(activeTransfers.size());
                    Socket socket = activeTransfers.get(index);
                    try {
                        System.out.println(">>> Simulating failure: closing connection");
                        socket.close();
                        logger.info("[METRICS] COUPURE_CLIENT=" + socket.getPort());
                        count++;
                        activeTransfers.remove(socket);
                    } catch (Exception e) {
                        System.err.println("Error while closing socket: " + e.getMessage());
                    }
                }
            }
        }
    }


}
