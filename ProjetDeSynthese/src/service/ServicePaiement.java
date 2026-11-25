
package service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import donnees.Transaction;
import donnees.Compteurs;
import donnees.EtatTransaction;

import Stockage.EnregistrementTransactionBd;
import Traitement.ProcesseurPaiement;
import FileEtCapacite.GestionnaireWorkers;
import FileEtCapacite.TacheTransaction;

/**
 * Gère le parcours d'une transaction, depuis sa réception
 * jusqu'à son traitement ou son échec.
 *
 * Enregistre les transactions et conserve les compteurs
 * nécessaires aux mesures du projet.
 */
public class ServicePaiement {

    private final EnregistrementTransactionBd enregistrementTransactions;
    private final ProcesseurPaiement processeurPaiement;
    private final GestionnaireWorkers gestionnaireWorkers;

    // Compteurs des transactions
    private final AtomicLong transactionsRecues = new AtomicLong(0);
    private final AtomicLong transactionsApprouvees = new AtomicLong(0);
    private final AtomicLong transactionsRefusees = new AtomicLong(0);
    private final AtomicLong transactionsEchouees = new AtomicLong(0);

    /**
     * Constructeur du service de paiement.
     */
    public ServicePaiement(
            EnregistrementTransactionBd enregistrementTransactions,
            ProcesseurPaiement processeurPaiement,
            GestionnaireWorkers gestionnaireWorkers) {

        this.enregistrementTransactions =
                Objects.requireNonNull(enregistrementTransactions);

        this.processeurPaiement =
                Objects.requireNonNull(processeurPaiement);

        this.gestionnaireWorkers =
                Objects.requireNonNull(gestionnaireWorkers);
    }

    /**
     * Reçoit une transaction et la soumet à la file d'attente.
     *
     * @param reference Référence de la transaction
     * @param montant Montant du paiement
     * @param devise Devise utilisée
     * @return Résultat de la transaction disponible plus tard
     */
    public CompletableFuture<Transaction> soumettre(
            String reference,
            BigDecimal montant,
            String devise) {

        // 1. Créer un objet pour recevoir le résultat final
        CompletableFuture<Transaction> resultatEnAttente =
                new CompletableFuture<>();

        // 2. Créer la transaction avec son état initial
        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(),
                reference,
                montant,
                devise,
                EtatTransaction.RECUE,
                null,
                Instant.now(),
                null,
                null,
                null
        );

        // 3. Enregistrer la transaction dans la base de données
        enregistrementTransactions.inserer(transaction);

        // Compter la transaction reçue
        transactionsRecues.incrementAndGet();

        // 4. Créer une tâche pour traiter la transaction
        TacheTransaction tache = new TacheTransaction(
                transaction,
                resultatEnAttente,
                this,
                processeurPaiement
        );

        // 5. Ajouter la tâche dans la file d'attente
        if (!gestionnaireWorkers.soumettre(tache)) {

            // La file est pleine : la transaction échoue
            Transaction resultat = transaction.terminer(
                    EtatTransaction.ECHOUEE,
                    "FILE_PLEINE",
                    0.0,
                    0.0
            );

            terminer(resultat, resultatEnAttente);
        }

        // 6. Retourner le résultat qui sera disponible plus tard
        return resultatEnAttente;
    }

    /**
     * Finalise une transaction et transmet son résultat.
     *
     * Appelée après le traitement, l'expiration
     * ou le refus de mise en file.
     */
    public void terminer(
            Transaction resultat,
            CompletableFuture<Transaction> resultatEnAttente) {

        // Empêcher une double finalisation de la transaction
        synchronized (resultatEnAttente) {

            if (resultatEnAttente.isDone()) {
                return;
            }

            try {

                // 1. Mettre à jour la transaction dans la base
                enregistrementTransactions.mettreAJour(resultat);

                // 2. Mettre à jour le compteur correspondant
                switch (resultat.etat()) {

                    case APPROUVEE ->
                        transactionsApprouvees.incrementAndGet();

                    case REFUSEE ->
                        transactionsRefusees.incrementAndGet();

                    case ECHOUEE ->
                        transactionsEchouees.incrementAndGet();

                    default -> {
                        // Aucun compteur final à modifier
                    }
                }

                // 3. Rendre le résultat disponible
                resultatEnAttente.complete(resultat);

            } catch (Exception e) {

                // Signaler un problème de finalisation
                resultatEnAttente.completeExceptionally(e);
            }
        }
    }

    /**
     * Recherche une transaction à partir de son identifiant.
     *
     * @param id Identifiant de la transaction
     * @return Transaction trouvée ou résultat vide
     */
    public Optional<Transaction> trouver(String id) {

        return enregistrementTransactions.trouverParId(id);
    }

    /**
     * Retourne les compteurs des transactions.
     *
     * @return Nombre de transactions reçues,
     *         approuvées, refusées et échouées
     */
    public Compteurs compteurs() {

        return new Compteurs(
                transactionsRecues.get(),
                transactionsApprouvees.get(),
                transactionsRefusees.get(),
                transactionsEchouees.get()
        );
    }
}
