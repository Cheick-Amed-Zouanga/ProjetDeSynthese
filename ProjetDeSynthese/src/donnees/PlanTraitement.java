
package donnees;

/**
 * Contient le résultat prévu du traitement d'une transaction.
 *
 * Le plan indique la durée simulée, la décision
 * et le motif associé à cette décision.
 */
public record PlanTraitement(

    EtatTransaction decision,    // APPROUVEE ou REFUSEE

    double dureePasserelleMs,    // Durée simulée en millisecondes

    String motif                // Raison de la décision

) {
}
