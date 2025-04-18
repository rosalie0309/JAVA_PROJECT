import java.io.*;
import java.net.*;



public class TrustedHelper {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(12346);
        System.out.println("TrustedHelper prêt sur le port 12346");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try (
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                ) {
                    String demande = in.readUTF();
                    if ("HELP_REQUEST".equals(demande)) {
                        boolean accepte = Math.random() < 0.8; // Probabilité Pc ici
                        out.writeUTF(accepte ? "YES" : "NO");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
