package Parametres;

import java.math.BigDecimal;
/**
 * Regroupe les paramètres utilisés pour simuler
 * la durée et la décision de traitement d'une transaction.
 *
 * Les valeurs sont chargées depuis application.yml.
 */
public record ParametresTraitement(

    long graine,                    // Graine pour reproduire les mêmes résultats (2026)

    double dureeMoyenneMs,          // Durée moyenne théorique d'un traitement (80 ms)

    double coefVariation,           // Variation des durées autour de la moyenne (0,5)

    double dureeMinMs,              // Durée minimale du traitement (10 ms)

    double dureeMaxMs,              // Durée maximale avant supplément (1000 ms)

    BigDecimal seuilGrosMontant,    // Montant à partir duquel un délai est ajouté (500 $)

    double delaiGrosMontantMs,      // Délai ajouté pour les gros montants (40 ms)

    double tauxRefusRisque,         // Probabilité de refus pour risque (3 %)

    BigDecimal montantMax           // Montant maximal autorisé (5000 $)

) {
}
