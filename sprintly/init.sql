-- ============================================================================
-- TaskFlow PostgreSQL Schema Initialization
-- ============================================================================
-- Run this against the PostgreSQL docker container as postgres user:
--   psql -h localhost -U postgres -d taskflow_db -f init.sql
-- ============================================================================

-- Create ENUM types
CREATE TYPE user_role AS ENUM ('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_DEVELOPER');

-- ============================================================================
-- users table
-- ============================================================================
-- Central identity object referenced by auth, user, and task modules
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255),  -- NULL for OAuth2-only users
    role user_role NOT NULL DEFAULT 'ROLE_DEVELOPER',
    oauth2_provider VARCHAR(30),  -- e.g. 'google', 'github'
    oauth2_provider_id VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE(email)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_oauth2 ON users(oauth2_provider, oauth2_provider_id);

-- ============================================================================
-- refresh_tokens table
-- ============================================================================
-- Persists refresh tokens for revocation and rotation
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ============================================================================
-- tasks table
-- ============================================================================
CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'TODO',
    created_by BIGINT NOT NULL,
    assigned_to BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_creator FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY(assigned_to) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_creator ON tasks(created_by);
CREATE INDEX idx_tasks_assignee ON tasks(assigned_to);

-- ============================================================================
-- notifications table
-- ============================================================================
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    recipient_id BIGINT NOT NULL,
    sender_id BIGINT,
    entity_id BIGINT,
    entity_type VARCHAR(50),
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_sender FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id);
CREATE INDEX idx_notifications_read ON notifications(read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- ============================================================================
-- Sample data (optional — for development)
-- ============================================================================
-- Uncomment to seed initial data
-- INSERT INTO users (name, email, password, role, enabled) VALUES
-- ('Admin User', 'admin@taskflow.com', '$2a$10$...bcrypt_hash...', 'ROLE_ADMIN', true),
-- ('Manager User', 'manager@taskflow.com', '$2a$10$...bcrypt_hash...', 'ROLE_MANAGER', true),
-- ('Developer User', 'dev@taskflow.com', '$2a$10$...bcrypt_hash...', 'ROLE_DEVELOPER', true);
