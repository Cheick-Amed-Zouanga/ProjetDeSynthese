
package Traitement;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Random;

import Parametres.ParametresTraitement;
import donnees.PlanTraitement;
import donnees.EtatTransaction;

/**
 * Génère le plan de traitement d'une transaction.
 *
 * Calcule une durée simulée avec une loi log-normale
 * et détermine si la transaction est approuvée ou refusée.
 *
 * Une même transaction produit toujours le même résultat
 * lorsque les paramètres sont identiques.
 */
public class GenerateurPlanTraitement {

    private final ParametresTraitement parametres;

    // Constructeur
    public GenerateurPlanTraitement(ParametresTraitement parametres) {
        this.parametres = Objects.requireNonNull(parametres);
    }

    /**
     * Génère un plan de traitement pour une transaction.
     *
     * @param cle Référence unique de la transaction
     * @param montant Montant de la transaction
     * @return Plan contenant la durée, la décision et le motif
     */
    public PlanTraitement planifier(String cle, BigDecimal montant) {

        Objects.requireNonNull(cle);
        Objects.requireNonNull(montant);

        // 1. Initialiser le générateur pseudoaléatoire
        long graine = 31L * parametres.graine() + cle.hashCode();

        Random hasard = new Random(graine);

        // 2. Calculer la durée avec une loi log-normale
        double cv = parametres.coefVariation();

        double sigma = Math.sqrt(Math.log(1 + cv * cv));

        double mu = Math.log(parametres.dureeMoyenneMs())
                - (sigma * sigma) / 2.0;

        double duree = Math.exp(
                mu + sigma * hasard.nextGaussian()
        );

        // 3. Limiter la durée entre le minimum et le maximum
        duree = Math.max(
                parametres.dureeMinMs(),
                Math.min(duree, parametres.dureeMaxMs())
        );

        // 4. Ajouter un délai pour les gros montants
        if (montant.compareTo(parametres.seuilGrosMontant()) > 0) {
            duree += parametres.delaiGrosMontantMs();
        }

        // 5. Générer un nombre pour simuler le risque
        double tirage = hasard.nextDouble();

        // 6. Refuser si le montant dépasse la limite
        if (montant.compareTo(parametres.montantMax()) > 0) {

            return new PlanTraitement(
                    EtatTransaction.REFUSEE,
                    duree,
                    "LIMITE_MONTANT"
            );
        }

        // 7. Refuser selon la probabilité de risque
        if (tirage < parametres.tauxRefusRisque()) {

            return new PlanTraitement(
                    EtatTransaction.REFUSEE,
                    duree,
                    "REGLE_RISQUE"
            );
        }

        // 8. Approuver la transaction
        return new PlanTraitement(
                EtatTransaction.APPROUVEE,
                duree,
                "AUTORISEE"
        );
    }
}
