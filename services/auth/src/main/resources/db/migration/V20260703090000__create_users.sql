CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,          -- BCrypt
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- Seed users (fixed UUIDs matching the Phase 1 seed contract). Password: 'password' (BCrypt).
INSERT INTO users (id, username, password_hash) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice',
   '$2b$10$35YFqauHPcMy8qhBtUouju2VPLctiYtOKqsvAriv7Bg72LxbXMLRu'),
  ('22222222-2222-2222-2222-222222222222', 'bob',
   '$2b$10$35YFqauHPcMy8qhBtUouju2VPLctiYtOKqsvAriv7Bg72LxbXMLRu');
