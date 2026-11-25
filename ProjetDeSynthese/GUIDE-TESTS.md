# Guide de test du service de paiement simulé

Ce guide explique comment vérifier que le projet fonctionne, comment lancer les essais et comment lire les résultats.

Sommaire :

1. [Applications nécessaires](#1-applications-nécessaires)
2. [Préparer le projet](#2-préparer-le-projet-une-seule-fois)
3. [Quel terminal utiliser](#3-quel-terminal-utiliser)
4. [Test rapide : les routes du service](#4-test-rapide--les-routes-du-service-5-minutes)
5. [Test de saturation](#5-test-de-saturation-file-pleine-et-attente-dépassée)
6. [Lancer un essai complet](#6-lancer-un-essai-complet)
7. [Lancer la campagne complète](#7-lancer-la-campagne-complète-60-essais)
8. [Où sont les résultats et quoi regarder](#8-où-sont-les-résultats-et-quoi-regarder)
9. [Commandes d'analyse PowerShell](#9-commandes-danalyse-powershell)
10. [Tester sans Docker](#10-tester-sans-docker-java-seul)
11. [Problèmes fréquents](#11-problèmes-fréquents)
12. [Nettoyer](#12-nettoyer)

---

## 1. Applications nécessaires

| Application | Obligatoire ? | Rôle |
|---|---|---|
| **Docker Desktop** (avec WSL 2) | Oui | Construit l'image et lance les 3 conteneurs. Java 25 est déjà dans l'image. |
| **PowerShell** (inclus dans Windows) | Oui | Lancer les scripts `scripts\*.ps1` et les commandes de ce guide. |
| **Un navigateur web** | Conseillé | Suivre `http://localhost:8080/health` et `/mesures` pendant un essai. |
| **curl** (inclus dans Windows 10/11) | Conseillé | Envoyer des requêtes de test (voir section 5). |
| **Excel, LibreOffice Calc** ou un éditeur de texte | Conseillé | Ouvrir les fichiers CSV des résultats. |
| **Eclipse** ou un JDK 25 | Seulement sans Docker | Voir section 10. |

Installer Docker Desktop sous Windows 10/11 :

1. Ouvrir PowerShell **en administrateur** et taper `wsl --install --no-distribution`, puis redémarrer.
2. Vérifier dans le Gestionnaire des tâches > Performance > Processeur que « Virtualisation » est **Activé** (sinon, l'activer dans le BIOS).
3. Télécharger Docker Desktop sur https://www.docker.com/products/docker-desktop/ et l'installer en gardant « Use WSL 2 » coché.
4. Lancer Docker Desktop et attendre « Engine running ».
5. Vérifier dans un terminal :

```powershell
docker version
docker compose version    # doit afficher 2.20 ou plus
```

---

## 2. Préparer le projet (une seule fois)

> Les commandes de ce guide sont des commandes **PowerShell**, sauf mention contraire. Elles ne marchent pas dans l'invite de commandes (cmd) : on y obtient « n'est pas reconnu en tant que commande interne ».

**Ouvrir PowerShell dans le dossier du projet** (au choix) :

- Dans l'Explorateur Windows, ouvrir le dossier `ProjetDeSynthese`, cliquer dans la barre d'adresse, taper `powershell` puis Entrée.
- Ou bien : menu Démarrer, taper `PowerShell`, ouvrir « Windows PowerShell », puis faire `cd` vers le dossier.
- Si une fenêtre cmd est déjà ouverte, taper `powershell` puis Entrée. L'invite commence alors par `PS C:\...`.

Toutes les commandes se lancent **depuis le dossier du projet**, celui qui contient `docker-compose.yml` :

```powershell
cd "C:\chemin\vers\ProjetDeSynthese"
```

1. **Bibliothèques** : le dossier `lib\` doit contenir `h2-2.5.252.jar` et `gson-2.13.2.jar`.

   ```powershell
   Get-ChildItem lib
   ```

2. **Mot de passe de la base** : le fichier `.env` doit exister et contenir une ligne `H2_PASSWORD=...`. S'il manque :

   ```powershell
   Copy-Item .env.exemple .env
   notepad .env      # remplacer choisir-un-mot-de-passe par un vrai mot de passe
   ```

3. **Autoriser les scripts PowerShell** (si Windows refuse de lancer les `.ps1`) :

   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
   ```

4. **Construire l'image** (environ 500 Mo à télécharger la première fois) :

   ```powershell
   docker compose build
   docker image ls projet-paiement     # l'image projet-paiement:1.0 doit apparaître
   ```

   Après **chaque modification du code Java**, il faut relancer `docker compose build`.

---

## 3. Quel terminal utiliser

| Terminal | Scripts du projet | Attention |
|---|---|---|
| **PowerShell** (recommandé) | `.\scripts\lancer-essai.ps1`, `.\scripts\lancer-campagne.ps1` | Dans Windows PowerShell 5.1, `curl` est un alias de `Invoke-WebRequest` : écrire **`curl.exe`**, ou utiliser `Invoke-RestMethod`. |
| **Invite de commandes (cmd)** | Pas de script `.bat` : passer par PowerShell | `curl` est le vrai curl. Les guillemets du JSON s'échappent avec `\"`. |
| **Git Bash, Linux, macOS** | `./scripts/lancer-essai.sh`, `./scripts/lancer-campagne.sh` | Rien de spécial. |

---

## 4. Test rapide : les routes du service (5 minutes)

On démarre **seulement le service**, sans générateur :

```powershell
docker compose up -d service-paiement
docker compose ps                  # attendre l'état "healthy"
```

Puis on teste chaque route.

### Avec PowerShell

```powershell
# Le service est-il vivant ?
Invoke-RestMethod http://localhost:8080/health

# Créer une transaction
$t = Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions `
     -ContentType "application/json" -Body '{"reference":"t1","montant":12.50}'
$t | Format-List

# La relire par son identifiant
Invoke-RestMethod "http://localhost:8080/transactions/$($t.id)"

# Les mesures du service
Invoke-RestMethod http://localhost:8080/mesures

# Lire, puis changer la capacité
Invoke-RestMethod http://localhost:8080/admin/capacite
Invoke-RestMethod -Method Put -Uri http://localhost:8080/admin/capacite `
     -ContentType "application/json" -Body '{"nombreWorkers":20}'

# Le journal des workers
Invoke-RestMethod "http://localhost:8080/mesures/evenements?depuis=0"
```

`Invoke-RestMethod` lance une erreur quand le code HTTP vaut 400 ou plus. Pour afficher le code d'une requête refusée :

```powershell
try {
    Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions `
        -ContentType "application/json" -Body '{"reference":"t2","montant":-5}'
} catch { "Code HTTP : " + [int]$_.Exception.Response.StatusCode }
```

### Avec cmd

```bat
curl http://localhost:8080/health
curl -X POST -H "Content-Type: application/json" -d "{\"reference\":\"t1\",\"montant\":12.50}" http://localhost:8080/transactions
curl -i -X POST -H "Content-Type: application/json" -d "{\"reference\":\"t2\",\"montant\":-5}" http://localhost:8080/transactions
curl http://localhost:8080/mesures
curl -X PUT -H "Content-Type: application/json" -d "{\"nombreWorkers\":20}" http://localhost:8080/admin/capacite
```

L'option `-i` affiche aussi le code HTTP.

### Résultats attendus

| Requête | Code HTTP | Réponse attendue |
|---|---|---|
| `GET /health` | 200 | `statut: UP`, `cible: 16`, `actifs: 16` |
| `POST` montant 12.50 | 201 | `etat: APPROUVEE`, `motif: AUTORISEE`, devise `CAD` par défaut |
| `POST` montant 6000 (plus de 5000) | 201 | `etat: REFUSEE`, `motif: LIMITE_MONTANT` |
| Environ 3 % des `POST` valides | 201 | `etat: REFUSEE`, `motif: REGLE_RISQUE` (refus métier simulé) |
| `POST` montant négatif, 0, ou 0.001 | 400 | `erreur: requête invalide` |
| `POST` sans `reference`, devise `"EURO"`, JSON cassé, corps vide | 400 | `erreur: requête invalide` |
| `GET /transactions/{id}` existant | 200 | la transaction enregistrée en base |
| `GET /transactions/inconnu` | 404 | `erreur: transaction introuvable` |
| `DELETE /transactions` | 405 | `erreur: méthode ou route non prise en charge` |
| `PUT /admin/capacite` avec 20 | 200 | `cible: 20`, `enDemarrage: 4` ; après 10 s, `actifs: 20` |
| `PUT /admin/capacite` avec 12 (après 20) | 200 | des workers passent `enVidange`, puis s'arrêtent |
| `PUT /admin/capacite` avec 0 ou 99 | 400 | `erreur: capacité hors bornes` (bornes : 1 à 32) |
| `PUT /admin/capacite` avec `{}` | 400 | `erreur: nombreWorkers obligatoire` |
| `GET /mesures/evenements?depuis=-1` ou `abc` | 400 | `erreur: requête invalide` |

Arrêter le service à la fin :

```powershell
docker compose down --volumes
```

---

## 5. Test de saturation (file pleine et attente dépassée)

But : vérifier que le service refuse proprement les transactions quand il n'a pas assez de workers. La file contient 200 places au maximum et une transaction n'attend pas plus de 2 s.

1. Démarrer le service (section 4) et le réduire à **1 worker** :

   ```powershell
   Invoke-RestMethod -Method Put -Uri http://localhost:8080/admin/capacite `
        -ContentType "application/json" -Body '{"nombreWorkers":1}'
   ```

2. Envoyer **300 transactions en même temps**. Depuis PowerShell, cette commande passe par cmd pour garder le vrai curl :

   ```powershell
   cmd /c 'curl -s -o NUL -w "%{http_code}\n" --parallel --parallel-max 300 -H "Content-Type: application/json" -d "{\"reference\":\"rafale\",\"montant\":10}" "http://localhost:8080/transactions?n=[1-300]"' | Group-Object | Select-Object Count,Name
   ```

3. Regarder les compteurs :

   ```powershell
   Invoke-RestMethod http://localhost:8080/mesures | Select-Object transactionsRecues,approuvees,refusees,echouees
   ```

**Résultat attendu** : quelques dizaines de réponses `201` et une majorité de `503`. Les 503 ont pour motif `FILE_PLEINE` (la file de 200 places est pleine) ou `ATTENTE_DEPASSEE` (la transaction a attendu plus de 2 s). Le service ne doit pas planter, et `/health` doit toujours répondre.

---

## 6. Lancer un essai complet

Un essai = une stratégie + un profil de charge + une graine.

| Paramètre | Valeurs possibles | Signification |
|---|---|---|
| Stratégie | `A` | 16 workers fixes du début à la fin |
| | `B` | départ à 5 workers ; le contrôleur adaptatif ajuste toutes les 5 s |
| Profil | `stable` | 40 transactions/s pendant 240 s |
| | `croissance` | 20 transactions/s, puis montée progressive jusqu'à 120/s (300 s) |
| | `rafales` | 40/s avec une rafale x2 (à 60 s) et une rafale x4 (à 150 s), puis faible charge (330 s) |
| Graine | 1 à 10 | La même graine donne exactement les mêmes transactions pour A et pour B |

### Commandes

```powershell
# PowerShell
.\scripts\lancer-essai.ps1 -Strategie A -Profil stable -Graine 1
.\scripts\lancer-essai.ps1 -Strategie B -Profil rafales -Graine 1
```

```bash
# Git Bash, Linux, macOS
./scripts/lancer-essai.sh A stable 1
./scripts/lancer-essai.sh B rafales 1
```

Un essai dure de **4 à 6 minutes**. À la fin, le script affiche une ligne de ce type :

```
Essai A / stable / graine 1 : réussies=8488, coût/1000=6,5977, SLO=true
```

### Suivre l'essai pendant qu'il tourne

- Dans le navigateur : `http://localhost:8080/health` et `http://localhost:8080/mesures` (actualiser avec F5).
- Dans un **deuxième** terminal :

  ```powershell
  docker compose ps                              # conteneurs en cours
  docker compose logs -f service-paiement        # journal du service
  docker compose logs -f controleur-b            # journal du contrôleur (stratégie B)
  ```

Ce qu'on doit voir :

- **Stratégie A** : `cible` et `actifs` restent à 16.
- **Stratégie B** : `cible` monte quand le débit augmente. Pendant 10 s, les nouveaux workers sont comptés dans `enDemarrage` avant de passer dans `actifs`. La cible redescend d'un worker toutes les 5 s quand la charge baisse (pas avant 30 s après la dernière hausse). Le journal du contrôleur doit contenir `CONTROLEUR_PRET`.

---

## 7. Lancer la campagne complète (60 essais)

2 stratégies x 3 profils x 10 graines = 60 essais, soit environ **5 heures**. Ne pas mettre l'ordinateur en veille pendant la campagne.

```powershell
.\scripts\lancer-campagne.ps1                              # graines 1 à 10
.\scripts\lancer-campagne.ps1 -GraineDebut 1 -GraineFin 2  # seulement les graines 1 et 2 (12 essais, 1 h environ)
```

```bash
./scripts/lancer-campagne.sh
./scripts/lancer-campagne.sh 1 2
```

À la fin, le script liste les essais en échec. On peut relancer un essai seul avec `lancer-essai`.

---

## 8. Où sont les résultats et quoi regarder

Chaque essai écrit dans `results\<stratégie>\<profil>_s<graine>\`, par exemple `results\B\rafales_s3\`.

| Fichier | Contenu | Quoi regarder |
|---|---|---|
| **`summary.csv`** | **Le résumé de l'essai (1 ligne)** | **Le fichier principal pour comparer A et B** (détail ci-dessous) |
| `requests.csv` | Une ligne par transaction : `instant, reference, latenceMs, codeHttp, etat, motif, phase` | Les motifs d'échec, et dans quelle phase les échecs ont lieu |
| `timeline.csv` | Les mesures du service chaque seconde : `cible, enDemarrage, actifs, enVidange, occupes, longueurFile, workersSecondes`, compteurs | La courbe des workers face à la charge (surtout pour B) |
| `events.csv` | Chaque changement d'un worker : `CIBLE, PROVISIONNE, ACTIF, EN_VIDANGE, ARRETE, ANNULE` | Vérifier que la montée et la descente se passent comme prévu |
| `trace.csv` | Les transactions prévues (identiques pour A et B avec la même graine) | Vérifier que A et B ont reçu la même charge |
| `service.log`, `adaptatif.log` | Journaux des conteneurs | Chercher les erreurs (`Exception`) |

### Les colonnes de `summary.csv`

Les 30 premières secondes (échauffement) ne sont **pas** comptées.

| Colonne | Signification | Bonne valeur |
|---|---|---|
| `transactionsEnvoyees` | Transactions envoyées hors échauffement | — |
| `transactionsReussies` | `APPROUVEE` + `REFUSEE` : un refus métier compte comme une transaction bien traitée | proche de `transactionsEnvoyees` |
| `transactionsEchouees` | Échecs techniques : `FILE_PLEINE`, `ATTENTE_DEPASSEE`, pas de réponse | 0 |
| `debitReussi` | Transactions réussies par seconde | proche du débit du profil |
| `tauxEchec` | % d'échecs techniques | **≤ 1 %** |
| `p50Ms`, `p95Ms`, `p99Ms` | Latences : 50 %, 95 % et 99 % des transactions répondent en moins de ce temps | **p95 ≤ 250 ms** |
| `coutTotal` | Coût fictif des workers sur la fenêtre mesurée (1 $ par worker-minute) | le plus bas possible |
| `coutPour1000` | Coût pour 1000 transactions réussies | **l'indicateur de coût à comparer** |
| `sloRespecte` | `true` si p95 ≤ 250 ms **et** tauxEchec ≤ 1 % | `true` |

### La question à laquelle les résultats répondent

> La stratégie B (adaptative) coûte-t-elle moins cher que A (fixe) tout en respectant l'objectif de service (SLO) ?

Il faut comparer, **pour chaque profil**, A et B avec la même graine :

- `coutPour1000` : B devrait être moins cher, car il paie moins de workers quand la charge est faible.
- `sloRespecte`, `tauxEchec`, `p95Ms` : B risque de rater le SLO quand la charge monte plus vite que les 10 s de démarrage d'un worker.

### Exemple obtenu pendant nos tests (graine 1, hors Docker)

| Essai | Échecs | p95 | Coût/1000 | SLO |
|---|---|---|---|---|
| A / stable | 0 % | 170 ms | 6,60 | oui |
| B / rafales | 8,3 % | 2007 ms | 3,94 | non |

Pour B / rafales, les 1061 échecs ont tous lieu pendant `rafale_x4` (à 150 s). Le contrôleur venait de redescendre à 5 workers ; les workers demandés ont mis 10 s à démarrer, pendant que la file débordait. Ces valeurs donnent un ordre de grandeur : avec Docker (2 processeurs alloués), les chiffres seront un peu différents.

---

## 9. Commandes d'analyse PowerShell

Toutes ces commandes se lancent depuis le dossier du projet.

**Afficher le résumé d'un essai :**

```powershell
Import-Csv results\A\stable_s1\summary.csv | Format-List
```

**Compter les résultats par état et motif (hors échauffement) :**

```powershell
Import-Csv results\B\rafales_s1\requests.csv | Where-Object phase -ne "echauffement" |
    Group-Object etat,motif | Sort-Object Count -Descending | Select-Object Count,Name
```

**Dans quelle phase ont lieu les échecs :**

```powershell
Import-Csv results\B\rafales_s1\requests.csv | Where-Object etat -eq "ECHOUEE" |
    Group-Object phase | Select-Object Count,Name
```

**Voir l'évolution des workers toutes les 10 secondes :**

```powershell
Import-Csv results\B\rafales_s1\timeline.csv |
    Where-Object { [int][double]$_.tempsSec % 10 -eq 0 } |
    Select-Object @{n='t';e={[int][double]$_.tempsSec}},cible,actifs,longueurFile,echouees |
    Format-Table -AutoSize
```

(La colonne `echouees` de `timeline.csv` est un total cumulé depuis le début.)

**Tableau de tous les essais, et moyennes par stratégie et profil :**

```powershell
$tous = Get-ChildItem results -Recurse -Filter summary.csv | ForEach-Object {
    $r = Import-Csv $_.FullName
    [pscustomobject]@{
        Strategie = $_.Directory.Parent.Name
        Profil    = ($_.Directory.Name -split "_s")[0]
        Graine    = [int]($_.Directory.Name -split "_s")[1]
        Echecs    = [double]$r.tauxEchec
        P95       = [double]$r.p95Ms
        Cout1000  = [double]$r.coutPour1000
        SLO       = $r.sloRespecte
    }
}

# Tous les essais
$tous | Sort-Object Profil, Graine, Strategie | Format-Table -AutoSize

# Moyennes par stratégie et profil
$tous | Group-Object Strategie, Profil | ForEach-Object {
    [pscustomobject]@{
        Groupe      = $_.Name
        Essais      = $_.Count
        EchecsMoy   = [math]::Round(($_.Group | Measure-Object Echecs -Average).Average, 2)
        P95Moy      = [math]::Round(($_.Group | Measure-Object P95 -Average).Average, 1)
        Cout1000Moy = [math]::Round(($_.Group | Measure-Object Cout1000 -Average).Average, 3)
        SLOtenus    = @($_.Group | Where-Object SLO -eq "true").Count
    }
} | Format-Table -AutoSize

# Enregistrer le tableau pour Excel
$tous | Export-Csv -NoTypeInformation -Encoding UTF8 resume-campagne.csv
```

Ouvrir dans Excel : Données > À partir d'un fichier texte/CSV, puis choisir le séparateur « virgule ». Les fichiers de `results` utilisent le **point** comme séparateur décimal.

---

## 10. Tester sans Docker (Java seul)

Il faut un **JDK 25** (`javac` et `java`). Celui d'Eclipse convient. Il se trouve dans un dossier du type `C:\Users\<nom>\.p2\pool\plugins\org.eclipse.justj.openjdk.hotspot.jre.full...\jre\bin`.

**Compiler** (PowerShell, depuis le dossier du projet) :

```powershell
$JAVA = "C:\chemin\vers\jdk-25\bin"     # à adapter
& "$JAVA\javac.exe" -encoding UTF-8 --module-path lib -d classes (Get-ChildItem src -Recurse -Filter *.java).FullName
```

**Terminal 1 : le service.** Mettre 16 workers pour A, 5 pour B :

```powershell
$env:H2_PASSWORD = "test"
$env:WORKERS_DEPART = "16"
Remove-Item -Recurse -Force data -ErrorAction SilentlyContinue   # repartir d'une base vide
& "$JAVA\java.exe" --module-path "lib;classes" -m ProjetDeSynthese/Main.ApplicationPaiement
```

Attendre le message `SERVICE_PRET sur le port 8080`.

**Terminal 2 : le contrôleur (stratégie B seulement)** :

```powershell
$env:URL_SERVICE = "http://localhost:8080"
& "$JAVA\java.exe" --module-path "lib;classes" -m ProjetDeSynthese/strategie.ControleurAdaptatif
```

Attendre le message `CONTROLEUR_PRET`.

**Terminal 3 : le générateur (un essai)** :

```powershell
$env:URL_SERVICE = "http://localhost:8080"
& "$JAVA\java.exe" --module-path "lib;classes" -m ProjetDeSynthese/exxperiences.CampagneEssais --un-essai A stable 1
```

Les résultats arrivent dans `results\` comme avec Docker (sans `service.log` ni `adaptatif.log`). Arrêter le service et le contrôleur avec **Ctrl+C**, puis supprimer `data\` avant l'essai suivant.

Dans Eclipse : lancer `exxperiences.CampagneEssais` **sans argument** fait les 60 essais d'affilée (environ 5 h). Il faut ajouter la variable `H2_PASSWORD` dans Run Configurations > Environment.

Les accents peuvent mal s'afficher dans la console Windows (`d�marr�s`). C'est seulement un problème d'affichage.

---

## 11. Problèmes fréquents

| Message ou symptôme | Cause | Solution |
|---|---|---|
| `'Get-ChildItem' n'est pas reconnu en tant que commande interne` | Commande PowerShell tapée dans cmd | Taper `powershell` puis Entrée, ou ouvrir PowerShell (section 2) |
| `request returned 500 Internal Server Error` / journal Docker : `no virtualization available` | Virtualisation (Intel VT-x / AMD-V) désactivée dans le BIOS | Redémarrer, entrer dans le BIOS (F1 sur Lenovo, souvent F2 ou Suppr ailleurs), activer « Intel Virtualization Technology », F10 pour enregistrer |
| `Créer le fichier .env avec H2_PASSWORD` | `.env` absent ou vide | Section 2, étape 2 |
| `error during connect` / `Cannot connect to the Docker daemon` | Docker Desktop n'est pas démarré | Lancer Docker Desktop et attendre « Engine running » |
| `module not found: com.h2database` pendant la construction | `lib\` ne contient pas les jars | Copier `h2-2.5.252.jar` et `gson-2.13.2.jar` dans `lib\` |
| `Module ProjetDeSynthese not found` (sans Docker) | Mauvais `--module-path` | Sous Windows, séparer avec `;` : `"lib;classes"` |
| `Bind for 0.0.0.0:8080 failed` / port déjà utilisé | Un autre programme utilise le port 8080 | `netstat -ano \| findstr :8080`, puis fermer le programme ou `docker compose down` |
| `l'exécution de scripts est désactivée sur ce système` | Politique PowerShell | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| `curl` répond avec un tableau PowerShell bizarre | `curl` = `Invoke-WebRequest` dans PowerShell 5.1 | Écrire `curl.exe` ou utiliser `Invoke-RestMethod` |
| `Repère FIN_MESURE absent de timeline.csv` | L'essai a été interrompu avant la fin | Relancer l'essai |
| Le code Java modifié n'a pas d'effet | Ancienne image | `docker compose build` |
| Un conteneur reste après un essai interrompu (Ctrl+C) | Compose n'a pas fait le ménage | `docker compose --profile strategie-b down --volumes` |

---

## 12. Nettoyer

```powershell
docker compose --profile strategie-b down --volumes   # arrêter tous les conteneurs et vider la base
docker image rm projet-paiement:1.0                   # supprimer l'image (il faudra la reconstruire)
Remove-Item -Recurse -Force results                   # supprimer TOUS les résultats (attention)
```
