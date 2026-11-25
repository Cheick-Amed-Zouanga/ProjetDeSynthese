# Projet de synthèse : service de paiement simulé

Ce projet compare deux façons de dimensionner un service de paiement simulé :

- **Stratégie A** : capacité fixe, 16 workers du début à la fin.
- **Stratégie B** : capacité adaptative, un contrôleur ajuste le nombre de workers toutes les 5 secondes.

Chaque essai envoie la même trace de transactions aux deux stratégies, puis mesure la latence, les échecs et le coût.

Pour tester le projet pas à pas et lire les résultats, voir [GUIDE-TESTS.md](GUIDE-TESTS.md).

## Ce qu'il faut installer

1. **Docker Desktop** (Windows ou macOS) ou Docker Engine (Linux).
2. Rien d'autre : Java 25 est dans l'image Docker.

## Préparer le projet (une seule fois)

1. Mettre les deux bibliothèques dans le dossier `lib/` du projet. Versions utilisées par le projet :
   - H2 2.5.252 : `h2-2.5.252.jar` ;
   - Gson 2.13.2 : `gson-2.13.2.jar`.

   Java 25 vient de l'image Docker `eclipse-temurin:25`.
2. Copier `.env.exemple` sous le nom `.env`, puis choisir un mot de passe pour H2.
3. Construire l'image :

```
docker compose build
```

## Lancer un essai

Windows PowerShell, depuis le dossier du projet :

```
.\scripts\lancer-essai.ps1 -Strategie B -Profil rafales -Graine 3
```

Linux, macOS ou Git Bash :

```
./scripts/lancer-essai.sh B rafales 3
```

Un essai dure de 4 à 6 minutes selon le profil. Pendant l'essai, `http://localhost:8080/health` et `http://localhost:8080/mesures` répondent dans le navigateur.

## Lancer la campagne complète

60 essais : 2 stratégies, 3 profils, 10 graines. Durée : environ 5 heures.

```
.\scripts\lancer-campagne.ps1
./scripts/lancer-campagne.sh
```

Pour une partie seulement, par exemple les graines 1 et 2 :

```
.\scripts\lancer-campagne.ps1 -GraineDebut 1 -GraineFin 2
./scripts/lancer-campagne.sh 1 2
```

## Où trouver les résultats

Chaque essai écrit dans `results/<stratégie>/<profil>_s<graine>/` :

- `trace.csv` : les transactions prévues ;
- `requests.csv` : le résultat et la latence de chaque transaction ;
- `timeline.csv` : les mesures du service chaque seconde ;
- `events.csv` : les changements d'état des workers ;
- `summary.csv` : le résumé de l'essai ;
- `service.log` et `adaptatif.log` : les journaux des conteneurs.

## Les trois conteneurs

- **service-paiement** : le service mesuré, port 8080.
- **controleur-b** : la stratégie B, seulement avec `--profile strategie-b`.
- **generateur** : joue la trace, mesure, écrit les résultats, puis s'arrête.

Les trois viennent de la même image `projet-paiement:1.0`. Seule la commande change.

## Commandes utiles

```
docker compose ps                       # conteneurs en cours
docker compose logs -f service-paiement # suivre le journal du service
docker compose down --volumes           # tout arrêter et vider la base
docker image ls                         # images présentes
```

Après une modification du code Java, il faut reconstruire l'image avec `docker compose build`.

## Sans Docker (Eclipse)

La classe `exxperiences.CampagneEssais` sans argument lance elle-même le service et le contrôleur B pour les 60 essais. Il faut alors définir la variable `H2_PASSWORD` dans la configuration d'exécution d'Eclipse.
