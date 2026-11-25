package Generateur;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prépare les transactions à envoyer pendant un essai.
 * Un même profil et une même graine donnent la même trace.
 */
public class GenerateurTrace {

    /** Génère les arrivées selon un processus de Poisson non homogène. */
    public List<LigneTrace> generer(ProfilCharge profil, long graine) {
        Random hasard = new Random(graine);
        double debitMax = profil.debitMax();
        List<LigneTrace> trace = new ArrayList<>();

        if (debitMax <= 0) {
            return trace; // Profil sans demande.
        }

        double instant = 0.0;
        int numero = 0;

        while (true) {
            // Tirer le temps avant la prochaine arrivée possible (loi exponentielle).
            double tirage = hasard.nextDouble();
            instant += -Math.log1p(-tirage) / debitMax;
            if (instant >= profil.duree()) {
                break;
            }

            // Garder l'arrivée avec une probabilité fonction du débit local.
            if (hasard.nextDouble() < profil.debit(instant) / debitMax) {
                numero++;

                // Montant log-normal de médiane 45 $ ; hypothèse expérimentale.
                double valeurMontant = 45.0 * Math.exp(hasard.nextGaussian());
                BigDecimal montant = BigDecimal.valueOf(valeurMontant)
                        .setScale(2, RoundingMode.HALF_UP);

                String reference = String.format(Locale.ROOT,
                        "%s-s%d-%06d", profil.nom(), graine, numero);
                trace.add(new LigneTrace(instant, reference, montant, "CAD"));
            }
        }
        return trace;
    }

    /** Enregistre la trace pour pouvoir vérifier ou rejouer l'essai. */
    public void ecrireCsv(List<LigneTrace> trace, Path fichier) throws IOException {
        if (fichier.getParent() != null) {
            Files.createDirectories(fichier.getParent());
        }
        try (BufferedWriter sortie = Files.newBufferedWriter(
                fichier, StandardCharsets.UTF_8)) {
            sortie.write("instant,reference,montant,devise\n");
            for (LigneTrace ligne : trace) {
                sortie.write(String.format(Locale.ROOT, "%.9f,%s,%s,%s%n",
                        ligne.instant(), ligne.reference(),
                        ligne.montant().toPlainString(), ligne.devise()));
            }
        }
    }
}
