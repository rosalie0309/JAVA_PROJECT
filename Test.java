import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Random;


public class Test {
    
    public static void main(String[] args) {
        int numClients = 3; // Nombre de clients à lancer
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
                
                if(fileindex == -1) {
                    // Si fileindex n'est pas spécifié, choisir un index de fichier aléatoire
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
