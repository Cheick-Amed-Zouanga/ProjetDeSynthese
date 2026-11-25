package Controleurs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serveur HTTP du service de paiement.
 *
 * Reçoit les requêtes, appelle le bon contrôleur et renvoie du JSON.
 * Chaque requête tourne dans un fil virtuel : le serveur HTTP ne limite
 * donc pas la capacité. Seuls les workers la limitent.
 *
 * Routes :
 * POST /transactions, GET /transactions/{id}, GET /health,
 * GET /mesures, GET /mesures/evenements?depuis=N,
 * GET /admin/capacite et PUT /admin/capacite.
 */
public class ServeurHttp {

    private final HttpServer serveur;
    private final Gson gson;
    private final ControleurTransactions controleurTransactions;
    private final ControleurEtatService controleurEtatService;
    private final ControleurAdministration controleurAdministration;
    private final ControleurMesures controleurMesures;

    // Constructeur : prépare le serveur sans le démarrer
    public ServeurHttp(
            int port,
            ControleurTransactions controleurTransactions,
            ControleurEtatService controleurEtatService,
            ControleurAdministration controleurAdministration,
            ControleurMesures controleurMesures) throws IOException {

        this.controleurTransactions = controleurTransactions;
        this.controleurEtatService = controleurEtatService;
        this.controleurAdministration = controleurAdministration;
        this.controleurMesures = controleurMesures;

        // Gson ne sait pas écrire un Instant tout seul : on lui donne un adaptateur
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new AdaptateurInstant())
                .create();

        // 0.0.0.0 : accepter les connexions venant des autres conteneurs
        this.serveur = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 1024);

        // Un fil virtuel par requête : POST /transactions peut attendre son résultat
        serveur.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        serveur.createContext("/transactions", this::routeTransactions);
        serveur.createContext("/health", this::routeSante);
        serveur.createContext("/mesures", this::routeMesures);
        serveur.createContext("/admin/capacite", this::routeCapacite);
    }

    /** Démarre l'écoute du port. */
    public void demarrer() {
        serveur.start();
    }

    /** Arrête le serveur (utilisé à l'arrêt du conteneur). */
    public void arreter() {
        serveur.stop(0);
    }

    // POST /transactions et GET /transactions/{id}
    private void routeTransactions(HttpExchange echange) throws IOException {
        traiter(echange, () -> {
            String chemin = echange.getRequestURI().getPath();
            String methode = echange.getRequestMethod();

            if ("POST".equals(methode) && "/transactions".equals(chemin)) {
                RequeteTransaction requete = lireJson(echange, RequeteTransaction.class);
                // On attend le résultat : le fil virtuel ne bloque aucun worker
                ControleurTransactions.ReponseHttp reponse =
                        controleurTransactions.creer(requete).join();
                envoyer(echange, reponse.statut(), reponse.corps());

            } else if ("GET".equals(methode) && chemin.startsWith("/transactions/")) {
                String id = chemin.substring("/transactions/".length());
                ControleurTransactions.ReponseHttp reponse = controleurTransactions.consulter(id);
                Object corps = reponse.corps() != null
                        ? reponse.corps()
                        : Map.of("erreur", "transaction introuvable");
                envoyer(echange, reponse.statut(), corps);

            } else {
                envoyer(echange, 405, Map.of("erreur", "méthode ou route non prise en charge"));
            }
        });
    }

    // GET /health
    private void routeSante(HttpExchange echange) throws IOException {
        traiter(echange, () -> {
            if (!"GET".equals(echange.getRequestMethod())) {
                envoyer(echange, 405, Map.of("erreur", "GET seulement"));
                return;
            }
            envoyer(echange, 200, controleurEtatService.consulterEtat());
        });
    }

    // GET /mesures et GET /mesures/evenements?depuis=N
    private void routeMesures(HttpExchange echange) throws IOException {
        traiter(echange, () -> {
            String chemin = echange.getRequestURI().getPath();
            if (!"GET".equals(echange.getRequestMethod())) {
                envoyer(echange, 405, Map.of("erreur", "GET seulement"));
            } else if ("/mesures".equals(chemin)) {
                envoyer(echange, 200, controleurMesures.consulterMesures());
            } else if ("/mesures/evenements".equals(chemin)) {
                int depuis = lireParametreEntier(echange.getRequestURI(), "depuis", 0);
                envoyer(echange, 200, controleurMesures.evenements(depuis));
            } else {
                envoyer(echange, 404, Map.of("erreur", "route inconnue"));
            }
        });
    }

    // GET et PUT /admin/capacite
    private void routeCapacite(HttpExchange echange) throws IOException {
        traiter(echange, () -> {
            String methode = echange.getRequestMethod();
            if ("GET".equals(methode)) {
                envoyer(echange, 200, controleurAdministration.lireCapacite());
            } else if ("PUT".equals(methode)) {
                RequeteCapacite requete = lireJson(echange, RequeteCapacite.class);
                ControleurAdministration.ReponseHttp reponse =
                        controleurAdministration.modifierCapacite(requete);
                envoyer(echange, reponse.statut(), reponse.corps());
            } else {
                envoyer(echange, 405, Map.of("erreur", "GET ou PUT seulement"));
            }
        });
    }

    /**
     * Exécute une route et transforme les erreurs en réponses JSON.
     * Une requête mal formée donne 400 ; une autre erreur donne 500.
     */
    private void traiter(HttpExchange echange, Action action) throws IOException {
        try {
            action.executer();
        } catch (JsonParseException | IllegalArgumentException e) {
            envoyer(echange, 400, Map.of("erreur", "requête invalide"));
        } catch (CompletionException e) {
            String message = e.getCause() == null ? e.toString() : e.getCause().toString();
            envoyer(echange, 500, Map.of("erreur", message));
        } catch (Exception e) {
            envoyer(echange, 500, Map.of("erreur", e.toString()));
        } finally {
            echange.close();
        }
    }

    /** Lit le corps JSON de la requête. Un corps vide donne null. */
    private <T> T lireJson(HttpExchange echange, Class<T> type) throws IOException {
        try (InputStream entree = echange.getRequestBody()) {
            String texte = new String(entree.readAllBytes(), StandardCharsets.UTF_8);
            return gson.fromJson(texte, type);
        }
    }

    /** Écrit la réponse JSON avec son code HTTP. */
    private void envoyer(HttpExchange echange, int statut, Object corps) throws IOException {
        byte[] octets = gson.toJson(corps).getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        echange.sendResponseHeaders(statut, octets.length);
        try (OutputStream sortie = echange.getResponseBody()) {
            sortie.write(octets);
        }
    }

    /** Lit un paramètre entier dans l'adresse, par exemple ?depuis=12. */
    private static int lireParametreEntier(URI adresse, String nom, int defaut) {
        String requete = adresse.getQuery();
        if (requete == null) {
            return defaut;
        }
        for (String paire : requete.split("&")) {
            String[] morceaux = paire.split("=", 2);
            if (morceaux.length == 2 && morceaux[0].equals(nom)) {
                return Integer.parseInt(morceaux[1]); // NumberFormatException donne 400
            }
        }
        return defaut;
    }

    /** Une route qui peut lancer une exception. */
    @FunctionalInterface
    private interface Action {
        void executer() throws Exception;
    }

    /** Écrit et lit un Instant au format texte ISO, par exemple 2026-11-24T20:15:03.120Z. */
    private static final class AdaptateurInstant extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter sortie, Instant valeur) throws IOException {
            if (valeur == null) {
                sortie.nullValue();
            } else {
                sortie.value(valeur.toString());
            }
        }

        @Override
        public Instant read(JsonReader entree) throws IOException {
            if (entree.peek() == JsonToken.NULL) {
                entree.nextNull();
                return null;
            }
            return Instant.parse(entree.nextString());
        }
    }
}
