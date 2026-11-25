CREATE TABLE IF NOT EXISTS transaction_paiement (
    id              VARCHAR(36) PRIMARY KEY,
    reference       VARCHAR(100),
    montant         DECIMAL(12,2) NOT NULL,
    devise          CHAR(3) NOT NULL,
    etat            VARCHAR(16) NOT NULL,
    motif           VARCHAR(32),
    cree_le         TIMESTAMP(6) NOT NULL,
    termine_le      TIMESTAMP(6),
    temps_attente_file_ms DOUBLE PRECISION,
    temps_traitement_ms   DOUBLE PRECISION
);