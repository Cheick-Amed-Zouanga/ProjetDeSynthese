package Controleurs;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import donnees.Transaction;
import service.ServicePaiement;
import donnees.EtatTransaction;


/**
 * Contrôle les demandes de création et
 * de consultation des transactions.
 */
public class ControleurTransactions {

    private final ServicePaiement servicePaiement;

    // Constructeur
    public ControleurTransactions(ServicePaiement servicePaiement) {
        this.servicePaiement = servicePaiement;
    }

    /**
     * Vérifie et soumet une nouvelle transaction.
     * Logique associée à POST /transactions.
     */
    public CompletableFuture<ReponseHttp> creer(
            RequeteTransaction requete) {

        // Vérifier les informations reçues
        if (requete == null
                || requete.reference() == null
                || requete.reference().isBlank()
                || requete.reference().length() > 100
                || requete.montant() == null
                || requete.montant().signum() <= 0
                || (requete.devise() != null
                    && !requete.devise().isBlank()
                    && !requete.devise().matches("[A-Za-z]{3}"))) {

            return CompletableFuture.completedFuture(
                new ReponseHttp(400,
                    Map.of("erreur", "requête invalide"))
            );
        }

        // Définir la devise
        String devise = requete.devise();

        if (devise == null || devise.isBlank()) {
            devise = "CAD";
        }

        // Arrondir le montant
        BigDecimal montant = requete.montant()
                .setScale(2, RoundingMode.HALF_UP);

        if (montant.signum() <= 0) {
            return CompletableFuture.completedFuture(
                new ReponseHttp(400,
                    Map.of("erreur", "requête invalide"))
            );
        }

        // Soumettre la transaction
        CompletableFuture<Transaction> resultatEnAttente =
                servicePaiement.soumettre(
                        requete.reference(),
                        montant,
                        devise
                );

        // Construire la réponse lorsque le résultat est disponible
        return resultatEnAttente.thenApply(transaction -> {

            if (transaction.etat() == EtatTransaction.ECHOUEE) {
                return new ReponseHttp(503, transaction);
            }

            return new ReponseHttp(201, transaction);
        });
    }

    /**
     * Recherche une transaction par son identifiant.
     * Logique associée à GET /transactions/{id}.
     */
    public ReponseHttp consulter(String id) {

        return servicePaiement.trouver(id)
                .map(transaction -> new ReponseHttp(200, transaction))
                .orElseGet(() -> new ReponseHttp(404, null));
    }

    /**
     * Représente le statut HTTP et le contenu
     * de la réponse.
     */
    public record ReponseHttp(int statut, Object corps) {
    }
}