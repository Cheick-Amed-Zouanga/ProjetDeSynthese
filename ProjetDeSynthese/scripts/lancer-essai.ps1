# Lance un seul essai avec Docker Compose (Windows PowerShell).
# Exemple, depuis le dossier du projet :
#   .\scripts\lancer-essai.ps1 -Strategie B -Profil rafales -Graine 3

param(
    [Parameter(Mandatory = $true)][ValidateSet("A", "B")][string]$Strategie,
    [Parameter(Mandatory = $true)][ValidateSet("stable", "croissance", "rafales")][string]$Profil,
    [Parameter(Mandatory = $true)][ValidateRange(1, 10)][int]$Graine
)

# Toujours en majuscule : A ou B (le nom du dossier en dépend)
$Strategie = $Strategie.ToUpper()

# Lire correctement les accents envoyés par Docker
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Se placer dans le dossier du projet (celui qui contient docker-compose.yml)
Set-Location (Split-Path $PSScriptRoot -Parent)

# Variables lues par docker-compose.yml
$env:STRATEGIE = $Strategie
$env:PROFIL = $Profil
$env:GRAINE = "$Graine"
if ($Strategie -eq "A") { $env:WORKERS_DEPART = "16" } else { $env:WORKERS_DEPART = "5" }

# Pour la stratégie B, on active le conteneur du contrôleur
$options = @()
if ($Strategie -eq "B") { $options = @("--profile", "strategie-b") }

$dossier = "results/$Strategie/${Profil}_s$Graine"
New-Item -ItemType Directory -Force -Path $dossier | Out-Null

Write-Host "=== Essai $Strategie / $Profil / graine $Graine ==="

# Démarrer les conteneurs. Quand le générateur s'arrête, Compose arrête les autres.
# Si l'image n'existe pas encore, Compose la construit d'abord.
docker compose @options up --attach generateur --abort-on-container-exit --exit-code-from generateur
$code = $LASTEXITCODE

# Garder les journaux avec les résultats de l'essai
docker compose @options logs --no-color --no-log-prefix service-paiement |
    Out-File -Encoding utf8 "$dossier/service.log"
if ($Strategie -eq "B") {
    docker compose @options logs --no-color --no-log-prefix controleur-b |
        Out-File -Encoding utf8 "$dossier/adaptatif.log"
}

# Supprimer les conteneurs et le volume de la base : le prochain essai repart de zéro
docker compose @options down --volumes

if ($code -ne 0) { Write-Host "Essai en échec (code $code). Voir $dossier" -ForegroundColor Red }
exit $code
