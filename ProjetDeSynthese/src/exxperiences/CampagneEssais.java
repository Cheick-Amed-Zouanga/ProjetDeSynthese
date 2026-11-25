package exxperiences;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import analyse.AnalyseurResultats;
import analyse.ResumeEssai;
import Generateur.CollecteurMesures;
import Generateur.ExecuteurTrace;
import Generateur.GenerateurTrace;
import Generateur.LigneTrace;
import Generateur.PhaseCharge;
import Generateur.ProfilCharge;

/**
 * Pilote les essais de la campagne expérimentale :
 * 2 stratégies × 3 profils × 10 graines = 60 essais.
 *
 * Deux façons de l'utiliser :
 * 1. Avec Docker : « --un-essai B rafales 3 » joue un seul essai.
 *    Le service et le contrôleur B tournent déjà dans d'autres conteneurs.
 *    Le script de campagne répète la commande 60 fois.
 * 2. Sans Docker (Eclipse) : sans argument spécial, la classe lance
 *    elle-même le service et le contrôleur B pour chacun des 60 essais.
 */
public class CampagneEssais {

    private final GenerateurTrace generateurTrace = new GenerateurTrace();
    private final AnalyseurResultats analyseurResultats = new AnalyseurResultats();
    private final HttpClient clientHttp = HttpClient.newHttpClient();
    private final String urlService;
    private final Path dossierResultats;

    public CampagneEssais(String urlService, Path dossierResultats) {
        this.urlService = urlService.replaceAll("/+$", "");
        this.dossierResultats = dossierResultats;
    }

    /** Point d'entrée de la campagne. */
    public static void main(String[] args) throws Exception {
        String url = lireVariable("URL_SERVICE", "http://localhost:8080");
        Path dossier = Path.of(lireVariable("DOSSIER_RESULTATS", "results"));

        // Mode Docker : un seul essai
        if (args.length > 0 && "--un-essai".equals(args[0])) {
            if (args.length < 4) {
                System.err.println("Utilisation : --un-essai <A|B> <stable|croissance|rafales> <graine>");
                System.exit(2);
            }
            new CampagneEssais(url, dossier)
                    .executerUnEssai(args[1], args[2], Long.parseLong(args[3]));
            // Le conteneur doit s'arrêter : Docker Compose arrête alors les autres
            System.exit(0);
        }

        // Mode local : la campagne complète, comme avant
        if (args.length > 0) {
            url = args[0];
        }
        if (args.length > 1) {
            dossier = Path.of(args[1]);
        }
        new CampagneEssais(url, dossier).executerCampagne();
    }

    /**
     * Joue un seul essai contre un service déjà démarré.
     * Utilisé par le conteneur « generateur » de Docker Compose.
     */
    public void executerUnEssai(String strategie, String nomProfil, long graine)
            throws Exception {
        ProfilCharge profil = trouverProfil(nomProfil);
        Path dossier = dossierEssai(strategie, profil, graine);

        List<LigneTrace> trace = generateurTrace.generer(profil, graine);
        generateurTrace.ecrireCsv(trace, dossier.resolve("trace.csv"));

        // Docker Compose attend déjà le healthcheck ; on vérifie quand même
        attendreService(Duration.ofSeconds(60));
        System.out.printf("Essai %s / %s / graine %d : %d transactions à envoyer%n",
                strategie, profil.nom(), graine, trace.size());

        jouerTrace(trace, profil, dossier);
        afficherResume(strategie, profil, graine, dossier);
    }

    /** Lance chaque paire A/B avec la même graine et le même profil. */
    public void executerCampagne() throws Exception {
        List<ProfilCharge> profils = creerProfils();
        for (long graine = 1; graine <= 10; graine++) {
            for (ProfilCharge profil : profils) {
                executerEssai("A", profil, graine);
                executerEssai("B", profil, graine);
            }
        }
        System.out.println("60 essais terminés. Résultats dans : "
                + dossierResultats.toAbsolutePath());
        // Analyses entre essais : moyennes, intervalles de confiance, tests A/B.
    }

    /** Démarre les processus, joue une trace, puis calcule un résumé. */
    private void executerEssai(String strategie, ProfilCharge profil, long graine)
            throws Exception {
        Path dossier = dossierEssai(strategie, profil, graine);

        // Préparer les mêmes transactions pour les stratégies A et B.
        List<LigneTrace> trace = generateurTrace.generer(profil, graine);
        generateurTrace.ecrireCsv(trace, dossier.resolve("trace.csv"));

        int workersDepart = "A".equals(strategie) ? 16 : 5;
        Process service = null;
        Process controleur = null;

        try {
            // ApplicationPaiement lit le nombre de workers dans son premier argument
            service = lancerProcessus(dossier.resolve("service.log"),
                    "Main.ApplicationPaiement", String.valueOf(workersDepart));
            attendreService(Duration.ofSeconds(30));

            if ("B".equals(strategie)) {
                controleur = lancerProcessus(dossier.resolve("adaptatif.log"),
                        "strategie.ControleurAdaptatif", urlService);
                attendreControleurPret(controleur, dossier.resolve("adaptatif.log"));
            }

            jouerTrace(trace, profil, dossier);

        } finally {
            arreterProcessus(controleur);
            arreterProcessus(service);
        }

        afficherResume(strategie, profil, graine, dossier);
    }

    /** Crée le dossier results/<stratégie>/<profil>_s<graine>. */
    private Path dossierEssai(String strategie, ProfilCharge profil, long graine)
            throws IOException {
        Path dossier = dossierResultats.resolve(strategie)
                .resolve(profil.nom() + "_s" + graine);
        Files.createDirectories(dossier);
        return dossier;
    }

    /** Envoie la trace à l'heure prévue et relève les mesures chaque seconde. */
    private void jouerTrace(List<LigneTrace> trace, ProfilCharge profil, Path dossier)
            throws Exception {
        CollecteurMesures collecteur = new CollecteurMesures(urlService);
        ExecuteurTrace executeur = new ExecuteurTrace(urlService, collecteur);
        executeur.executer(trace, profil, dossier);
    }

    /** Calcule le résumé de l'essai et l'affiche. */
    private void afficherResume(String strategie, ProfilCharge profil, long graine,
            Path dossier) throws IOException {
        ResumeEssai resume = analyseurResultats.calculerResume(dossier, profil);
        System.out.printf("Essai %s / %s / graine %d : "
                + "réussies=%d, coût/1000=%.4f, SLO=%b%n",
                strategie, profil.nom(), graine,
                resume.transactionsReussies(), resume.coutPour1000(),
                resume.sloRespecte());
    }

    /** Retrouve un profil par son nom : stable, croissance ou rafales. */
    private static ProfilCharge trouverProfil(String nom) {
        for (ProfilCharge profil : creerProfils()) {
            if (profil.nom().equalsIgnoreCase(nom)) {
                return profil;
            }
        }
        throw new IllegalArgumentException("Profil inconnu : " + nom
                + " (choix : stable, croissance, rafales)");
    }

    /** Lit une variable d'environnement ou retourne la valeur par défaut. */
    private static String lireVariable(String nom, String defaut) {
        String valeur = System.getenv(nom);
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }

    /** Crée exactement les trois profils et les phases du protocole. */
    private static List<ProfilCharge> creerProfils() {
        ProfilCharge stable = new ProfilCharge("stable", 240, 30, List.of(
                new PhaseCharge("base", 0, 240, 40, 40)));

        ProfilCharge croissance = new ProfilCharge("croissance", 300, 30, List.of(
                new PhaseCharge("depart", 0, 30, 20, 20),
                new PhaseCharge("montee", 30, 300, 20, 120)));

        ProfilCharge rafales = new ProfilCharge("rafales", 330, 30, List.of(
                new PhaseCharge("base", 0, 60, 40, 40),
                new PhaseCharge("rafale_x2", 60, 80, 80, 80),
                new PhaseCharge("recuperation_1", 80, 150, 40, 40),
                new PhaseCharge("rafale_x4", 150, 170, 160, 160),
                new PhaseCharge("recuperation_2", 170, 230, 40, 40),
                new PhaseCharge("faible_charge", 230, 330, 12, 12)));

        return List.of(stable, croissance, rafales);
    }

    /**
     * Lance une classe main Java dans un autre processus.
     * Le projet est un module : il faut --module-path et -m, pas -cp.
     * Avec -cp, Java ne trouve pas la classe (ClassNotFoundException).
     */
    private static Process lancerProcessus(Path journal,
            String classePrincipale, String argument) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
        String cheminModules = System.getProperty("jdk.module.path");
        if (cheminModules == null || cheminModules.isBlank()) {
            // Lancement sans module : on garde le classpath
            return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    classePrincipale, argument)
                    .redirectErrorStream(true)
                    .redirectOutput(journal.toFile())
                    .start();
        }
        return new ProcessBuilder(java, "--module-path", cheminModules,
                "-m", "ProjetDeSynthese/" + classePrincipale, argument)
                .redirectErrorStream(true)
                .redirectOutput(journal.toFile())
                .start();
    }

    /** Vérifie que GET /health répond avant d'envoyer la charge. */
    private void attendreService(Duration delaiMax)
            throws IOException, InterruptedException {
        long limite = System.nanoTime() + delaiMax.toNanos();
        while (System.nanoTime() < limite) {
            try {
                HttpRequest requete = HttpRequest.newBuilder(
                        URI.create(urlService + "/health"))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                HttpResponse<String> reponse = clientHttp.send(requete,
                        HttpResponse.BodyHandlers.ofString());
                if (reponse.statusCode() == 200
                        && reponse.body().contains("UP")) {
                    return;
                }
            } catch (IOException e) {
                // Le service est probablement encore en cours de démarrage.
            }
            Thread.sleep(200);
        }
        throw new IOException("Le service n'a pas répondu à GET /health");
    }

    /** Attend que le contrôleur B ait envoyé sa première capacité cible. */
    private static void attendreControleurPret(Process processus, Path journal)
            throws IOException, InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            if (Files.exists(journal) && Files.readString(journal).contains("CONTROLEUR_PRET")) {
                return;
            }
            if (!processus.isAlive()) {
                throw new IOException("Contrôleur B arrêté : voir " + journal);
            }
            Thread.sleep(100);
        }
        throw new IOException("Contrôleur B non prêt : voir " + journal);
    }

    /** Arrête un processus et évite de laisser un service en cours entre deux essais. */
    private static void arreterProcessus(Process processus) throws InterruptedException {
        if (processus == null || !processus.isAlive()) {
            return;
        }
        processus.destroy();
        if (!processus.waitFor(5, TimeUnit.SECONDS)) {
            processus.destroyForcibly();
            processus.waitFor();
        }
    }
}
