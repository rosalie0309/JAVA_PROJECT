import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Client {
    private static final int tailleBloc = 1024;
    private static final int Dc = 4; // nombre de téléchargements parallèles
    private static final Logger logger = Log.setup("Client", "client.log");
    
    public static void main(String[] args) {
        try (
        Socket socket = new Socket("127.0.0.1", 12345);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        DataInputStream input = new DataInputStream(socket.getInputStream());
        Scanner sc = new Scanner(System.in);
        ) {
            output.writeUTF("CLIENT_MAIN");
            output.flush();

            // Demande de la liste des fichiers
            output.writeUTF("LIST");
            output.flush();

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

            if(isSocketClosed(input, output)) return;
            
            String nomFichier = noms.get(fileIndex);
            long tailleFichier = tailles.get(fileIndex);
            int nbBlocs = (int) Math.ceil((double) tailleFichier / tailleBloc);
            
            byte[] fichierRecu = new byte[(int) tailleFichier];
            ExecutorService pool = Executors.newFixedThreadPool(Dc);
            List<Future<byte[]>> resultats = new ArrayList<>();

            // Téléchargement des blocs
            for (int i = 0; i < nbBlocs; i++) {
                BlocDownloader tache = new BlocDownloader("127.0.0.1", 12345, fileIndex, i);
                Future<byte[]> future = pool.submit(tache);
                resultats.add(future);
                if(isSocketClosed(input, output)) {
                    pool.shutdownNow(); // Arrêter le pool si le socket est fermé
                    System.out.println("Téléchargement annulé.");
                    return;
                };
            }
            
            // Réassemblage
            for (int i = 0; i < resultats.size(); i++) {
                byte[] bloc = resultats.get(i).get();
                System.arraycopy(bloc, 0, fichierRecu, i * tailleBloc, bloc.length);
                if(isSocketClosed(input, output)) {
                    pool.shutdownNow(); // Arrêter le pool si le socket est fermé
                    System.out.println("Téléchargement annulé.");
                    return;
                };
            }
            
            pool.shutdown();
            
            // Sauvegarde du fichier
            File dossier = new File("Downloads");
            if (!dossier.exists()) {
                dossier.mkdirs();
            }
            String nomLocal = dossier + "/" + UUID.randomUUID().toString() + "_" + nomFichier;
            try (FileOutputStream fos = new FileOutputStream(nomLocal)) {
                fos.write(fichierRecu);
            }
            System.out.println("Fichier téléchargé et enregistré sous : " + nomLocal);
            logger.info("Fichier téléchargé et enregistré sous : " + nomLocal);

            if(isSocketClosed(input, output)) return;
            
            // Envoi du hash pour vérification (réutilisation du socket initial)
            byte[] hash = Digest.md5(nomLocal);
            String hashHex = bytesToHex(hash);
            System.out.println("Hash du fichier téléchargé : " + hashHex);
            logger.info("Hash du fichier téléchargé : " + hashHex);

            output.writeUTF("HASH " + fileIndex);  // Envoi de la commande HASH
            output.writeUTF(hashHex);  // Envoi du hash au serveur

            socket.close();
            output.close();
            input.close();
            sc.close();

        } catch (SocketException e) {
            System.out.println("Serveur fermé ou connexion interrompue.");
            logger.warning("Serveur fermé ou connexion interrompue.");
        } catch (IOException e) {
            logger.warning("Erreur Client : " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            logger.warning("Erreur Client : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isSocketClosed(DataInputStream in, DataOutputStream out) {
        try {
            out.writeUTF("PING");
            if(in.readUTF().equals("PONG")) {
                return false;
            } 
        } catch (IOException e) {
            System.err.println("Socket fermé");
        }
        return true;
    }
}