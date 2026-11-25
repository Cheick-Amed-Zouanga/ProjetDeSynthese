package Controleurs;
/**
 * Représente une demande de modification
 * du nombre de workers du service.
 *
 * Utilisée par PUT /admin/capacite.
 */
public record RequeteCapacite(

    Integer nombreWorkers  // Nombre de workers souhaité (exemple : 12)

) {
}