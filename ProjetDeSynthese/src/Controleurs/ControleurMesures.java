package Controleurs;


import java.time.Instant;
import java.util.List;
import java.util.Map;

import FileEtCapacite.GestionnaireWorkers;
import donnees.Compteurs;
import donnees.EtatCourant;
import donnees.Evenement;
import service.ServicePaiement;

import java.util.LinkedHashMap;



/**
 * Permet de consulter les mesures du service de paiement.
 *
 * Fournit les informations sur les workers,
 * la file d'attente et les transactions.
 *
 * Routes associées :
 * GET /mesures
 * GET /mesures/evenements?depuis=N
 */
public class ControleurMesures {

    private final GestionnaireWorkers gestionnaireWorkers;
    private final ServicePaiement servicePaiement;

    // Constructeur
    public ControleurMesures(
            GestionnaireWorkers gestionnaireWorkers,
            ServicePaiement servicePaiement) {

        this.gestionnaireWorkers = gestionnaireWorkers;
        this.servicePaiement = servicePaiement;
    }

    /**
     * Récupère les mesures actuelles du service.
     *
     * Utilisée chaque seconde par le générateur de charge
     * et toutes les 5 secondes par la stratégie B.
     */
    public Map<String, Object> consulterMesures() {

        // Récupérer l'état actuel des workers
        EtatCourant etat = gestionnaireWorkers.etatCourant();

        // Récupérer les compteurs des transactions
        Compteurs compteurs = servicePaiement.compteurs();

        // Regrouper les mesures
        Map<String, Object> mesures = new LinkedHashMap<>();

        // Instant de la mesure
        mesures.put("instant", Instant.now());

        // Informations sur les workers
        mesures.put("cible", etat.cible());
        mesures.put("enDemarrage", etat.enDemarrage());
        mesures.put("actifs", etat.actifs());
        mesures.put("enVidange", etat.enVidange());
        mesures.put("occupes", etat.occupes());

        // Informations sur la file et le temps payé
        mesures.put("longueurFile", etat.longueurFile());
        mesures.put("workersSecondes", etat.workersSecondes());

        // Compteurs des transactions
        mesures.put("transactionsRecues", compteurs.recues());
        mesures.put("approuvees", compteurs.approuvees());
        mesures.put("refusees", compteurs.refusees());
        mesures.put("echouees", compteurs.echouees());

        return mesures;
    }

    /**
     * Retourne les événements enregistrés
     * à partir d'une position du journal.
     *
     * @param depuis Position de départ dans le journal
     * @return Liste des événements correspondants
     */
    public List<Evenement> evenements(int depuis) {

        return gestionnaireWorkers.evenementsDepuis(depuis);
    }
}
