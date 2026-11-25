
package Main;

import java.math.BigDecimal;
import java.time.Duration;

import Parametres.ParametresTraitement;
import Parametres.ParametresCapacite;

import Stockage.EnregistrementTransactionBd;
import Stockage.EnregistrementTransactionBdJdbc;

import Traitement.GenerateurPlanTraitement;
import Traitement.ProcesseurPaiement;

import FileEtCapacite.DeterminerCoutWorker;
import FileEtCapacite.GestionnaireWorkers;

import service.ServicePaiement;

import Controleurs.ControleurTransactions;
import Controleurs.ControleurEtatService;
import Controleurs.ControleurAdministration;
import Controleurs.ControleurMesures;
import Controleurs.ServeurHttp;

import Stockage.ConnexionBD;

/**
 * Point d'entrée du service de paiement simulé.
 *
 * Initialise les paramètres, le stockage,
 * le traitement, les workers et les contrôleurs,
 * puis démarre le serveur HTTP.
 *
 * Réglages lus au démarrage (Docker Compose les fournit) :
 * WORKERS_DEPART (16 pour la stratégie A, 5 pour B), PORT (8080).
 * Sans Docker, le nombre de workers peut aussi venir du premier argument.
 */
public class ApplicationPaiement {

    public static void main(String[] args) throws Exception {

        // 0. Lire les réglages : argument, sinon variable d'environnement, sinon défaut
        int workersDepart = args.length > 0
                ? Integer.parseInt(args[0])
                : lireEntier("WORKERS_DEPART", 5);
        int port = lireEntier("PORT", 8080);

        // 1. Initialiser les paramètres du traitement
        ParametresTraitement parametresTraitement =
                new ParametresTraitement(
                    2026L,                     // Graine aléatoire
                    80.0,                      // Durée moyenne (ms)
                    0.5,                       // Coefficient de variation
                    10.0,                      // Durée minimale (ms)
                    1000.0,                    // Durée maximale (ms)
                    new BigDecimal("500.00"),  // Seuil gros montant
                    40.0,                      // Délai supplémentaire (ms)
                    0.03,                      // Taux de refus pour risque
                    new BigDecimal("5000.00")  // Montant maximal
                );

        // 2. Initialiser les paramètres de capacité
        ParametresCapacite parametresCapacite =
                new ParametresCapacite(
                    workersDepart,          // Workers initiaux (16 pour A, 5 pour B)
                    1,                      // Nombre minimal
                    32,                     // Nombre maximal
                    Duration.ofSeconds(10), // Délai d'activation
                    200,                    // Capacité de la file
                    Duration.ofSeconds(2)   // Attente maximale
                );

        // 3. Initialiser le stockage des transactions
        // Un conteneur neuf a une base vide : on crée la table d'abord
        ConnexionBD.creerTableSiAbsente();
        EnregistrementTransactionBd enregistrementTransactions =
                new EnregistrementTransactionBdJdbc();

        // 4. Initialiser le générateur de plans
        GenerateurPlanTraitement generateurPlanTraitement =
                new GenerateurPlanTraitement(
                    parametresTraitement
                );

        // 5. Initialiser le processeur de paiement
        ProcesseurPaiement processeurPaiement =
                new ProcesseurPaiement(
                    generateurPlanTraitement,
                    2000
                );

        // 6. Initialiser le compteur du temps payé
        DeterminerCoutWorker compteurTempsPaye =
                new DeterminerCoutWorker();

        // 7. Initialiser le gestionnaire des workers
        GestionnaireWorkers gestionnaireWorkers =
                new GestionnaireWorkers(
                    parametresCapacite,
                    compteurTempsPaye
                );

        // 8. Initialiser le service de paiement
        ServicePaiement servicePaiement =
                new ServicePaiement(
                    enregistrementTransactions,
                    processeurPaiement,
                    gestionnaireWorkers
                );

        // 9. Initialiser les contrôleurs
        ControleurTransactions controleurTransactions =
                new ControleurTransactions(servicePaiement);

        ControleurEtatService controleurEtatService =
                new ControleurEtatService(gestionnaireWorkers);

        ControleurAdministration controleurAdministration =
                new ControleurAdministration(
                    gestionnaireWorkers,
                    parametresCapacite
                );

        ControleurMesures controleurMesures =
                new ControleurMesures(
                    gestionnaireWorkers,
                    servicePaiement
                );

        // 10. Démarrer les workers initiaux
        gestionnaireWorkers.demarrerActifs(
                parametresCapacite.workersDepart()
        );

        System.out.println("Workers initiaux démarrés : " + workersDepart);

        // 11. Démarrer le serveur HTTP
        ServeurHttp serveur = new ServeurHttp(
                port,
                controleurTransactions,
                controleurEtatService,
                controleurAdministration,
                controleurMesures
        );
        serveur.demarrer();

        // 12. Arrêt propre : « docker compose down » envoie le signal SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arrêt du service demandé.");
            serveur.arreter();
        }));

        System.out.println("Base : " + ConnexionBD.url());
        System.out.println("SERVICE_PRET sur le port " + port);
        System.out.flush();
    }

    // Lit un entier dans une variable d'environnement
    private static int lireEntier(String nom, int defaut) {
        String valeur = System.getenv(nom);
        return valeur == null || valeur.isBlank() ? defaut : Integer.parseInt(valeur.trim());
    }
}
