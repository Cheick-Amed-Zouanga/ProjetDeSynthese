package donnees;
import java.time.Instant;

/**
 * Représente un événement lié au changement de capacité du système.
 * Permet de garder une trace des changements d'état des workers.
 */
public record Evenement(
    Instant instant,  // Date et heure de l'événement
    String type,      // Type : CIBLE, PROVISIONNE, ACTIF, EN_VIDANGE, ARRETE, ANNULE
    int idWorker,     // Identifiant du worker 
    int cible         // Nombre de workers souhaités
) {
}
