import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de test automatique pour lancer le serveur et plusieurs clients.
 * Utilise ProcessBuilder pour lancer des processus Java en local.
 */
public class Test {

    public static void main(String[] args) throws IOException, InterruptedException {
        int clients = 3;
        int DC = 2;
        double P = 0.3;
        int T = 5;
        int Cs = 2;
        String fileName = "file1";

        for (String arg : args) {
            if (arg.startsWith("--clients=")) clients = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--DC=")) DC = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--P=")) P = Double.parseDouble(arg.split("=")[1]);
            else if (arg.startsWith("--T=")) T = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--Cs=")) Cs = Integer.parseInt(arg.split("=")[1]);
            else if (arg.startsWith("--file=")) fileName = arg.split("=")[1];
        }

        List<Process> processes = new ArrayList<>();

        // Lancer le serveur
        ProcessBuilder serverPB = new ProcessBuilder("java", "Server",
                "--port=12345",
                "--Cs=" + Cs,
                "--T=" + T,
                "--P=" + P);
        serverPB.inheritIO();
        processes.add(serverPB.start());

        Thread.sleep(2000); // laisser le serveur démarrer

        // Lancer les clients
        for (int i = 0; i < clients; i++) {
            ProcessBuilder clientPB = new ProcessBuilder("java", "Client",
                    "--file=" + fileName,
                    "--DC=" + DC,
                    "--host=localhost",
                    "--port=12345");
            clientPB.inheritIO();
            processes.add(clientPB.start());
        }

        // Attendre la fin de tous les processus
        for (Process p : processes) {
            p.waitFor();
        }

        System.out.println("Test terminé.");
    }
}
