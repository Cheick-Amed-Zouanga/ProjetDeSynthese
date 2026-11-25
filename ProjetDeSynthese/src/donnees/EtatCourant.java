package donnees;
/**
 * Représente l'état du système à un instant donné.
 * Permet de connaître la capacité et l'utilisation des workers.
 */
public record EtatCourant(
    int cible,              // Nombre de workers souhaités
    int enDemarrage,        // Workers en cours de démarrage
    int actifs,             // Workers actuellement actifs
    int enVidange,          // Workers en cours d'arrêt
    int occupes,            // Workers qui traitent une transaction
    int longueurFile,       // Nombre de transactions en attente
    double workersSecondes  // Temps cumulé des workers en secondes
) {
}
