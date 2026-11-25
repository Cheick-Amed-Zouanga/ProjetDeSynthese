package Parametres;

import java.time.Duration;

/**
 * Regroupe les paramètres de capacité du service simulé.
 *
 * Définit le nombre de workers disponibles,
 * la capacité de la file et les délais associés.
 */
public record ParametresCapacite(

    int workersDepart,          // Workers au départ,
    int workersMin,             // Nombre minimal de workers (1)
    int workersMax,             // Nombre maximal de workers (32)
    Duration delaiActivation,   // Temps nécessaire pour activer un worker (10 s)
    int tailleFile,             // Nombre maximal de transactions en attente (200)
    Duration attenteMax         // Temps maximal d'attente dans la file (2 s)

) {
}
