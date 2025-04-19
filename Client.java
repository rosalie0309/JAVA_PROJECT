import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Client {
    private static final int tailleBloc = 1024;
    private static final int Dc = 4;
    private static final Logger logger = Log.setup("Client", "client.log");

    public static void main(String[] args) {
        ExecutorService blocPool = Executors.newFixedThreadPool(Dc);
        List<Future<byte[]>> resultats = new ArrayList<>();

        Thread clientMainThread = new Thread(() -> {
            try (
                Socket socket = new Socket("127.0.0.1", 12345);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                DataInputStream input = new DataInputStream(socket.getInputStream());
                Scanner sc = new Scanner(System.in)
            ) {
                // Comme nous fonctionnons encore sur un seul pc, on peut simuler les id pour les clients
                // qui se connectent afin de pouvoir arrêter de manière spécifique un client 
                String clientId = "client_" + UUID.randomUUID().toString().substring(0, 8);
                output.writeUTF("CLIENT_MAIN " + clientId);                
                output.flush();

                String status = input.readUTF();

                while (!"WELCOME".equals(status)) {
                    if ("WAITING_FOR_HELP".equals(status)) {
                        System.out.println("Serveur surchargé. Tentative de délégation en cours...");
                    } else if ("SERVER_BUSY".equals(status)) {
                        System.out.println("Aucun client trusted n’a accepté. Veuillez réessayer plus tard.");
                        return;
                    } else {
                        System.out.println("En attente d'une place ou d'une délégation... (" + status + ")");
                    }

                    // On attend un nouveau message du serveur
                    status = input.readUTF();
                }

                System.out.println("Bienvenue, téléchargement possible !");

                // Demande de liste
                output.writeUTF("LIST");
                output.flush();

                try {
                    input.readInt(); // confirmation
                } catch (IOException e) {
                    System.out.println("Le serveur a fermé la connexion. Aucun téléchargement possible.");
                    return;
                }

                int nbFichiers = input.readInt();
                List<String> noms = new ArrayList<>();
                List<Long> tailles = new ArrayList<>();

                System.out.println("Fichiers disponibles :");
                for (int i = 0; i < nbFichiers; i++) {
                    String nom = input.readUTF();
                    long taille = input.readLong();
                    System.out.println(i + " : " + nom + " (" + taille + " octets)");
                    noms.add(nom);
                    tailles.add(taille);
                }

                System.out.print("Entrez le numéro du fichier à télécharger : ");
                int fileIndex = Integer.parseInt(sc.nextLine());
                if (fileIndex < 0 || fileIndex >= noms.size()) {
                    System.out.println("Indice invalide.");
                    return;
                }

                String nomFichier = noms.get(fileIndex);
                long tailleFichier = tailles.get(fileIndex);
                int nbBlocs = (int) Math.ceil((double) tailleFichier / tailleBloc);
                byte[] fichierRecu = new byte[(int) tailleFichier];

                if (socket.isClosed() || !socket.isConnected()) {
                    System.out.println("La connexion au serveur a été interrompue. Téléchargement annulé.");
                    return;
                }

                // Téléchargement parallèle des blocs

                for (int i = 0; i < nbBlocs; i++) {
                    BlocDownloader tache = new BlocDownloader("127.0.0.1", 12345, fileIndex, i, clientId);
                    resultats.add(blocPool.submit(tache));
                }

                boolean fichierComplet = true;
                for (int i = 0; i < resultats.size(); i++) {
                    byte[] bloc = resultats.get(i).get();
                    if (bloc.length == 0) {
                        System.out.println("Bloc " + i + " vide ou interrompu.");
                        fichierComplet = false;
                        break;
                    }
                    System.arraycopy(bloc, 0, fichierRecu, i * tailleBloc, bloc.length);
                }

                if (!fichierComplet) {
                    System.out.println("Téléchargement interrompu : fichier incomplet, non sauvegardé.(Le serveur vous a déconnecté)");
                    logger.warning("Téléchargement incomplet, certains blocs sont manquants. (Serveur vous a déconnecté)");
                    for (Future<byte[]> f : resultats) f.cancel(true);
                    blocPool.shutdownNow();
                    return;
                }

                blocPool.shutdown();

                File dossier = new File("Downloads");
                if (!dossier.exists()) dossier.mkdirs();
                String nomLocal = dossier + "/" + UUID.randomUUID() + "_" + nomFichier;

                try (FileOutputStream fos = new FileOutputStream(nomLocal)) {
                    fos.write(fichierRecu);
                }

                System.out.println("Fichier téléchargé et enregistré sous : " + nomLocal);
                logger.info("Fichier téléchargé : " + nomLocal);

                byte[] hash = Digest.md5(nomLocal);
                String hashHex = bytesToHex(hash);
                System.out.println("Hash du fichier téléchargé : " + hashHex);
                logger.info("Hash du fichier : " + hashHex);

                output.writeUTF("HASH " + fileIndex);
                output.writeUTF(hashHex);
                output.flush();

            } catch (SocketException e) {
                System.out.println("Connexion au serveur interrompue. Aucun téléchargement possible.");
            } catch (Exception e) {
                logger.warning("Erreur Client : " + e.getMessage());
                e.printStackTrace();
            }
        });

        clientMainThread.start();

        try {
            clientMainThread.join();
        } catch (InterruptedException e) {
            System.out.println("CLIENT_MAIN interrompu !");
        }

        for (Future<byte[]> f : resultats) {
            f.cancel(true);
        }

        blocPool.shutdownNow();
        System.out.println("Client terminé.");
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
