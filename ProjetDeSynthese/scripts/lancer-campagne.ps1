# Lance la campagne complète : 2 stratégies x 3 profils x 10 graines = 60 essais.
# Durée : environ 5 heures. Exemple, depuis le dossier du projet :
#   .\scripts\lancer-campagne.ps1
# Pour une partie seulement :
#   .\scripts\lancer-campagne.ps1 -GraineDebut 1 -GraineFin 2

param(
    [int]$GraineDebut = 1,
    [int]$GraineFin = 10
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location (Split-Path $PSScriptRoot -Parent)

# Construire l'image une seule fois au début
docker compose build
if ($LASTEXITCODE -ne 0) { Write-Host "La construction de l'image a échoué." -ForegroundColor Red; exit 1 }

$profils = @("stable", "croissance", "rafales")
$echecs = @()

for ($graine = $GraineDebut; $graine -le $GraineFin; $graine++) {
    foreach ($profil in $profils) {
        # A puis B avec la même graine et le même profil : une paire comparable
        foreach ($strategie in @("A", "B")) {
            & "$PSScriptRoot\lancer-essai.ps1" -Strategie $strategie -Profil $profil -Graine $graine
            if ($LASTEXITCODE -ne 0) { $echecs += "$strategie/$profil/graine $graine" }
        }
    }
}

Write-Host "Campagne terminée. Résultats dans le dossier results."
if ($echecs.Count -gt 0) {
    Write-Host "Essais en échec :" -ForegroundColor Red
    $echecs | ForEach-Object { Write-Host "  $_" }
    exit 1
}
