package Generateur;

/**
 * Décrit une période du scénario de charge.
 * Le débit peut rester constant ou varier progressivement.
 */
public record PhaseCharge(
        String nom,          // Nom de la période : base, rafale_x4, etc.
        double debut,       // Début de la période, en secondes
        double fin,         // Fin de la période, en secondes
        double debitDebut,  // Transactions/seconde au début
        double debitFin     // Transactions/seconde à la fin
) {
    public PhaseCharge {
        if (nom == null || nom.isBlank() || !Double.isFinite(debut)
                || !Double.isFinite(fin) || debut < 0 || fin <= debut
                || !Double.isFinite(debitDebut) || debitDebut < 0
                || !Double.isFinite(debitFin) || debitFin < 0) {
            throw new IllegalArgumentException("Phase de charge invalide");
        }
    }
}
