-- ============================================================
-- Schema PostgreSQL - Application Inventaire Blockchain
-- Version : 1.0.0
-- Base     : inventaire_db
-- ============================================================

-- Extension UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Types ENUM
-- ============================================================

CREATE TYPE role_utilisateur AS ENUM ('ADMIN', 'GESTIONNAIRE', 'AUDITEUR', 'OPERATEUR');
CREATE TYPE type_transaction  AS ENUM ('ENTREE', 'SORTIE', 'TRANSFERT');
CREATE TYPE statut_transaction AS ENUM ('EN_ATTENTE', 'CONFIRMEE', 'ECHOUEE');

-- ============================================================
-- Table utilisateurs
-- ============================================================
CREATE TABLE utilisateurs (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom                 VARCHAR(200) NOT NULL,
    prenom              VARCHAR(200) NOT NULL,
    email               VARCHAR(200) UNIQUE NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,              -- BCrypt hash
    role                role_utilisateur NOT NULL DEFAULT 'OPERATEUR',
    actif               BOOLEAN      NOT NULL DEFAULT true,
    premier_login       BOOLEAN      NOT NULL DEFAULT true, -- doit changer mdp
    tentatives_echec    INTEGER      NOT NULL DEFAULT 0,
    verrouille_jusqu_au TIMESTAMP,
    derniere_connexion  TIMESTAMP,
    cle_publique        TEXT,                               -- signature blockchain
    cree_le             TIMESTAMP    NOT NULL DEFAULT NOW(),
    modifie_le          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Table categories
-- ============================================================
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom         VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- ============================================================
-- Table entrepots
-- ============================================================
CREATE TABLE entrepots (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom             VARCHAR(200) NOT NULL,
    adresse         TEXT,
    responsable_id  UUID         REFERENCES utilisateurs(id),
    actif           BOOLEAN      NOT NULL DEFAULT true,
    metadata        JSONB        DEFAULT '{}'
);

-- ============================================================
-- Table produits
-- ============================================================
CREATE TABLE produits (
    id                          UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference                   VARCHAR(100)   UNIQUE NOT NULL,
    nom                         VARCHAR(200)   NOT NULL,
    description                 TEXT,
    categorie_id                UUID           REFERENCES categories(id),
    unite_mesure                VARCHAR(50)    NOT NULL DEFAULT 'unite',
    seuil_critique              INTEGER        NOT NULL DEFAULT 10,
    seuil_reapprovisionnement   INTEGER        NOT NULL DEFAULT 20,
    prix_unitaire               DECIMAL(10,2),
    image_path                  VARCHAR(500),
    actif                       BOOLEAN        NOT NULL DEFAULT true,
    metadata                    JSONB          DEFAULT '{}',
    cree_le                     TIMESTAMP      NOT NULL DEFAULT NOW(),
    modifie_le                  TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Table stock actuel (snapshot rapide par entrepot)
-- ============================================================
CREATE TABLE stock_actuel (
    produit_id  UUID      REFERENCES produits(id)  NOT NULL,
    entrepot_id UUID      REFERENCES entrepots(id) NOT NULL,
    quantite    INTEGER   NOT NULL DEFAULT 0 CHECK (quantite >= 0),
    derniere_maj TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (produit_id, entrepot_id)
);

-- ============================================================
-- Table transactions_cache (miroir PostgreSQL du ledger Fabric)
-- ============================================================
CREATE TABLE transactions_cache (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    blockchain_tx_id        VARCHAR(256)    UNIQUE,
    bloc_numero             BIGINT,
    bloc_hash               VARCHAR(256),
    type                    type_transaction NOT NULL,
    statut                  statut_transaction NOT NULL DEFAULT 'EN_ATTENTE',
    produit_id              UUID            REFERENCES produits(id) NOT NULL,
    quantite                INTEGER         NOT NULL CHECK (quantite > 0),
    quantite_avant          INTEGER,
    quantite_apres          INTEGER,
    operateur_id            UUID            REFERENCES utilisateurs(id) NOT NULL,
    entrepot_source_id      UUID            REFERENCES entrepots(id),
    entrepot_destination_id UUID            REFERENCES entrepots(id),
    metadata                JSONB           DEFAULT '{}',
    commentaire             TEXT,
    enregistre_le           TIMESTAMP       NOT NULL DEFAULT NOW(),
    confirme_le             TIMESTAMP
);

-- ============================================================
-- Table logs de connexion
-- ============================================================
CREATE TABLE logs_connexion (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    utilisateur_id  UUID         REFERENCES utilisateurs(id),
    email_tente     VARCHAR(200),
    succes          BOOLEAN      NOT NULL,
    message         VARCHAR(500),
    heure           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Index de performance
-- ============================================================
CREATE INDEX idx_transactions_produit    ON transactions_cache(produit_id);
CREATE INDEX idx_transactions_date       ON transactions_cache(enregistre_le DESC);
CREATE INDEX idx_transactions_operateur  ON transactions_cache(operateur_id);
CREATE INDEX idx_transactions_statut     ON transactions_cache(statut);
CREATE INDEX idx_transactions_metadata   ON transactions_cache USING gin(metadata);
CREATE INDEX idx_stock_produit           ON stock_actuel(produit_id);
CREATE INDEX idx_produits_reference      ON produits(reference);
CREATE INDEX idx_produits_categorie      ON produits(categorie_id);
CREATE INDEX idx_logs_connexion_date     ON logs_connexion(heure DESC);
CREATE INDEX idx_utilisateurs_email      ON utilisateurs(email);

-- ============================================================
-- Donnees initiales
-- ============================================================

-- Categories par defaut
INSERT INTO categories (nom, description) VALUES
    ('electronique',        'Materiel electronique et informatique'),
    ('Consommables',        'Fournitures et consommables bureau'),
    ('Outillage',           'Outils et equipements atelier'),
    ('Matieres premieres',  'Matieres premieres pour production'),
    ('Produits finis',      'Produits prets à la livraison');

-- Entrepot principal par defaut
INSERT INTO entrepots (nom, adresse) VALUES
    ('Entrepot Principal', '1 Rue de l''Industrie, 75001 Paris'),
    ('Entrepot Secondaire', '2 Avenue du Commerce, 69001 Lyon');

-- Compte administrateur systeme
-- Mot de passe : Admin@1234 (BCrypt $2a$12$)
-- IMPORTANT : remplacer le hash par un vrai hash BCrypt en production
INSERT INTO utilisateurs (nom, prenom, email, password_hash, role, premier_login)
VALUES (
    'Admin',
    'Systeme',
    'admin@inventaire.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewJyoui/R6VoCPuO',
    'ADMIN',
    false
);

-- Utilisateur gestionnaire de demonstration
-- Mot de passe : Gestionnaire@1234
INSERT INTO utilisateurs (nom, prenom, email, password_hash, role)
VALUES (
    'Dupont',
    'Marie',
    'gestionnaire@inventaire.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewJyoui/R6VoCPuO',
    'GESTIONNAIRE'
);

-- Produits de demonstration
INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-001',
    'Ordinateur portable Dell',
    'Dell Latitude 5540 - i5/16Go/512SSD',
    id, 2, 5, 899.99
FROM categories WHERE nom = 'electronique';

INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-002',
    'Ramette papier A4',
    '500 feuilles 80g/m2',
    id, 10, 30, 4.99
FROM categories WHERE nom = 'Consommables';

INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-003',
    'Clavier mecanique',
    'Clavier Cherry MX Blue USB',
    id, 5, 10, 79.99
FROM categories WHERE nom = 'electronique';

-- Stock initial de demonstration
INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 15
FROM produits p, entrepots e
WHERE p.reference = 'PROD-001' AND e.nom = 'Entrepot Principal';

INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 8
FROM produits p, entrepots e
WHERE p.reference = 'PROD-001' AND e.nom = 'Entrepot Secondaire';

INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 25
FROM produits p, entrepots e
WHERE p.reference = 'PROD-002' AND e.nom = 'Entrepot Principal';

INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 3
FROM produits p, entrepots e
WHERE p.reference = 'PROD-003' AND e.nom = 'Entrepot Principal';

-- ============================================================
-- Fin du schema
-- ============================================================
