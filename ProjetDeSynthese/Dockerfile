# Image du projet de synthèse : service de paiement simulé.
# La même image sert à trois conteneurs : le service, le contrôleur B et le générateur.

# ---------- Étape 1 : construction ----------
# Cette image contient le JDK complet, donc le compilateur javac.
FROM eclipse-temurin:25-jdk AS construction

WORKDIR /construction

# Les bibliothèques changent rarement : on les copie avant le code.
# Docker garde cette couche en cache et ne la refait pas à chaque modification du code.
COPY lib/ lib/
COPY src/ src/

# Compiler le module ProjetDeSynthese avec H2 et Gson
RUN javac -encoding UTF-8 --module-path lib -d classes $(find src -name "*.java")

# ---------- Étape 2 : exécution ----------
# Cette image contient seulement Java pour exécuter (JRE) : elle est plus petite.
FROM eclipse-temurin:25-jre

# curl sert au healthcheck : Docker l'utilise pour appeler GET /health
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# On reprend seulement le résultat de l'étape 1, pas le code source
COPY --from=construction /construction/lib/ lib/
COPY --from=construction /construction/classes/ classes/

# Dossiers de la base H2 et des résultats
RUN mkdir -p /app/data /app/results

# Le service écoute sur ce port (information pour les lecteurs et les outils)
EXPOSE 8080

# Partie fixe de la commande : lancer Java en mode module
ENTRYPOINT ["java", "--module-path", "/app/lib:/app/classes", "-m"]

# Partie par défaut : la classe à lancer. docker-compose.yml la remplace
# pour le contrôleur B et pour le générateur.
CMD ["ProjetDeSynthese/Main.ApplicationPaiement"]
