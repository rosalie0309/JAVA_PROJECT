import java.io.FileInputStream;
import java.security.MessageDigest;

public class Digest {

    /**
     * Calcule le MD5 d'un fichier donné.
     * @param filepath chemin du fichier
     * @return string MD5 hexadécimal
     */
    public static String computeMD5(String filepath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(filepath);
            byte[] buffer = new byte[1024];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            fis.close();

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Erreur calcul MD5: " + e.getMessage());
            return null;
        }
    }
} 