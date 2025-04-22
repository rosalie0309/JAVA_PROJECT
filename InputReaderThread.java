import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.Socket;

public class InputReaderThread extends Thread {
    private volatile String userInput = null;
    private volatile boolean running = true;

    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Entrez le numéro du fichier à télécharger : "); 
            String line = reader.readLine();
            if (running) {
                userInput = line;
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("Erreur de lecture d'entrée : " + e.getMessage());
            }
        }
    }

    public void stopReading() {
        running = false;
        this.interrupt();
    }

    public String getUserInput() {
        return userInput;
    }
}

