package Controleurs;
import java.util.Map;

import FileEtCapacite.GestionnaireWorkers;
import donnees.EtatCourant;



/**
 * Permet de consulter l'état actuel du service
 * et le nombre de workers disponibles.
 *
 * Utilisé pour la route GET /health.
 */
public class ControleurEtatService {

    private final GestionnaireWorkers gestionnaireWorkers;

    // Constructeur
    public ControleurEtatService(GestionnaireWorkers gestionnaireWorkers) {
        this.gestionnaireWorkers = gestionnaireWorkers;
    }

    /**
     * Retourne l'état du service et sa capacité actuelle.
     */
    public Map<String, Object> consulterEtat() {

        EtatCourant etat = gestionnaireWorkers.etatCourant();

        return Map.of(
            "statut", "UP",
            "cible", etat.cible(),
            "actifs", etat.actifs()
        );
    }
}
