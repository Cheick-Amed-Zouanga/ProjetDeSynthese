package Generateur;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lit GET /mesures environ chaque seconde pendant un essai.
 * Enregistre les mesures et les événements dans des fichiers CSV.
 */
public class CollecteurMesures {

    private final HttpClient client;   // Client HTTP utilisé pour interroger le service
    private final String urlService;   // Adresse du service simulé
    private volatile boolean actif;    // Indique si les mesures doivent continuer

    private Thread filCollecte;        // Thread chargé des lectures périodiques
    private Path dossierResultats;     // Dossier de sortie de l'essai
    private BufferedWriter fichierMesures;
    private long debutNs;              // Début de la collecte en nanosecondes

    public CollecteurMesures(String urlService) {
        this.urlService = urlService.replaceAll("/+$", "");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    /** Lance l'enregistrement d'une mesure environ toutes les secondes. */
    public synchronized void demarrer(Path dossier) throws IOException {
        if (actif) {
            throw new IllegalStateException("Collecte déjà démarrée");
        }
        Files.createDirectories(dossier);
        dossierResultats = dossier;
        fichierMesures = Files.newBufferedWriter(
                dossier.resolve("timeline.csv"), StandardCharsets.UTF_8);
        fichierMesures.write("tempsSec,type,instant,cible,enDemarrage,actifs,enVidange,"
                + "occupes,longueurFile,workersSecondes,transactionsRecues,"
                + "approuvees,refusees,echouees\n");

        debutNs = System.nanoTime();
        actif = true;
        filCollecte = new Thread(() -> {
            while (actif) {
                try {
                    capturer("PERIODIQUE");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Un échec de lecture est signalé, jamais remplacé par une fausse mesure.
                    System.err.println("Échec de collecte : " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruption) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "collecteur-mesures");
        filCollecte.start();
    }

    /** Capture la fin de la fenêtre mesurée, AVANT l'attente des réponses tardives. */
    public void capturerFinMesure() throws IOException, InterruptedException {
        capturer("FIN_MESURE");
    }

    /** Interroge /mesures et écrit une ligne horodatée. */
    private synchronized void capturer(String type)
            throws IOException, InterruptedException {
        HttpRequest requete = HttpRequest.newBuilder(
                URI.create(urlService + "/mesures"))
                .timeout(Duration.ofSeconds(3)).GET().build();
        HttpResponse<String> reponse = client.send(requete,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (reponse.statusCode() != 200) {
            throw new IOException("GET /mesures : HTTP " + reponse.statusCode());
        }
        JsonObject valeurs = JsonParser.parseString(reponse.body()).getAsJsonObject();
        double secondes = (System.nanoTime() - debutNs) / 1_000_000_000.0;
        String[] cles = {"instant", "cible", "enDemarrage", "actifs", "enVidange",
                "occupes", "longueurFile", "workersSecondes", "transactionsRecues",
                "approuvees", "refusees", "echouees"};
        StringBuilder ligne = new StringBuilder(String.format(Locale.ROOT,
                "%.6f,%s", secondes, type));
        for (String cle : cles) {
            ligne.append(',').append(csv(texte(valeurs, cle)));
        }
        fichierMesures.write(ligne + "\n");
        fichierMesures.flush();
    }

    /** Arrête la collecte et sauvegarde GET /mesures/evenements dans events.csv. */
    public void arreter() throws IOException, InterruptedException {
        actif = false;
        if (filCollecte != null) {
            filCollecte.interrupt();
            filCollecte.join();
        }
        synchronized (this) {
            if (fichierMesures != null) {
                fichierMesures.close();
                fichierMesures = null;
            }
        }

        HttpRequest requete = HttpRequest.newBuilder(
                URI.create(urlService + "/mesures/evenements?depuis=0"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> reponse = client.send(requete,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (reponse.statusCode() != 200) {
            throw new IOException("GET /mesures/evenements : HTTP "
                    + reponse.statusCode());
        }

        JsonArray evenements = JsonParser.parseString(reponse.body()).getAsJsonArray();
        try (BufferedWriter sortie = Files.newBufferedWriter(
                dossierResultats.resolve("events.csv"), StandardCharsets.UTF_8)) {
            sortie.write("instant,type,idWorker,cible\n");
            for (JsonElement element : evenements) {
                JsonObject evenement = element.getAsJsonObject();
                sortie.write(csv(texte(evenement, "instant")) + ","
                        + csv(texte(evenement, "type")) + ","
                        + csv(texte(evenement, "idWorker")) + ","
                        + csv(texte(evenement, "cible")) + "\n");
            }
        }
    }

    private static String texte(JsonObject objet, String cle) {
        JsonElement valeur = objet.get(cle);
        return valeur == null || valeur.isJsonNull() ? "" : valeur.getAsString();
    }

    private static String csv(String valeur) {
        return "\"" + valeur.replace("\"", "\"\"") + "\"";
    }
}

