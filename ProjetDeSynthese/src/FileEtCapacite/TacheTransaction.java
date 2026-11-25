
package FileEtCapacite;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import donnees.Transaction;
import donnees.EtatTransaction;
import Traitement.ProcesseurPaiement;
import service.ServicePaiement;

/**
 * Représente une transaction placée dans la file d'attente.
 *
 * Permet de traiter la transaction lorsqu'un worker
 * est disponible ou de la faire échouer si elle
 * dépasse le temps maximal d'attente.
 */
public class TacheTransaction implements TacheFile {

    private final Transaction transaction;
    // Transaction en attente de traitement

    private final CompletableFuture<Transaction> resultatEnAttente;
    // Contient le résultat final lorsqu'il sera disponible

    private final ServicePaiement servicePaiement;
    // Service chargé d'enregistrer le résultat de la transaction

    private final ProcesseurPaiement processeurPaiement;
    // Exécute le traitement simulé de la transaction

    private final Instant instantEntree;
    // Date et heure d'entrée dans la file d'attente

    /**
     * Constructeur de la tâche.
     */
    public TacheTransaction(
            Transaction transaction,
            CompletableFuture<Transaction> resultatEnAttente,
            ServicePaiement servicePaiement,
            ProcesseurPaiement processeurPaiement) {

        this.transaction = Objects.requireNonNull(transaction);
        this.resultatEnAttente = Objects.requireNonNull(resultatEnAttente);
        this.servicePaiement = Objects.requireNonNull(servicePaiement);
        this.processeurPaiement = Objects.requireNonNull(processeurPaiement);

        // Enregistrer l'instant d'entrée dans la file
        this.instantEntree = Instant.now();
    }

    /**
     * Retourne la date et l'heure d'entrée dans la file.
     */
    @Override
    public Instant instantEntree() {
        return instantEntree;
    }

    /**
     * Exécute la transaction lorsqu'un worker la récupère.
     *
     * @param attenteFileMs Temps passé dans la file en ms
     */
    @Override
    public void executer(double attenteFileMs) {

        // Effectuer le traitement simulé de la transaction
        Transaction resultat = processeurPaiement.traiter(
                transaction,
                attenteFileMs
        );

        // Enregistrer et transmettre le résultat final
        servicePaiement.terminer(
                resultat,
                resultatEnAttente
        );
    }

    /**
     * Fait échouer une transaction lorsqu'elle dépasse
     * le temps maximal d'attente dans la file.
     *
     * @param attenteFileMs Temps passé dans la file en ms
     */
    @Override
    public void expirer(double attenteFileMs) {

        // Marquer la transaction comme échouée
        Transaction resultat = transaction.terminer(
                EtatTransaction.ECHOUEE,
                "ATTENTE_DEPASSEE",
                attenteFileMs,
                0.0
        );

        // Enregistrer et transmettre le résultat final
        servicePaiement.terminer(
                resultat,
                resultatEnAttente
        );
    }
}
