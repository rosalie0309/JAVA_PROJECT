import java.io.*;
import java.util.logging.Logger;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    private int tailleBloc = 1024;
    private int Dc = 4; // nombre de téléchargements parallèles
    private Logger logger = Log.setup("Client", "client.log");
    
    public static void main(String[] args) {
        Client c = new Client();
        try (
        Socket socket = new Socket("127.0.0.1", 12345);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        DataInputStream input = new DataInputStream(socket.getInputStream());
        Scanner sc = new Scanner(System.in);
        ) {

            output.writeUTF("CLIENT_MAIN");
            output.flush();

            int fileIndex = -1;

            Thread monitorThread = verifyThread(input, output); // Lancement du thread de vérification de la connexion
            monitorThread.setDaemon(true); // Permet de ne pas bloquer la fermeture du programme
            
            String slave = input.readUTF();
            if(slave.equals("CLIENT_SLAVE")) monitorThread.start();
        

            if(args.length > 0) {
                for (String arg : args) {
                    if (arg.startsWith("--file=")) fileIndex = Integer.parseInt(arg.split("=")[1]);
                    if (arg.startsWith("--DC=")) c.Dc = Integer.parseInt(arg.split("=")[1]);
                }
            }

            if(args.length == 0 || fileIndex == -1) {
                // Demande de la liste des fichiers
                output.writeUTF("LIST");
                output.flush();

                int nbFichiers = input.readInt();
                System.out.println("Nombre de fichiers disponibles : " + nbFichiers);
                
                System.out.println("Fichiers disponibles :");
                for (int i = 0; i < nbFichiers; i++) {
                    String nom = input.readUTF();
                    long taille = input.readLong();
                    System.out.println(i + " : " + nom + " (" + taille + " octets)");
                }       
                
                System.out.print("Entrez le numéro du fichier à télécharger : ");
                fileIndex = Integer.parseInt(sc.nextLine());
                if (fileIndex < 0 || fileIndex >= nbFichiers) {
                    System.out.println("Indice invalide.");
                    monitorThread.interrupt();
                    return;
                }
            }
            
            output.writeUTF("GET_INFO " + fileIndex);
            output.flush();
            String nomFichier = input.readUTF();
            long tailleFichier = input.readLong();

            int nbBlocs = (int) Math.ceil((double) tailleFichier / c.tailleBloc); //Divise la taille du fichier par la taille d'un bloc et arrondit à l'entier supérieur
            byte[] fichierRecu = new byte[(int) tailleFichier];
            ExecutorService pool = Executors.newFixedThreadPool(c.Dc);
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
                System.arraycopy(bloc, 0, fichierRecu, i * c.tailleBloc, bloc.length);
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
            c.logger.info("Fichier téléchargé et enregistré sous : " + nomLocal);
            
            // Envoi du hash pour vérification (réutilisation du socket initial)
            byte[] hash = Digest.md5(nomLocal);
            String hashHex = bytesToHex(hash);
            System.out.println("Hash du fichier téléchargé : " + hashHex);
            c.logger.info("Hash du fichier téléchargé : " + hashHex);

            output.writeUTF("HASH " + fileIndex);  // Envoi de la commande HASH
            output.writeUTF(hashHex);  // Envoi du hash au serveur

            String message = input.readUTF(); // Réponse du serveur
            System.out.println(message); 
            c.logger.info(message); 

            monitorThread.interrupt(); // Interruption du thread de vérification de la connexion
            socket.close();
            output.close();
            input.close();
            sc.close();

        } catch (SocketException e) {
            System.out.println("Serveur fermé ou connexion interrompue.");
            c.logger.warning("Serveur fermé ou connexion interrompue.");
        } catch (IOException e) {
            c.logger.warning("Erreur Client : " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            c.logger.warning("Erreur Client : " + e.getMessage());
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

    public static Thread verifyThread(DataInputStream input, DataOutputStream output) {
        Thread monitorThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(500); // Vérifier toutes les 200 ms pour éviter de surcharger le CPU
                    if (isSocketClosed(input, output)) {
                        System.out.println("\n[INFO] Socket fermé. Fin du programme.");
                        System.exit(0);
                    }
                }
            } catch (InterruptedException ignored) {}
        });
        return monitorThread;
    }
}