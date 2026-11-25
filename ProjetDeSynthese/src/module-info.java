module ProjetDeSynthese {
    requires java.sql;          // JDBC : parler à la base de données
    requires com.h2database;    // Pilote de la base H2
    requires java.net.http;     // HttpClient : générateur et contrôleur B
    requires jdk.httpserver;    // HttpServer : serveur du service de paiement
    requires com.google.gson;   // Lecture et écriture du JSON

    // Gson lit les champs des records par réflexion : il faut lui ouvrir ces paquets
    opens donnees to com.google.gson;
    opens Controleurs to com.google.gson;
}
