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

DO $$
BEGIN
    CREATE TYPE role_utilisateur AS ENUM ('ADMIN', 'GESTIONNAIRE', 'AUDITEUR', 'OPERATEUR');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END$$;

DO $$
BEGIN
    CREATE TYPE type_transaction AS ENUM ('ENTREE', 'SORTIE', 'TRANSFERT');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END$$;

DO $$
BEGIN
    CREATE TYPE statut_transaction AS ENUM ('EN_ATTENTE', 'CONFIRMEE', 'ECHOUEE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END$$;

-- ============================================================
-- Table utilisateurs
-- ============================================================
CREATE TABLE IF NOT EXISTS utilisateurs (
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
CREATE TABLE IF NOT EXISTS categories (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom         VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

-- ============================================================
-- Table entrepots
-- ============================================================
CREATE TABLE IF NOT EXISTS entrepots (
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
CREATE TABLE IF NOT EXISTS produits (
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
CREATE TABLE IF NOT EXISTS stock_actuel (
    produit_id  UUID      REFERENCES produits(id)  NOT NULL,
    entrepot_id UUID      REFERENCES entrepots(id) NOT NULL,
    quantite    INTEGER   NOT NULL DEFAULT 0 CHECK (quantite >= 0),
    derniere_maj TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (produit_id, entrepot_id)
);

-- ============================================================
-- Table transactions_cache (miroir PostgreSQL du ledger Fabric)
-- ============================================================
CREATE TABLE IF NOT EXISTS transactions_cache (
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
CREATE TABLE IF NOT EXISTS logs_connexion (
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
CREATE INDEX IF NOT EXISTS idx_transactions_produit    ON transactions_cache(produit_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date       ON transactions_cache(enregistre_le DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_operateur  ON transactions_cache(operateur_id);
CREATE INDEX IF NOT EXISTS idx_transactions_statut     ON transactions_cache(statut);
CREATE INDEX IF NOT EXISTS idx_transactions_metadata   ON transactions_cache USING gin(metadata);
CREATE INDEX IF NOT EXISTS idx_stock_produit           ON stock_actuel(produit_id);
CREATE INDEX IF NOT EXISTS idx_produits_reference      ON produits(reference);
CREATE INDEX IF NOT EXISTS idx_produits_categorie      ON produits(categorie_id);
CREATE INDEX IF NOT EXISTS idx_logs_connexion_date     ON logs_connexion(heure DESC);
CREATE INDEX IF NOT EXISTS idx_utilisateurs_email      ON utilisateurs(email);

-- ============================================================
-- Donnees initiales
-- ============================================================

-- Categories par defaut
INSERT INTO categories (nom, description) VALUES
    ('electronique',        'Materiel electronique et informatique'),
    ('Consommables',        'Fournitures et consommables bureau'),
    ('Outillage',           'Outils et equipements atelier'),
    ('Matieres premieres',  'Matieres premieres pour production'),
    ('Produits finis',      'Produits prets a la livraison')
ON CONFLICT (nom) DO NOTHING;

UPDATE categories
SET nom = 'electronique',
    description = 'Materiel electronique et informatique'
WHERE nom IN ('Ã‰lectronique', 'Ã©lectronique', 'Electronique');

UPDATE categories
SET nom = 'Matieres premieres',
    description = 'Matieres premieres pour production'
WHERE nom IN ('MatiÃ¨res premiÃ¨res', 'Matieres premiÃ¨res', 'MatiÃ¨res premieres');

-- Entrepot principal par defaut
INSERT INTO entrepots (nom, adresse)
SELECT 'Entrepot Principal', '1 Rue de l''Industrie, 75001 Paris'
WHERE NOT EXISTS (
    SELECT 1 FROM entrepots WHERE nom = 'Entrepot Principal' AND adresse = '1 Rue de l''Industrie, 75001 Paris'
);

INSERT INTO entrepots (nom, adresse)
SELECT 'Entrepot Secondaire', '2 Avenue du Commerce, 69001 Lyon'
WHERE NOT EXISTS (
    SELECT 1 FROM entrepots WHERE nom = 'Entrepot Secondaire' AND adresse = '2 Avenue du Commerce, 69001 Lyon'
);

-- Compte administrateur systeme
-- Mot de passe : Admin@1234 (BCrypt $2a$12$)
-- IMPORTANT : remplacer le hash par un vrai hash BCrypt en production
INSERT INTO utilisateurs (nom, prenom, email, password_hash, role, premier_login)
VALUES (
    'Admin',
    'Systeme',
    'admin@inventaire.com',
    '$2a$12$lBI4D7cL.YEqtfPPwA/xguxjRL8vyaBVn0a1e/Ss4z4D0n7kLy8fy',
    'ADMIN',
    false
)
ON CONFLICT (email) DO NOTHING;

UPDATE utilisateurs
SET nom = 'Admin',
    prenom = 'Systeme',
    password_hash = '$2a$12$lBI4D7cL.YEqtfPPwA/xguxjRL8vyaBVn0a1e/Ss4z4D0n7kLy8fy',
    role = 'ADMIN',
    premier_login = false,
    tentatives_echec = 0,
    verrouille_jusqu_au = NULL
WHERE email = 'admin@inventaire.com';

-- Utilisateur gestionnaire de demonstration
-- Mot de passe : User@1234
INSERT INTO utilisateurs (nom, prenom, email, password_hash, role)
VALUES (
    'SI',
    'Lhoussin',
    'gestionnaire@inventaire.com',
    '$2a$12$wVycGFXS2kKAhgJMkH4WPOSlI1g70h1JuB3J6kS7j6Th.Pk7mLcxS',
    'GESTIONNAIRE'
)
ON CONFLICT (email) DO NOTHING;

UPDATE utilisateurs
SET nom = 'SI',
    prenom = 'Lhoussin',
    password_hash = '$2a$12$wVycGFXS2kKAhgJMkH4WPOSlI1g70h1JuB3J6kS7j6Th.Pk7mLcxS',
    role = 'GESTIONNAIRE',
    tentatives_echec = 0,
    verrouille_jusqu_au = NULL
WHERE email = 'gestionnaire@inventaire.com';

-- Utilisateur auditeur de demonstration
-- Mot de passe : User@1234
INSERT INTO utilisateurs (nom, prenom, email, password_hash, role)
VALUES (
    'Audit',
    'Demo',
    'auditeur@inventaire.com',
    '$2a$12$wVycGFXS2kKAhgJMkH4WPOSlI1g70h1JuB3J6kS7j6Th.Pk7mLcxS',
    'AUDITEUR'
)
ON CONFLICT (email) DO NOTHING;

UPDATE utilisateurs
SET nom = 'Audit',
    prenom = 'Demo',
    password_hash = '$2a$12$wVycGFXS2kKAhgJMkH4WPOSlI1g70h1JuB3J6kS7j6Th.Pk7mLcxS',
    role = 'AUDITEUR',
    tentatives_echec = 0,
    verrouille_jusqu_au = NULL
WHERE email = 'auditeur@inventaire.com';

-- Produits de demonstration
INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-001',
    'Ordinateur portable Dell',
    'Dell Latitude 5540 - i5/16Go/512SSD',
    id, 2, 5, 899.99
FROM categories WHERE nom = 'electronique'
ON CONFLICT (reference) DO NOTHING;

UPDATE produits
SET nom = 'Ordinateur portable Dell',
    description = 'Dell Latitude 5540 - i5/16Go/512SSD',
    seuil_critique = 2,
    seuil_reapprovisionnement = 5,
    prix_unitaire = 899.99
WHERE reference = 'PROD-001';

INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-002',
    'Ramette papier A4',
    '500 feuilles 80g/m2',
    id, 10, 30, 4.99
FROM categories WHERE nom = 'Consommables'
ON CONFLICT (reference) DO NOTHING;

UPDATE produits
SET nom = 'Ramette papier A4',
    description = '500 feuilles 80g/m2',
    seuil_critique = 10,
    seuil_reapprovisionnement = 30,
    prix_unitaire = 4.99
WHERE reference = 'PROD-002';

INSERT INTO produits (reference, nom, description, categorie_id, seuil_critique, seuil_reapprovisionnement, prix_unitaire)
SELECT
    'PROD-003',
    'Clavier mecanique',
    'Clavier Cherry MX Blue USB',
    id, 5, 10, 79.99
FROM categories WHERE nom = 'electronique'
ON CONFLICT (reference) DO NOTHING;

UPDATE produits
SET nom = 'Clavier mecanique',
    description = 'Clavier Cherry MX Blue USB',
    seuil_critique = 5,
    seuil_reapprovisionnement = 10,
    prix_unitaire = 79.99
WHERE reference = 'PROD-003';

-- Stock initial de demonstration
INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 15
FROM produits p, entrepots e
WHERE p.reference = 'PROD-001' AND e.nom = 'Entrepot Principal'
ON CONFLICT (produit_id, entrepot_id) DO NOTHING;
INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 8
FROM produits p, entrepots e
WHERE p.reference = 'PROD-001' AND e.nom = 'Entrepot Secondaire'
ON CONFLICT (produit_id, entrepot_id) DO NOTHING;

INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 25
FROM produits p, entrepots e
WHERE p.reference = 'PROD-002' AND e.nom = 'Entrepot Principal'
ON CONFLICT (produit_id, entrepot_id) DO NOTHING;
INSERT INTO stock_actuel (produit_id, entrepot_id, quantite)
SELECT p.id, e.id, 3
FROM produits p, entrepots e
WHERE p.reference = 'PROD-003' AND e.nom = 'Entrepot Principal'
ON CONFLICT (produit_id, entrepot_id) DO NOTHING;
-- ============================================================
-- Fin du schema
-- ============================================================
