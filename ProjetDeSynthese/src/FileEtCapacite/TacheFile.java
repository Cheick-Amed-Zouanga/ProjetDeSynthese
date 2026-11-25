package FileEtCapacite;

import java.time.Instant;

/**
 * Représente le travail à effectuer sur une transaction placée dans la file d'attente.
 *
 * Permet au gestionnaire de workers de traiter
 * ou d'expirer une tâche sans connaître le service
 * qui l'a créée.
 */
public interface TacheFile {
    /**
     * Retourne la date et l'heure d'entrée
     * de la transaction  dans la file d'attente.
     *
     * @return Instant d'entrée dans la file
     */
    Instant instantEntree();

    /**
     * Exécute la tâche lorsqu'un worker la récupère.
     *
     * @param attenteFileMs Temps passé dans la file en ms
     */
    void executer(double attenteFileMs);

    /**
     * Signale que la transaction a dépassé son délai
     * maximal d'attente dans la file.
     *
     * @param attenteFileMs Temps passé dans la file en ms
     */
    void expirer(double attenteFileMs);

}
