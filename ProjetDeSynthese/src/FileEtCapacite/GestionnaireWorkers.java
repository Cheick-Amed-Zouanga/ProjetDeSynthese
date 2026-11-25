package FileEtCapacite;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Controleurs.RequeteTransaction;
import Parametres.ParametresCapacite;
import donnees.EtatCourant;
import donnees.EtatWorker;
import donnees.Evenement;

/**
 * Gère la file d'attente et le nombre de workers.
 *
 * Permet d'ajouter ou de retirer des workers selon
 * la capacité demandée, tout en suivant le temps payé.
 */
public class GestionnaireWorkers {

    private final BlockingQueue<TacheFile> fileAttente;
    private final List<Worker> listeWorkers;
    private final DeterminerCoutWorker compteurTempsPaye;
    private final List<Evenement> historiqueEvenements;
    private final ParametresCapacite parametres;
    private final ScheduledExecutorService planificateur;

    private int capaciteCible;
    private int prochainIdWorker;

    // Constructeur
    public GestionnaireWorkers(
            ParametresCapacite parametres,
            DeterminerCoutWorker compteurTempsPaye) {

        this.parametres = parametres;
        this.compteurTempsPaye = compteurTempsPaye;

        // Créer une file FIFO avec une capacité limitée
        this.fileAttente = new ArrayBlockingQueue<>(
                parametres.tailleFile()
        );

        this.listeWorkers = new ArrayList<>();
        this.historiqueEvenements = new ArrayList<>();

        // Planifie les actions qui doivent s'exécuter plus tard
        this.planificateur =
                Executors.newSingleThreadScheduledExecutor();

        this.capaciteCible = 0;
        this.prochainIdWorker = 1;
    }

    /**
     * Démarre directement les workers initiaux.
     * Aucun délai d'activation n'est appliqué.
     */
    public synchronized void demarrerActifs(int nombre) {

        if (nombre < parametres.workersMin()
                || nombre > parametres.workersMax()) {
            throw new IllegalArgumentException(
                    "Nombre de workers hors limites"
            );
        }

        if (!listeWorkers.isEmpty()) {
            throw new IllegalStateException(
                    "Les workers sont déjà démarrés"
            );
        }

        capaciteCible = nombre;

        for (int i = 0; i < nombre; i++) {

            Worker worker = new Worker(
                    prochainIdWorker++,
                    EtatWorker.ACTIF,
                    this
            );

            listeWorkers.add(worker);

            // Le worker commence à être payé
            compteurTempsPaye.ajuster(+1);

            journaliser("ACTIF", worker);

            // Lancer le worker dans son propre thread
            lancerWorker(worker);
        }
    }

    /**
     * Modifie le nombre de workers souhaité.
     * Ajoute ou retire des workers selon la cible.
     */
    public synchronized void fixerCible(int nombre) {

        if (nombre < parametres.workersMin()
                || nombre > parametres.workersMax()) {
            throw new IllegalArgumentException(
                    "Capacité cible hors limites"
            );
        }

        capaciteCible = nombre;
        journaliser("CIBLE", null);

        // Compter les workers actifs ou en démarrage
        int workersActuels = 0;

        for (Worker worker : listeWorkers) {
            if (worker.etat() == EtatWorker.ACTIF
                    || worker.etat() == EtatWorker.EN_DEMARRAGE) {
                workersActuels++;
            }
        }

        // Ajouter des workers si la capacité est insuffisante
        if (nombre > workersActuels) {

            int aAjouter = nombre - workersActuels;

            for (int i = 0; i < aAjouter; i++) {
                provisionner();
            }

        } else if (nombre < workersActuels) {

            int aRetirer = workersActuels - nombre;

            // Annuler d'abord les workers en démarrage
            for (int i = listeWorkers.size() - 1;
                    i >= 0 && aRetirer > 0; i--) {

                Worker worker = listeWorkers.get(i);

                if (worker.etat() == EtatWorker.EN_DEMARRAGE) {

                    worker.changerEtat(EtatWorker.ARRETE);
                    listeWorkers.remove(i);

                    compteurTempsPaye.ajuster(-1);
                    journaliser("ANNULE", worker);

                    aRetirer--;
                }
            }

            // Ensuite retirer les workers actifs
            for (int i = listeWorkers.size() - 1;
                    i >= 0 && aRetirer > 0; i--) {

                Worker worker = listeWorkers.get(i);

                if (worker.etat() == EtatWorker.ACTIF) {

                    // Le worker termine sa transaction en cours
                    worker.changerEtat(EtatWorker.EN_VIDANGE);

                    journaliser("EN_VIDANGE", worker);
                    aRetirer--;
                }
            }
        }
    }

    /**
     * Ajoute une tâche à la file d'attente.
     *
     * @return true si la tâche est acceptée,
     *         false si la file est pleine.
     */
    public boolean soumettre(TacheFile tache) {

        // Refuser la tâche si la file est pleine
        if (!fileAttente.offer(tache)) {
            return false;
        }

        // Planifier son expiration après le délai maximal
        planificateur.schedule(() -> {

            // Expirer seulement si la tâche attend encore
            if (fileAttente.remove(tache)) {

                double attenteMs = calculerAttenteMs(tache);

                tache.expirer(attenteMs);
            }

        }, parametres.attenteMax().toMillis(),
           TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Récupère la prochaine tâche disponible.
     * Vérifie également si son attente a expiré.
     */
    public TacheFile prendreTache(long attenteMaxMs)
            throws InterruptedException {

        // Attendre une tâche pendant la durée indiquée
        TacheFile tache = fileAttente.poll(
                attenteMaxMs,
                TimeUnit.MILLISECONDS
        );

        // Vérifier le délai d'attente de la tâche
        while (tache != null) {

            double attenteMs = calculerAttenteMs(tache);

            if (attenteMs <= parametres.attenteMax().toMillis()) {
                break;
            }

            // La transaction a trop attendu
            tache.expirer(attenteMs);

            // Prendre la suivante sans attendre
            tache = fileAttente.poll();
        }

        return tache;
    }

    /**
     * Arrête un worker après la fin de sa transaction.
     */
    public synchronized void arreter(Worker worker) {

        if (!listeWorkers.remove(worker)) {
            return;
        }

        worker.changerEtat(EtatWorker.ARRETE);

        // Le worker n'est plus payé
        compteurTempsPaye.ajuster(-1);

        journaliser("ARRETE", worker);
    }

    /**
     * Retourne l'état actuel du service :
     * capacité, workers occupés, file et temps payé.
     */
    public synchronized EtatCourant etatCourant() {

        int enDemarrage = 0;
        int actifs = 0;
        int enVidange = 0;
        int occupes = 0;

        for (Worker worker : listeWorkers) {

            switch (worker.etat()) {

                case EN_DEMARRAGE -> enDemarrage++;

                case ACTIF -> actifs++;

                case EN_VIDANGE -> enVidange++;

                default -> {
                }
            }

            if (worker.estOccupe()) {
                occupes++;
            }
        }

        return new EtatCourant(
                capaciteCible,
                enDemarrage,
                actifs,
                enVidange,
                occupes,
                fileAttente.size(),
                compteurTempsPaye.calculerTempsPaye()
        );
    }

    /**
     * Retourne les événements enregistrés
     * à partir d'une position du journal.
     */
    public synchronized List<Evenement> evenementsDepuis(
            int position) {

        if (position < 0 || position > historiqueEvenements.size()) {
            throw new IllegalArgumentException(
                    "Position du journal invalide"
            );
        }

        return List.copyOf(
                historiqueEvenements.subList(
                        position,
                        historiqueEvenements.size()
                )
        );
    }

    /**
     * Crée un worker avec un délai d'activation.
     * Le worker est payé dès sa création.
     */
    private synchronized void provisionner() {

        Worker worker = new Worker(
                prochainIdWorker++,
                EtatWorker.EN_DEMARRAGE,
                this
        );

        listeWorkers.add(worker);

        compteurTempsPaye.ajuster(+1);
        journaliser("PROVISIONNE", worker);

        // Activer le worker après le délai de démarrage
        planificateur.schedule(() -> {

            synchronized (GestionnaireWorkers.this) {

                // Ne pas activer un worker annulé
                if (worker.etat() == EtatWorker.EN_DEMARRAGE) {

                    worker.changerEtat(EtatWorker.ACTIF);

                    journaliser("ACTIF", worker);

                    lancerWorker(worker);
                }
            }

        }, parametres.delaiActivation().toMillis(),
           TimeUnit.MILLISECONDS);
    }

    /**
     * Lance la boucle d'un worker dans un nouveau thread.
     */
    private void lancerWorker(Worker worker) {

        Thread thread = new Thread(
                worker::boucle,
                "worker-" + worker.id()
        );

        thread.start();
    }

    /**
     * Calcule le temps passé par une tâche dans la file.
     */
    private double calculerAttenteMs(TacheFile tache) {

        return Math.max(
                0.0,
                Duration.between(
                        tache.instantEntree(),
                        Instant.now()
                ).toNanos() / 1_000_000.0
        );
    }

    /**
     * Enregistre un événement lié aux workers.
     */
    private synchronized void journaliser(
            String type,
            Worker worker) {

        int idWorker = (worker != null)
                ? worker.id()
                : 0;

        historiqueEvenements.add(
                new Evenement(
                        Instant.now(),
                        type,
                        idWorker,
                        capaciteCible
                )
        );
    }
}
