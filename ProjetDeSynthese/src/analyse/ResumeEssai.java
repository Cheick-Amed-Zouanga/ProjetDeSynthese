package analyse;

/**
 * Regroupe les indicateurs calculés après un essai de charge.
 */
public record ResumeEssai(
        int transactionsEnvoyees,   // Nombre de transactions de la fenêtre mesurée
        int transactionsReussies,  // Transactions APPROUVEE ou REFUSEE
        int transactionsEchouees,  // Échecs techniques et absences de réponse
        double debitReussi,        // Transactions correctement traitées par seconde
        double tauxEchec,          // Pourcentage d'échecs techniques
        double p50Ms,              // 50e percentile de latence, en ms
        double p95Ms,              // 95e percentile de latence, en ms
        double p99Ms,              // 99e percentile de latence, en ms
        double coutTotal,          // Coût fictif de la fenêtre de mesure, en dollars
        double coutPour1000,       // Coût fictif pour 1 000 transactions réussies
        boolean sloRespecte       // Indique si les objectifs SLO retenus sont tenus
) {
}
