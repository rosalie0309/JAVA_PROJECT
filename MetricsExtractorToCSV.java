import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MetricsExtractorToCSV {
    public static void main(String[] args) {
        String logPath = "resultats.txt"; // À adapter
        String csvOutputPath = "extractions.csv";

        Pattern patternTemps = Pattern.compile("\\[METRICS] TEMPS=(\\d+)");
        Pattern patternCoupure = Pattern.compile("\\[METRICS] COUPURE_CLIENT=(\\d+)");

        List<Integer> tempsList = new ArrayList<>();
        List<Integer> coupureList = new ArrayList<>();

        try (
            BufferedReader reader = new BufferedReader(new FileReader(logPath));
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvOutputPath))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcherTemps = patternTemps.matcher(line);
                Matcher matcherCoupure = patternCoupure.matcher(line);

                if (matcherTemps.find()) {
                    tempsList.add(Integer.parseInt(matcherTemps.group(1)));
                }

                if (matcherCoupure.find()) {
                    coupureList.add(Integer.parseInt(matcherCoupure.group(1)));
                }
            }

            // Écriture dans le CSV
            writer.write("TEMPS,COUPURE_CLIENT");
            writer.newLine();
            int size = Math.min(tempsList.size(), coupureList.size());
            for (int i = 0; i < size; i++) {
                writer.write(tempsList.get(i) + "," + coupureList.get(i));
                writer.newLine();
            }

            System.out.println("Extraction terminée : " + size + " lignes extraites.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
