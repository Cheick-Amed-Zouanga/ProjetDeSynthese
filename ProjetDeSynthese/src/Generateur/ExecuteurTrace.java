package Generateur;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Envoie les transactions de la trace à l'instant prévu, sans attendre
 * la réponse de chaque transaction avant d'envoyer la suivante.
 */
public class ExecuteurTrace {

    private final HttpClient client;
    private final String urlService;
    private final CollecteurMesures collecteur;

    private static final class Envoi {
        LigneTrace ligne;
        AtomicBoolean resultatEcrit = new AtomicBoolean(false);
        CompletableFuture<HttpResponse<String>> reponse;
        Envoi(LigneTrace ligne) { this.ligne = ligne; }
    }

    public ExecuteurTrace(String urlService, CollecteurMesures collecteur) {
        this.urlService = urlService.replaceAll("/+$", "");
        this.collecteur = collecteur;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    /**
     * Joue toute la trace, mesure les latences, puis laisse 5 s aux réponses tardives.
     * Le temps est mesuré depuis l'envoi PRÉVU, et non l'envoi effectif.
     */
    public void executer(List<LigneTrace> trace, ProfilCharge profil, Path dossier)
            throws Exception {
        Files.createDirectories(dossier);
        List<Envoi> envois = new ArrayList<>();
        AtomicReference<IOException> erreurEcriture = new AtomicReference<>();

        try (BufferedWriter fichierRequetes = Files.newBufferedWriter(
                dossier.resolve("requests.csv"), StandardCharsets.UTF_8)) {
            fichierRequetes.write("instant,reference,latenceMs,codeHttp,etat,motif,phase\n");
            collecteur.demarrer(dossier);
            long debutNs = System.nanoTime();

            try {
                for (LigneTrace ligne : trace) {
                    long instantPrevuNs = debutNs + (long) (ligne.instant() * 1_000_000_000L);
                    attendreJusqua(instantPrevuNs);

                    // Construire le JSON demandé par POST /transactions.
                    JsonObject corps = new JsonObject();
                    corps.addProperty("reference", ligne.reference());
                    corps.addProperty("montant", ligne.montant());
                    corps.addProperty("devise", ligne.devise());

                    HttpRequest requete = HttpRequest.newBuilder(
                            URI.create(urlService + "/transactions"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                            .build();
                    Envoi envoi = new Envoi(ligne);
                    envois.add(envoi);

                    // L'appel est ASYNCHRONE : la boucle passe au prochain envoi.
                    envoi.reponse = client.sendAsync(requete,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    envoi.reponse.whenComplete((reponse, erreur) -> {
                        if (!envoi.resultatEcrit.compareAndSet(false, true)) {
                            return; // Éviter de compter deux fois la même transaction.
                        }
                        double latenceMs = (System.nanoTime() - instantPrevuNs) / 1_000_000.0;
                        int codeHttp = reponse == null ? 0 : reponse.statusCode();
                        String etat = "";
                        String motif = erreur == null ? "" : "ERREUR_HTTP";
                        if (reponse != null) {
                            try {
                                JsonObject objet = JsonParser.parseString(
                                        reponse.body()).getAsJsonObject();
                                etat = valeur(objet, "etat");
                                motif = valeur(objet, "motif");
                            } catch (Exception e) {
                                motif = "REPONSE_INVALIDE";
                            }
                        }
                        try {
                            ecrireReponse(fichierRequetes, ligne, profil,
                                    latenceMs, codeHttp, etat, motif);
                        } catch (IOException e) {
                            erreurEcriture.compareAndSet(null, e);
                        }
                    });
                }

                // Attendre la fin nominale du profil, même si la trace se termine avant.
                attendreJusqua(debutNs + (long) (profil.duree() * 1_000_000_000L));
                collecteur.capturerFinMesure();

                // Au plus 5 secondes de grâce pour recevoir les dernières réponses.
                CompletableFuture<?>[] reponses = envois.stream()
                        .map(e -> e.reponse).toArray(CompletableFuture[]::new);
                try {
                    CompletableFuture.allOf(reponses)
                            .handle((ignore, erreur) -> null)
                            .get(5, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException
                        | java.util.concurrent.ExecutionException e) {
                    // Les requêtes sans réponse seront explicitement comptées comme échecs.
                }

                for (Envoi envoi : envois) {
                    if (envoi.resultatEcrit.compareAndSet(false, true)) {
                        long instantPrevuNs = debutNs
                                + (long) (envoi.ligne.instant() * 1_000_000_000L);
                        double latenceMs = (System.nanoTime() - instantPrevuNs)
                                / 1_000_000.0;
                        ecrireReponse(fichierRequetes, envoi.ligne, profil,
                                latenceMs, 0, "", "SANS_REPONSE");
                        envoi.reponse.cancel(true);
                    }
                }
            } finally {
                // Récupérer le journal avant d'arrêter le processus du service.
                collecteur.arreter();
                synchronized (fichierRequetes) {
                    fichierRequetes.flush();
                }
            }
        }
        if (erreurEcriture.get() != null) {
            throw new UncheckedIOException("Écriture de requests.csv impossible",
                    erreurEcriture.get());
        }
    }

    private static void attendreJusqua(long instantNs) throws InterruptedException {
        while (true) {
            long restant = instantNs - System.nanoTime();
            if (restant <= 0) {
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Essai interrompu");
            }
            LockSupport.parkNanos(Math.min(restant, 5_000_000L));
        }
    }

    /** Sauvegarde une réponse, de façon sécurisée entre les threads HTTP. */
    private static void ecrireReponse(BufferedWriter sortie, LigneTrace ligne,
            ProfilCharge profil, double latenceMs, int codeHttp,
            String etat, String motif) throws IOException {
        synchronized (sortie) {
            sortie.write(String.format(Locale.ROOT, "%.9f,%s,%.3f,%d,%s,%s,%s%n",
                    ligne.instant(), ligne.reference(), latenceMs, codeHttp,
                    etat, motif, profil.nomPhase(ligne.instant())));
        }
    }

    private static String valeur(JsonObject objet, String nom) {
        return objet.has(nom) && !objet.get(nom).isJsonNull()
                ? objet.get(nom).getAsString() : "";
    }
}

