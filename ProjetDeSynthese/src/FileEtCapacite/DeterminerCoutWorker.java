package FileEtCapacite;
/**
 * Calcule le temps total d'utilisation des workers payés.
 * Ce temps permet ensuite de déterminer le coût
 * de l'infrastructure pour les stratégies A et B.
 */
public class DeterminerCoutWorker {

    private int workersPayes;          // Nombre de workers actuellement payés
    private long tempsCumuleNs;        // Temps cumulé de tous les workers en nanosecondes
    private long dernierCalculNs;      // Instant du dernier calcul en nanosecondes

    // Constructeur
    public DeterminerCoutWorker() {
        this.workersPayes = 0;
        this.tempsCumuleNs = 0L;
        this.dernierCalculNs = System.nanoTime();
    }

    /**
     * Met à jour le nombre de workers payés.
     *
     * @param variation +1 pour ajouter un worker,
     *                  -1 pour retirer un worker
     */
    public synchronized void ajuster(int variation) {

        // Récupérer l'instant actuel
        long maintenantNs = System.nanoTime();

        // Ajouter le temps cumulé depuis le dernier calcul
        tempsCumuleNs += (long) workersPayes
                * (maintenantNs - dernierCalculNs);

        // Enregistrer l'instant de la mise à jour
        dernierCalculNs = maintenantNs;

        // Modifier le nombre de workers payés
        workersPayes += variation;
    }

    /**
     * Retourne le temps total payé en workers-secondes.
     *
     * @return Temps cumulé en workers-secondes
     */
    public synchronized double calculerTempsPaye() {

        long maintenantNs = System.nanoTime();

        // Ajouter le temps écoulé depuis le dernier calcul
        long totalNs = tempsCumuleNs
                + (long) workersPayes * (maintenantNs - dernierCalculNs);

        // Convertir les nanosecondes en secondes
        return totalNs / 1_000_000_000.0;
    }
}
