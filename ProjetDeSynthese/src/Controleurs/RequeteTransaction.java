package Controleurs;

import java.math.BigDecimal;

/**
 * Représente les données reçues lors d'une demande
 * de création de transaction (POST /transactions).
 */
public record RequeteTransaction(

    String reference,     // Référence unique de la transaction

    BigDecimal montant,   // Montant du paiement

    String devise         // Devise du paiement (CAD par défaut)

) {

    // Utiliser CAD si aucune devise n'est fournie
    public RequeteTransaction {
        if (devise == null || devise.isBlank()) {
            devise = "CAD";
        }
    }
}