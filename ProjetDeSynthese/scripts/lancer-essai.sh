#!/usr/bin/env bash
# Lance un seul essai avec Docker Compose (Linux, macOS ou Git Bash).
# Exemple, depuis le dossier du projet :
#   ./scripts/lancer-essai.sh B rafales 3
set -u

if [ $# -ne 3 ]; then
    echo "Utilisation : $0 <A|B> <stable|croissance|rafales> <graine 1-10>"
    exit 2
fi

case "$1" in A|B) ;; *) echo "Stratégie : A ou B"; exit 2 ;; esac
case "$2" in stable|croissance|rafales) ;; *) echo "Profil : stable, croissance ou rafales"; exit 2 ;; esac
case "$3" in [1-9]|10) ;; *) echo "Graine : de 1 à 10"; exit 2 ;; esac

cd "$(dirname "$0")/.."

export STRATEGIE="$1" PROFIL="$2" GRAINE="$3"
if [ "$STRATEGIE" = "A" ]; then export WORKERS_DEPART=16; else export WORKERS_DEPART=5; fi

options=()
if [ "$STRATEGIE" = "B" ]; then options=(--profile strategie-b); fi

dossier="results/$STRATEGIE/${PROFIL}_s$GRAINE"
mkdir -p "$dossier"

echo "=== Essai $STRATEGIE / $PROFIL / graine $GRAINE ==="

# Si l'image n'existe pas encore, Compose la construit d'abord.
docker compose ${options[@]+"${options[@]}"} up --attach generateur \
    --abort-on-container-exit --exit-code-from generateur
code=$?

docker compose ${options[@]+"${options[@]}"} logs --no-color --no-log-prefix service-paiement > "$dossier/service.log"
if [ "$STRATEGIE" = "B" ]; then
    docker compose ${options[@]+"${options[@]}"} logs --no-color --no-log-prefix controleur-b > "$dossier/adaptatif.log"
fi

docker compose ${options[@]+"${options[@]}"} down --volumes

if [ $code -ne 0 ]; then echo "Essai en échec (code $code). Voir $dossier"; fi
exit $code
