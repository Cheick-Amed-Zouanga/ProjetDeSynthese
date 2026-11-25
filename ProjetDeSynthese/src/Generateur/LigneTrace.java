package Generateur;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Représente une transaction fictive à envoyer à un instant précis.
 */
public record LigneTrace(
        double instant,       // Instant prévu d'envoi, en secondes depuis le début
        String reference,     // Référence reproductible de la transaction
        BigDecimal montant,   // Montant fictif de la transaction
        String devise        // Devise, par exemple CAD
) {
    public LigneTrace {
        if (!Double.isFinite(instant) || instant < 0) {
            throw new IllegalArgumentException("Instant invalide");
        }
        Objects.requireNonNull(reference);
        Objects.requireNonNull(montant);
        Objects.requireNonNull(devise);
    }
}
