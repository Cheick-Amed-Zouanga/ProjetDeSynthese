package strategie;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Stratégie B : ajuste la capacité d'après le débit de transactions observé.
 * S'exécute dans un processus distinct et utilise seulement les routes HTTP.
 *
 * Avec Docker, ce processus tourne dans son propre conteneur.
 * Il trouve le service par son nom : http://service-paiement:8080.
 * Quand il est prêt, il crée le fichier /tmp/controleur-pret :
 * le healthcheck de Docker Compose vérifie ce fichier.
 */
public class ControleurAdaptatif {

    private static final Duration INTERVALLE_MESURE = Duration.ofSeconds(5);
    private static final int WORKERS_MIN = 2;
    private static final int WORKERS_MAX = 32;
    private static final double DUREE_MOYENNE_S = 0.081; // Hypothèse : 80 ms + 1 ms
    private static final double UTILISATION_VISEE = 0.70; // 30 % de marge
    private static final Duration DELAI_AVANT_REDUCTION = Duration.ofSeconds(30);

    private final HttpClient client;
    private final String urlService;
    private int workersCible;
    private long compteurRecuPrecedent;
    private Instant derniereAugmentation;

    public ControleurAdaptatif(String urlService) {
        this.urlService = urlService.replaceAll("/+$", "");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    /** Lance le contrôleur depuis la ligne de commande. */
    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : System.getenv("URL_SERVICE");
        if (url == null || url.isBlank()) {
            url = "http://localhost:8080";
        }
        new ControleurAdaptatif(url).executer();
    }

    /** Contrôle la capacité toutes les cinq secondes jusqu'à l'arrêt du processus. */
    public void executer() throws IOException, InterruptedException {
        attendreService(Duration.ofSeconds(60));
        workersCible = 5;
        envoyerCible(workersCible);
        compteurRecuPrecedent = lireNombreRecues();
        derniereAugmentation = Instant.now();
        signalerPret();

        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(INTERVALLE_MESURE.toMillis());
            try {
                ajuster();
            } catch (IOException e) {
                // Une erreur réseau passagère ne doit pas arrêter le contrôleur :
                // avec Docker Compose, un conteneur arrêté arrêterait tout l'essai.
                System.out.println("Mesure impossible, nouvel essai dans 5 s : " + e.getMessage());
                System.out.flush();
            }
        }
    }

    /** Une décision : lire le débit, calculer le besoin, changer la cible si besoin. */
    private void ajuster() throws IOException, InterruptedException {
        long compteurActuel = lireNombreRecues();
        double debitObserve = Math.max(0,
                compteurActuel - compteurRecuPrecedent)
                / (double) INTERVALLE_MESURE.toSeconds();
        compteurRecuPrecedent = compteurActuel;

        int workersNecessaires = calculerBesoin(debitObserve);
        if (workersNecessaires > workersCible) {
            // Augmenter directement le nombre demandé de workers.
            workersCible = workersNecessaires;
            derniereAugmentation = Instant.now();
            envoyerCible(workersCible);
        } else if (workersNecessaires < workersCible
                && Duration.between(derniereAugmentation, Instant.now())
                .compareTo(DELAI_AVANT_REDUCTION) >= 0) {
            // Diminuer progressivement : un worker par intervalle.
            workersCible--;
            envoyerCible(workersCible);
        }
    }

    /** Attend que le service réponde, au cas où il démarre encore. */
    private void attendreService(Duration delaiMax) throws IOException, InterruptedException {
        long limite = System.nanoTime() + delaiMax.toNanos();
        while (true) {
            try {
                lireNombreRecues();
                return;
            } catch (IOException e) {
                if (System.nanoTime() > limite) {
                    throw new IOException("Service introuvable à " + urlService, e);
                }
                Thread.sleep(500);
            }
        }
    }

    /** Écrit CONTROLEUR_PRET dans le journal et crée le fichier lu par le healthcheck. */
    private void signalerPret() throws IOException {
        Path fichier = Path.of(System.getProperty("java.io.tmpdir"), "controleur-pret");
        Files.writeString(fichier, Instant.now().toString());
        System.out.println("CONTROLEUR_PRET");
        System.out.flush();
    }

    /** Calcule les workers requis selon le débit et l'utilisation cible. */
    private int calculerBesoin(double debitTransactionsSec) {
        int nombre = (int) Math.ceil(debitTransactionsSec
                * DUREE_MOYENNE_S / UTILISATION_VISEE);
        return Math.max(WORKERS_MIN, Math.min(WORKERS_MAX, nombre));
    }

    /** Lit le nombre cumulé de transactions reçues via GET /mesures. */
    private long lireNombreRecues() throws IOException, InterruptedException {
        HttpRequest requete = HttpRequest.newBuilder(
                URI.create(urlService + "/mesures"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> reponse = client.send(requete,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (reponse.statusCode() != 200) {
            throw new IOException("GET /mesures : HTTP " + reponse.statusCode());
        }
        JsonObject mesures = JsonParser.parseString(reponse.body()).getAsJsonObject();
        return mesures.get("transactionsRecues").getAsLong();
    }

    /**
     * Demande une nouvelle capacité via PUT /admin/capacite.
     * Le JSON utilise « nombreWorkers », comme RequeteCapacite dans ton projet.
     */
    private void envoyerCible(int nombreWorkers) throws IOException, InterruptedException {
        JsonObject corps = new JsonObject();
        corps.addProperty("nombreWorkers", nombreWorkers);
        HttpRequest requete = HttpRequest.newBuilder(
                URI.create(urlService + "/admin/capacite"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(corps.toString()))
                .build();
        HttpResponse<String> reponse = client.send(requete,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (reponse.statusCode() != 200) {
            throw new IOException("PUT /admin/capacite : HTTP "
                    + reponse.statusCode() + " : " + reponse.body());
        }
    }
}

