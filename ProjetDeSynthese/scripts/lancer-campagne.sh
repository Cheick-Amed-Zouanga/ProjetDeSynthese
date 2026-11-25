#!/usr/bin/env bash
# Lance la campagne complète : 2 stratégies x 3 profils x 10 graines = 60 essais.
# Exemple : ./scripts/lancer-campagne.sh        (graines 1 à 10)
#           ./scripts/lancer-campagne.sh 1 2    (graines 1 et 2 seulement)
set -u
cd "$(dirname "$0")/.."

debut="${1:-1}"
fin="${2:-10}"

docker compose build || { echo "La construction de l'image a échoué."; exit 1; }

echecs=()
for graine in $(seq "$debut" "$fin"); do
    for profil in stable croissance rafales; do
        for strategie in A B; do
            ./scripts/lancer-essai.sh "$strategie" "$profil" "$graine" \
                || echecs+=("$strategie/$profil/graine $graine")
        done
    done
done

echo "Campagne terminée. Résultats dans le dossier results."
if [ ${#echecs[@]} -gt 0 ]; then
    echo "Essais en échec :"
    printf '  %s\n' "${echecs[@]}"
    exit 1
fi
