
package Traitement;

import donnees.Transaction;
import donnees.PlanTraitement;
import donnees.EtatTransaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Simule le traitement d'une transaction de paiement.
 *
 * Effectue des calculs SHA-256 pour représenter une charge CPU,
 * puis attend pour simuler la réponse d'une banque.
 */
public class ProcesseurPaiement {

    private final GenerateurPlanTraitement modele;
    private final int nombreHachages;

    // Constructeur
    public ProcesseurPaiement(
            GenerateurPlanTraitement modele,
            int nombreHachages) {

        this.modele = modele;
        this.nombreHachages = nombreHachages;
    }

    /**
     * Traite une transaction et retourne son état final.
     */
    public Transaction traiter(
            Transaction transaction,
            double attenteFileMs) {

        long debut = System.nanoTime();

        try {

        	// 1. Générer le plan de traitement
        	PlanTraitement plan = modele.planifier(
        	    transaction.reference(),
        	    transaction.montant()
        	);
            // 2. Préparer les données de la transaction
            String donnees = transaction.id()
                    + transaction.reference()
                    + transaction.montant()
                    + transaction.devise();

            // 3. Simuler une charge CPU avec SHA-256
            MessageDigest sha256 =
                    MessageDigest.getInstance("SHA-256");

            byte[] resultat =
                    donnees.getBytes(StandardCharsets.UTF_8);

            // Exécuter le nombre de hachages configuré
            for (int i = 0; i < nombreHachages; i++) {
                resultat = sha256.digest(resultat);
            }

            // 4. Simuler l'attente de la réponse bancaire
            Thread.sleep(
                Math.max(
                    0L,
                    Math.round(plan.dureePasserelleMs())
                )
            );

            // 5. Mesurer le temps réel du traitement
            double tempsTraitementMs =
                    (System.nanoTime() - debut) / 1_000_000.0;

            // 6. Retourner la transaction terminée
            return transaction.terminer(
                plan.decision(),
                plan.motif(),
                attenteFileMs,
                tempsTraitementMs
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return transaction.terminer(
                EtatTransaction.ECHOUEE,
                "ERREUR_INTERNE",
                attenteFileMs,
                (System.nanoTime() - debut) / 1_000_000.0
            );

        } catch (Exception e) {

            return transaction.terminer(
                EtatTransaction.ECHOUEE,
                "ERREUR_INTERNE",
                attenteFileMs,
                (System.nanoTime() - debut) / 1_000_000.0
            );
        }
    }
}
