
import java.io.File;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private ExecutorService pool;
	private int port;
	private int poolSize;
    private ServerSocket server;
    private File[] files;
    private final Set<String> trustedClients = Collections.synchronizedSet(new HashSet<>());


    public Server(int port, int poolSize) {
        this.port = port;
        this.poolSize = poolSize;
        this.pool = Executors.newFixedThreadPool(poolSize);

        File dir = new File("fichiers");
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Dossier 'fichiers' créé (il était manquant)");
        }

        this.files = dir.listFiles();
        if (this.files == null || this.files.length == 0) {
            System.out.println("Aucun fichier disponible dans le dossier 'fichiers'. Arrêt du serveur.");
            System.exit(1);
        }
        try {
            this.server = new ServerSocket(port);
            System.out.println("Serveur demarre sur le port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void manageRequest() {
        try {
            while(true) {
                this.pool.execute(new Slave(server.accept(), this.files, this.trustedClients));
                System.out.println("Un client s'est connecte au serveur");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void afficherClientsDeConfiance() {
        System.out.println("Clients de confiance :");
        for (String client : trustedClients) {
            System.out.println("- " + client);
        }
    }

    public static void main(String[] args) {
        int port = 12345;
        int poolSize = 10;
        Server server = new Server(port, poolSize);
        server.manageRequest();
    }
}
