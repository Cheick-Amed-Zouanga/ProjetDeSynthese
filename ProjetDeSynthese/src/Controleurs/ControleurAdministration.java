package Controleurs;
import java.util.Map;

import FileEtCapacite.GestionnaireWorkers;
import Parametres.ParametresCapacite;
import donnees.EtatCourant;

/**
 * Permet de consulter et de modifier la capacité
 * de traitement du service de paiement simulé.
 *
 * Utilisé par les routes :
 * GET /admin/capacite
 * PUT /admin/capacite
 */
public class ControleurAdministration {

    private final GestionnaireWorkers gestionnaireWorkers;
    private final ParametresCapacite parametres;

    // Constructeur
    public ControleurAdministration(
            GestionnaireWorkers gestionnaireWorkers,
            ParametresCapacite parametres) {

        this.gestionnaireWorkers = gestionnaireWorkers;
        this.parametres = parametres;
    }

    /**
     * Retourne les informations sur la capacité
     * actuelle du service.
     *
     * GET /admin/capacite
     */
    public Map<String, Object> lireCapacite() {

        // Récupérer l'état actuel des workers
        EtatCourant etat = gestionnaireWorkers.etatCourant();

        // Retourner les informations de capacité
        return Map.of(
            "cible", etat.cible(),
            "enDemarrage", etat.enDemarrage(),
            "actifs", etat.actifs(),
            "enVidange", etat.enVidange(),
            "workersMin", parametres.workersMin(),
            "workersMax", parametres.workersMax(),
            "delaiDemarrageMs",
                parametres.delaiActivation().toMillis()
        );
    }

    /**
     * Modifie le nombre de workers souhaité.
     *
     * PUT /admin/capacite
     */
    public ReponseHttp modifierCapacite(RequeteCapacite requete) {

        // Vérifier que le nombre de workers est fourni
        if (requete == null || requete.nombreWorkers() == null) {

            return new ReponseHttp(
                400,
                Map.of("erreur", "nombreWorkers obligatoire")
            );
        }

        try {

            // Demander au gestionnaire de modifier la capacité
            gestionnaireWorkers.fixerCible(
                requete.nombreWorkers()
            );

        } catch (IllegalArgumentException e) {

            // Refuser une capacité hors des limites autorisées
            return new ReponseHttp(
                400,
                Map.of("erreur", "capacité hors bornes")
            );
        }

        // Retourner la nouvelle capacité demandée
        return new ReponseHttp(
            200,
            lireCapacite()
        );
    }

    /**
     * Représente le code HTTP et les données
     * retournées par le contrôleur.
     */
    public record ReponseHttp(
        int statut,
        Map<String, Object> corps
    ) {
    }
}