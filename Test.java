import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lance automatiquement le serveur et les clients pour un test.
 */
public class Test {

    public static void main(String[] args) throws IOException, InterruptedException {
        int clients = 3;
        int DC = 2;
        String fileName = "FileA.txt";

        for (String arg : args) {
            if (arg.startsWith("--clients=")) clients = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--DC=")) DC = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--file=")) fileName = arg.split("=")[1];
        }

        List<Process> processes = new ArrayList<>();

        // Lancer le serveur sans arguments (car tout est configuré dans Server.java)
        ProcessBuilder serverPB = new ProcessBuilder("java", "Server");
        serverPB.inheritIO();
        processes.add(serverPB.start());

        Thread.sleep(2000); // attendre que le serveur démarre

        // Lancer les clients
        for (int i = 1; i <= clients; i++) {
            ProcessBuilder clientPB = new ProcessBuilder("java", "Client",
                    "--file=" + fileName,
                    "--DC=" + DC,
                    "--host=localhost",
                    "--port=12345",
                    "--id=" + i);
            clientPB.inheritIO();
            processes.add(clientPB.start());
        }

        // Attendre que tous les processus soient terminés
        for (Process p : processes) {
            p.waitFor();
        }

        System.out.println("Test terminé !");
    }
}
