package donnees;
/**
 * Représente le nombre de transactions selon leur résultat.
 * Permet de consulter les compteurs du service de paiement.
 */
public record Compteurs(
    long recues,      // Nombre total de transactions reçues
    long approuvees,  // Nombre de transactions approuvées
    long refusees,    // Nombre de transactions refusées
    long echouees     // Nombre de transactions échouées
) {
}
