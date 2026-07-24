-- ============================================================
-- SFW Footwear Wholesale — Sample / Seed Data
-- Covers all 8 test scenarios from the project specification.
-- Run this AFTER the schema has been created (DatabaseManager.open()).
-- ============================================================

-- ── Lookup tables ──────────────────────────────────────────────


INSERT OR IGNORE INTO transport_companies (name) VALUES
    ('MAHAVIR TRANSPORT'),
    ('SHREE GANESH CARRIERS'),
    ('OM LOGISTICS');

-- ── LR 1: MH/2024/001  ─ multiple items ──────────
INSERT OR IGNORE INTO lr_entries
    (lr_number, lr_date, transport_company)
VALUES
    ('MH/2024/001', '2024-01-10', 'MAHAVIR TRANSPORT');

-- Three item lines with different products and locations
INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 1, 'SWEETY', 10, 12, 'G4' FROM lr_entries e WHERE e.lr_number='MH/2024/001';

INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 2, 'SWEETY', 4, 12, 'G2' FROM lr_entries e WHERE e.lr_number='MH/2024/001';

INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 3, 'NOVA CLASSIC', 6, 10, 'G1' FROM lr_entries e WHERE e.lr_number='MH/2024/001';

-- ── LR 2: MH/2024/002  ─ second test ───────────────────
INSERT OR IGNORE INTO lr_entries
    (lr_number, lr_date, transport_company)
VALUES
    ('MH/2024/002', '2024-01-08', 'SHREE GANESH CARRIERS');

INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 1, 'NOVA RUNNER', 8, 12, 'G3'
FROM lr_entries e WHERE e.lr_number='MH/2024/002';

-- ── LR 3: MH/2024/003  ─ mixed carton counts ─────────
INSERT OR IGNORE INTO lr_entries
    (lr_number, lr_date, transport_company)
VALUES
    ('MH/2024/003', '2024-01-15', 'OM LOGISTICS');

INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 1, 'STAR SPORT', 5, 12, 'G5' FROM lr_entries e WHERE e.lr_number='MH/2024/003';
INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 2, 'STAR SPORT', 3, 12, 'RK2' FROM lr_entries e WHERE e.lr_number='MH/2024/003';
INSERT OR IGNORE INTO lr_items
    (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
SELECT e.id, 3, 'STAR CASUAL', 7, 10, 'G1' FROM lr_entries e WHERE e.lr_number='MH/2024/003';

-- ── Stock (initial snapshot matching LR 1+2+3) ────────────────
-- On first real run, stock is populated by LrService.saveLr().
-- This seed data sets up the stock directly so sample data is visible immediately.
INSERT OR REPLACE INTO stock (product_name, location, cartons, pairs_per_carton, lr_source)
VALUES
    ('SWEETY',      'G4',  10, 12, 'MH/2024/001'),
    ('SWEETY',      'G2',   4, 12, 'MH/2024/001'),
    ('NOVA CLASSIC','G1',   6, 10, 'MH/2024/001'),
    ('NOVA RUNNER', 'G3',   8, 12, 'MH/2024/002'),
    ('STAR SPORT',  'G5',   5, 12, 'MH/2024/003'),
    ('STAR SPORT',  'RK2',  3, 12, 'MH/2024/003'),
    ('STAR CASUAL', 'G1',   7, 10, 'MH/2024/003');



-- ── GD Transfer 1: SWEETY 5X8 G4→G2 (MERGE SCENARIO) ─────────
-- Before: G4=10, G2=4.  After Done: G4=7, G2=7 (single merged G2 row).
-- This transfer is pre-marked Done, and the stock above already reflects the result.
INSERT OR IGNORE INTO gd_transfers
    (transfer_date, product_name, prev_location, updated_location,
     qty_cartons, pairs, done)
VALUES
    ('2024-01-15', 'SWEETY', 'G4', 'G2', 3, 36, 1);

-- Note: because stock was seeded directly above, we adjust to show post-merge state:
UPDATE stock SET cartons = 7 WHERE product_name='SWEETY' AND location='G4';
UPDATE stock SET cartons = 7 WHERE product_name='SWEETY' AND location='G2';

-- ── GD Transfer 2: NOVA CLASSIC G1→Shop (SHOP TRANSFER) ───────
-- Source warehouse stock decreases; NO stock row created for Shop.
INSERT OR IGNORE INTO gd_transfers
    (transfer_date, product_name, prev_location, updated_location,
     qty_cartons, pairs, done)
VALUES
    ('2024-01-16', 'NOVA CLASSIC', 'G1', 'Shop', 2, 20, 1);

UPDATE stock SET cartons = 4 WHERE product_name='NOVA CLASSIC' AND location='G1';
-- (No Shop row inserted — correct per spec.)

-- ── GD Transfer 3: Pending (not yet Done) ─────────────────────
INSERT OR IGNORE INTO gd_transfers
    (transfer_date, product_name, prev_location, updated_location,
     qty_cartons, pairs, done)
VALUES
    ('2024-01-20', 'STAR SPORT', 'G5', 'G4', 2, 24, 0);
