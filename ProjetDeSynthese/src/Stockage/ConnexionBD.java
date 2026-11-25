package Stockage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gère la connexion à la base de données H2.
 *
 * Les réglages viennent des variables d'environnement.
 * Docker Compose les fournit au conteneur ; sans Docker,
 * les valeurs par défaut servent pour Eclipse.
 */
public class ConnexionBD {

    // DB_CLOSE_DELAY=-1 : la base reste ouverte entre deux connexions
    private static final String URL = lireVariable(
            "BD_URL", "jdbc:h2:./data/paiement;DB_CLOSE_DELAY=-1");
    private static final String USER = lireVariable("BD_UTILISATEUR", "admin");
    private static final String PASSWORD = lireVariable("H2_PASSWORD", "");

    // Même table que schemas.sql
    private static final String CREATION_TABLE =
            "CREATE TABLE IF NOT EXISTS transaction_paiement ("
            + " id VARCHAR(36) PRIMARY KEY,"
            + " reference VARCHAR(100),"
            + " montant DECIMAL(12,2) NOT NULL,"
            + " devise CHAR(3) NOT NULL,"
            + " etat VARCHAR(16) NOT NULL,"
            + " motif VARCHAR(32),"
            + " cree_le TIMESTAMP(6) NOT NULL,"
            + " termine_le TIMESTAMP(6),"
            + " temps_attente_file_ms DOUBLE PRECISION,"
            + " temps_traitement_ms DOUBLE PRECISION)";

    // Ouvre une connexion à la base de données
    public static Connection connecter() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Crée la table si elle n'existe pas encore.
     * Un conteneur neuf démarre avec une base vide : sans cette étape,
     * la première insertion échouerait.
     */
    public static void creerTableSiAbsente() throws SQLException {
        try (Connection connexion = connecter();
             Statement instruction = connexion.createStatement()) {
            instruction.execute(CREATION_TABLE);
        }
    }

    /** Retourne l'adresse de la base, utile dans le journal de démarrage. */
    public static String url() {
        return URL;
    }

    // Lit une variable d'environnement ou retourne la valeur par défaut
    private static String lireVariable(String nom, String defaut) {
        String valeur = System.getenv(nom);
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }
}
