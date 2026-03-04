-- ============================================
-- SEED DATA (runs on first startup)
-- ============================================
-- Password for all seed users: password123

-- Only insert if users table is empty (idempotent)
INSERT INTO users (name, email, password, credits, created_at, updated_at)
SELECT 'Alice Creator', 'alice@example.com', '$2b$10$NaUmyWBJEFkMW1DgRYsIEexPCHpTrFYDHMhRez5y2gkdaDBvMRoMK', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'alice@example.com');

INSERT INTO users (name, email, password, credits, created_at, updated_at)
SELECT 'Bob Bidder', 'bob@example.com', '$2b$10$NaUmyWBJEFkMW1DgRYsIEexPCHpTrFYDHMhRez5y2gkdaDBvMRoMK', 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'bob@example.com');

INSERT INTO users (name, email, password, credits, created_at, updated_at)
SELECT 'Charlie Dev', 'charlie@example.com', '$2b$10$NaUmyWBJEFkMW1DgRYsIEexPCHpTrFYDHMhRez5y2gkdaDBvMRoMK', 75, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'charlie@example.com');

