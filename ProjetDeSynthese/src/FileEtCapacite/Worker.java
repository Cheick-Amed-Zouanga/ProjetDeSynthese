package FileEtCapacite;

import java.time.Duration;
import java.time.Instant;
import donnees.EtatWorker;
/**
 * Représente un worker chargé de traiter les transactions.
 * Chaque worker traite une transaction à la fois
 * dans son propre fil d'exécution.
 */
public class Worker {

    private final int id;                        // Identifiant du worker
    private volatile EtatWorker etat;            // État actuel du worker
    private volatile boolean occupe;             // Indique si le worker traite une transaction
    private final GestionnaireWorkers gestionnaire; // Gestionnaire des workers et de la file

    // Constructeur
    public Worker(int id, EtatWorker etatDepart,
                  GestionnaireWorkers gestionnaire) {

        this.id = id;
        this.etat = etatDepart;
        this.occupe = false;
        this.gestionnaire = gestionnaire;
    }

    /**
     * Boucle principale du worker.
     * Récupère et traite les transactions une par une.
     */
    public void boucle() {

        try {
            // Continuer tant que le worker est actif
            while (etat == EtatWorker.ACTIF) {

                // Attendre une tâche pendant 100 ms maximum
                TacheFile tache = gestionnaire.prendreTache(100);

                // Si aucune tâche n'est disponible, recommencer
                if (tache == null) {
                    continue;
                }

                // Indiquer que le worker est occupé
                occupe = true;

                try {

                    // Calculer le temps passé dans la file
                    double attenteFileMs = Duration.between(
                            tache.instantEntree(),
                            Instant.now()
                    ).toNanos() / 1_000_000.0;

                    // Exécuter le traitement de la transaction
                    tache.executer(Math.max(0, attenteFileMs));

                } finally {

                    // Le worker n'est plus occupé
                    occupe = false;
                }
            }

        } catch (InterruptedException e) {

            // Conserver le signal d'interruption
            Thread.currentThread().interrupt();

        } finally {

            // Arrêter le worker s'il est en cours de retrait
            if (etat == EtatWorker.EN_VIDANGE) {
                gestionnaire.arreter(this);
            }
        }
    }

    /**
     * Retourne l'identifiant du worker.
     */
    public int id() {
        return id;
    }

    /**
     * Retourne l'état actuel du worker.
     */
    public EtatWorker etat() {
        return etat;
    }

    /**
     * Modifie l'état du worker.
     */
    public void changerEtat(EtatWorker nouvelEtat) {
        this.etat = nouvelEtat;
    }

    /**
     * Indique si le worker traite une transaction.
     */
    public boolean estOccupe() {
        return occupe;
    }
}
