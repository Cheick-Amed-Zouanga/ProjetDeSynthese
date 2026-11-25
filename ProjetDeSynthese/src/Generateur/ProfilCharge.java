package Generateur;

import java.util.List;

/**
 * Définit un scénario d'arrivée des transactions.
 * Il contient des phases et une période d'échauffement.
 */
public record ProfilCharge(
        String nom,                 // Identifiant du profil : stable, croissance, rafales
        double duree,              // Durée totale de l'essai, en secondes
        double echauffement,       // Période exclue des mesures, en secondes
        List<PhaseCharge> phases   // Phases de demande du profil
) {
    public ProfilCharge {
        if (nom == null || nom.isBlank() || !Double.isFinite(duree)
                || duree <= 0 || !Double.isFinite(echauffement)
                || echauffement < 0 || echauffement >= duree
                || phases == null || phases.isEmpty()) {
            throw new IllegalArgumentException("Profil de charge invalide");
        }
        phases = List.copyOf(phases); // Empêcher une modification extérieure.
    }

    /** Calcule le débit prévu à un instant donné, en transactions/seconde. */
    public double debit(double secondes) {
        for (PhaseCharge phase : phases) {
            if (secondes >= phase.debut() && secondes < phase.fin()) {
                // Interpolation linéaire : utile pendant une montée progressive.
                double progression = (secondes - phase.debut())
                        / (phase.fin() - phase.debut());
                return phase.debitDebut()
                        + (phase.debitFin() - phase.debitDebut()) * progression;
            }
        }
        return 0.0; // En dehors des phases, aucune nouvelle transaction.
    }

    /** Retourne le débit maximal théorique du profil. */
    public double debitMax() {
        double maximum = 0.0;
        for (PhaseCharge phase : phases) {
            maximum = Math.max(maximum,
                    Math.max(phase.debitDebut(), phase.debitFin()));
        }
        return maximum;
    }

    /** Retourne le nom de la phase correspondant à l'instant indiqué. */
    public String nomPhase(double secondes) {
        if (secondes < echauffement) {
            return "echauffement";
        }
        for (PhaseCharge phase : phases) {
            if (secondes >= phase.debut() && secondes < phase.fin()) {
                return phase.nom();
            }
        }
        return "hors_profil";
    }
}
