import java.io.IOException;
import java.util.Random;

/**
 * Classe de test pour lancer le serveur et les clients.
 * Permet de spécifier le nombre de clients, le DC et la probabilité de déconnexion via des arguments de ligne de commande.
 */
public class Test {
    
    /**
     * Fonction principale pour lancer le serveur et les clients.
     * @param args Arguments de la ligne de commande pour spécifier le nombre de clients, le DC et la probabilité de déconnexion.
     * Sinon il existe une valeur par défaut pour chaque paramètre.
     */
    public static void main(String[] args) {
        int numClients = 3;
        int DC = 4;
        double P = 0.2;
        int fileindex = -1;
        Random random = new Random();
        
        for (String arg : args) {
            if(arg.startsWith("--clients=")) numClients = Integer.parseInt(arg.split("=")[1]);
            if(arg.startsWith("--DC=")) DC = Integer.parseInt(arg.split("=")[1]);
            if(arg.startsWith("--P=")) P = Double.parseDouble(arg.split("=")[1]);
            if(arg.startsWith("--file=")) fileindex = Integer.parseInt(arg.split("=")[1]);
        }
        
        try {
            // Lancer le serveur
            ProcessBuilder serverProcessBuilder = new ProcessBuilder("java", "-cp", ".", "Server", "--P=" + String.format("%.1f", P));
            serverProcessBuilder.redirectErrorStream(true);
            
            Process serverProcess = serverProcessBuilder.start();
            System.out.println("Server process started.");

            // Lancer plusieurs clients
            Process[] clientProcesses = new Process[numClients];
            for (int i = 0; i < numClients; i++) {
                
                // Si fileindex n'est pas spécifié, choisir un index de fichier aléatoire
                if(fileindex == -1) {
                    fileindex = random.nextInt(4);
                }
                
                ProcessBuilder clientProcessBuilder = new ProcessBuilder("java", "-cp", ".", "Client", "--file=" + fileindex, "--DC=" + DC);
                clientProcesses[i] = clientProcessBuilder.start();
                System.out.println("Client " + i + " process started.");
            }
            
            // Attendre que tous les clients terminent
            for (int i = 0; i < numClients; i++) {
                int exitCode = clientProcesses[i].waitFor();
                System.out.println("Client " + i + " finished with exit code: " + exitCode);
            }

            // Terminer le serveur
            serverProcess.destroyForcibly();
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
