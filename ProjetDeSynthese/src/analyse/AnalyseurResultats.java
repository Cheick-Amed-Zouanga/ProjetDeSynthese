package analyse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import Generateur.ProfilCharge;

/**
 * Lit les fichiers d'un essai et calcule débit, échecs,
 * percentiles, coût fictif et respect des objectifs de service.
 */
public class AnalyseurResultats {

    // Hypothèses de l'équipe, et non des tarifs ou seuils bancaires vérifiés.
    private static final double PRIX_MINUTE_WORKER = 1.0; // 1 $/worker-minute
    private static final double SLO_P95_MS = 250.0;       // Seuil provisoire
    private static final double SLO_ECHECS_MAX = 1.0;     // 1 % maximum

    private record PointMesure(double secondes, double workersSecondes,
                               boolean finMesure) { }

    /** Calcule et écrit summary.csv à partir des fichiers de l'essai. */
    public ResumeEssai calculerResume(Path dossier, ProfilCharge profil)
            throws IOException {
        List<Double> latences = new ArrayList<>();
        int envoyees = 0;
        int reussies = 0;

        List<String> lignes = Files.readAllLines(
                dossier.resolve("requests.csv"), StandardCharsets.UTF_8);
        for (int i = 1; i < lignes.size(); i++) {
            String[] colonnes = lignes.get(i).split(",", -1);
            if (colonnes.length < 7 || "echauffement".equals(colonnes[6])) {
                continue;
            }
            envoyees++;
            String etat = colonnes[4];
            if ("APPROUVEE".equals(etat) || "REFUSEE".equals(etat)) {
                reussies++; // Un refus métier est bien une transaction traitée.
            }
            int codeHttp = Integer.parseInt(colonnes[3]);
            if (codeHttp > 0 && !colonnes[2].isBlank()) {
                // Une requête sans réponse compte comme échec, pas comme latence observée.
                latences.add(Double.parseDouble(colonnes[2]));
            }
        }

        int echouees = envoyees - reussies;
        double dureeMesure = profil.duree() - profil.echauffement();
        double debitReussi = reussies / dureeMesure;
        double tauxEchec = envoyees == 0 ? 100.0 : echouees * 100.0 / envoyees;

        Collections.sort(latences);
        double p50 = percentile(latences, 0.50);
        double p95 = percentile(latences, 0.95);
        double p99 = percentile(latences, 0.99);

        // Calculer les workers-secondes durant la fenêtre, sans échauffement ni grâce.
        List<PointMesure> points = lireMesures(dossier.resolve("timeline.csv"));
        double debutWorkersSecondes = valeurA(points, profil.echauffement());
        double finWorkersSecondes = points.stream()
                .filter(PointMesure::finMesure)
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Repère FIN_MESURE absent de timeline.csv"))
                .workersSecondes();
        double workersSecondes = Math.max(0, finWorkersSecondes - debutWorkersSecondes);
        double cout = workersSecondes / 60.0 * PRIX_MINUTE_WORKER;
        double coutPour1000 = reussies == 0 ? Double.NaN : cout / reussies * 1000;

        boolean sloRespecte = Double.isFinite(p95)
                && p95 <= SLO_P95_MS && tauxEchec <= SLO_ECHECS_MAX;

        ResumeEssai resume = new ResumeEssai(envoyees, reussies, echouees,
                debitReussi, tauxEchec, p50, p95, p99,
                cout, coutPour1000, sloRespecte);
        ecrireResume(dossier.resolve("summary.csv"), resume);
        return resume;
    }

    /** Percentile nearest-rank : position ceil(p × n), indices Java à partir de 0. */
    private double percentile(List<Double> latencesTriees, double probabilite) {
        if (latencesTriees.isEmpty()) {
            return Double.NaN;
        }
        int indice = Math.max(0, (int) Math.ceil(probabilite
                * latencesTriees.size()) - 1);
        return latencesTriees.get(Math.min(indice, latencesTriees.size() - 1));
    }

    /** Lit les compteurs de capacité écrits chaque seconde par CollecteurMesures. */
    private List<PointMesure> lireMesures(Path fichier) throws IOException {
        List<PointMesure> points = new ArrayList<>();
        List<String> lignes = Files.readAllLines(fichier, StandardCharsets.UTF_8);
        for (int i = 1; i < lignes.size(); i++) {
            String[] colonnes = lignes.get(i).split(",", -1);
            if (colonnes.length < 10 || colonnes[9].isBlank()) {
                continue;
            }
            double temps = Double.parseDouble(colonnes[0]);
            double secondesWorkers = Double.parseDouble(
                    colonnes[9].replace("\"", ""));
            points.add(new PointMesure(temps, secondesWorkers,
                    "FIN_MESURE".equals(colonnes[1])));
        }
        points.sort(Comparator.comparingDouble(PointMesure::secondes));
        if (points.isEmpty()) {
            throw new IOException("Aucune mesure de capacité valide");
        }
        return points;
    }

    /** Interpole le compteur de workers-secondes à l'instant de fin d'échauffement. */
    private double valeurA(List<PointMesure> points, double secondes) {
        PointMesure precedent = points.get(0);
        for (PointMesure suivant : points) {
            if (suivant.secondes() >= secondes) {
                if (suivant.secondes() == precedent.secondes()) {
                    return suivant.workersSecondes();
                }
                double part = Math.max(0, Math.min(1,
                        (secondes - precedent.secondes())
                        / (suivant.secondes() - precedent.secondes())));
                return precedent.workersSecondes()
                        + part * (suivant.workersSecondes()
                        - precedent.workersSecondes());
            }
            precedent = suivant;
        }
        return points.get(points.size() - 1).workersSecondes();
    }

    /** Enregistre les indicateurs pour une analyse statistique ultérieure. */
    private void ecrireResume(Path fichier, ResumeEssai r) throws IOException {
        try (BufferedWriter sortie = Files.newBufferedWriter(
                fichier, StandardCharsets.UTF_8)) {
            sortie.write("transactionsEnvoyees,transactionsReussies,transactionsEchouees,"
                    + "debitReussi,tauxEchec,p50Ms,p95Ms,p99Ms,coutTotal,"
                    + "coutPour1000,sloRespecte\n");
            sortie.write(String.format(Locale.ROOT,
                    "%d,%d,%d,%.6f,%.6f,%s,%s,%s,%.6f,%s,%b%n",
                    r.transactionsEnvoyees(), r.transactionsReussies(),
                    r.transactionsEchouees(), r.debitReussi(), r.tauxEchec(),
                    nombre(r.p50Ms()), nombre(r.p95Ms()), nombre(r.p99Ms()),
                    r.coutTotal(), nombre(r.coutPour1000()), r.sloRespecte()));
        }
    }

    private String nombre(double valeur) {
        return Double.isFinite(valeur)
                ? String.format(Locale.ROOT, "%.6f", valeur) : "";
    }
}
