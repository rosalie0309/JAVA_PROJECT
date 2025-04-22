import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Client {
    private static final int tailleBloc = 1024;
    private static final int Dc = 4;
    private static final Logger logger = Log.setup("Client", "client.log");
    private static final Scanner sc = new Scanner(System.in);


    public static void main(String[] args) {
        while (true) {
            boolean terminé = lancerClient(); // Essaye une connexion
            if (terminé) break;

            System.out.println("En attente de reconnexion dans 2 secondes...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
        }

        System.out.println("Client terminé.");
    }

    private static boolean lancerClient() {
        ExecutorService blocPool = Executors.newFixedThreadPool(Dc);
        List<Future<byte[]>> resultats = new ArrayList<>();

        try (
            Socket socket = new Socket("127.0.0.1", 12345);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            
        ) {
            String clientId = "client_" + UUID.randomUUID().toString().substring(0, 8);
            output.writeUTF("CLIENT_MAIN " + clientId);
            output.flush();

            String status;
            while (true) {
                try {
                    status = input.readUTF();
                } catch (EOFException e) {
                    System.out.println("Connexion fermée par le serveur. (EOF)");
                    return true;
                } catch (IOException e) {
                    System.out.println("Erreur de lecture depuis le serveur : " + e.getMessage());
                    return true;
                }

                if ("DISCONNECT".equals(status)) {
                    System.out.println("Déconnecté par le serveur. Retour au terminal.");
                    return true;
                } else if ("WELCOME".equals(status)) {
                    System.out.println("Bienvenue, téléchargement possible !");
                    break;
                } else if ("WAIT_IN_QUEUE".equals(status)) {
                    System.out.println("Serveur saturé. Mise en file, reconnexion bientôt...");
                    return false;
                } else if ("WAITING_FOR_HELP".equals(status)) {
                    System.out.println("Serveur surchargé. Tentative de délégation en cours...");
                } else if ("SERVER_BUSY".equals(status)) {
                    System.out.println("Aucun client trusted n’a accepté. Veuillez réessayer plus tard.");
                    return true;
                } else {
                    System.out.println("Réponse inattendue du serveur : " + status);
                    return true;
                }
            }

            // Demande de liste
            output.writeUTF("LIST");
            output.flush();

            String confirmation;
            try {
                confirmation = input.readUTF();
                if ("DISCONNECT".equals(confirmation)) {
                    System.out.println("Déconnecté par le serveur (signal reçu après LIST).");
                    return true;
                } else if (!"LIST_OK".equals(confirmation)) {
                    System.out.println("Réponse inattendue du serveur après LIST : '" + confirmation + "'");
                    return true;
                }
            } catch (IOException e) {
                System.out.println("Le serveur a fermé la connexion. Aucun téléchargement possible.");
                return true;
            }

            int nbFichiers = input.readInt();
            List<String> noms = new ArrayList<>();
            List<Long> tailles = new ArrayList<>();

            System.out.println("Fichiers disponibles :");
            for (int i = 0; i < nbFichiers; i++) {
                try {
                    String nom = input.readUTF();
                    long taille = input.readLong();
                    System.out.println(i + " : " + nom + " (" + taille + " octets)");
                    noms.add(nom);
                    tailles.add(taille);
                } catch (EOFException | SocketException e) {
                    System.out.println("Connexion fermée par le serveur pendant la lecture de la liste.");
                    return true;
                }
            }

            System.out.print("Entrez le numéro du fichier à télécharger : ");
            int fileIndex = Integer.parseInt(sc.nextLine());
            if (fileIndex < 0 || fileIndex >= noms.size()) {
                System.out.println("Indice invalide.");
                return true;
            }

            String nomFichier = noms.get(fileIndex);
            long tailleFichier = tailles.get(fileIndex);
            int nbBlocs = (int) Math.ceil((double) tailleFichier / tailleBloc);
            byte[] fichierRecu = new byte[(int) tailleFichier];

            for (int i = 0; i < nbBlocs; i++) {
                BlocDownloader tache = new BlocDownloader("127.0.0.1", 12345, fileIndex, i, clientId);
                resultats.add(blocPool.submit(tache));
            }

            boolean fichierComplet = true;
        try {
            for (int i = 0; i < resultats.size(); i++) {
                if (checkDisconnection(input)) return true;

                byte[] bloc = resultats.get(i).get();
                if (bloc.length == 0) {
                    System.out.println("Bloc " + i + " vide ou interrompu.");
                    return true;
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Téléchargement interrompu.");
            return true;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketException) {
                System.out.println("Connexion interrompue (Socket fermée). Retour au terminal.");
            } else {
                System.out.println("Erreur pendant le téléchargement : " + cause.getMessage());
            }
            return true;
        }


            if (!fichierComplet) {
                System.out.println("Téléchargement interrompu : fichier incomplet.");
                logger.warning("Téléchargement incomplet, certains blocs sont manquants.");
                for (Future<byte[]> f : resultats) f.cancel(true);
                blocPool.shutdownNow();
                return true;
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

            if (checkDisconnection(input)) return true;


            byte[] hash = Digest.md5(nomLocal);
            String hashHex = bytesToHex(hash);
            System.out.println("Hash du fichier téléchargé : " + hashHex);
            logger.info("Hash du fichier : " + hashHex);

            if (checkDisconnection(input)) return true;

            output.writeUTF("HASH " + fileIndex);
            output.writeUTF(hashHex);
            output.flush();

            return true;

        } catch (Exception e) {
            System.out.println("Erreur lors de l'exécution du client : " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    private static boolean checkDisconnection(DataInputStream input) {
        try {
            if (input.available() == 0) {
                // Pas de message en attente, éviter readUTF bloquant
                Thread.sleep(100); // petite pause
                return false;
            }
    
            String msg = input.readUTF();
            if ("DISCONNECT".equals(msg)) {
                System.out.println("Déconnecté par le serveur (signal reçu).");
                return true;
            } else {
                System.out.println("Message inattendu du serveur : " + msg);
            }
        } catch (EOFException e) {
            System.out.println("Connexion fermée par le serveur. (EOF)");
            return true;
        } catch (SocketException e) {
            System.out.println("Connexion perdue. (Socket fermée)");
            return true;
        } catch (IOException e) {
            System.out.println("Erreur lors de la vérification de déconnexion : " + e.getMessage());
            return true;
        } catch (InterruptedException e) {
            // Pas critique ici
        }
    
        return false;
    }
    

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
