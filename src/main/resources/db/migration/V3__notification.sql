-- V3__notification.sql
-- Email templates (edited in the DB, no redeploy needed), a send-attempt audit log,
-- and the Spring Modulith event-publication registry (transactional outbox for domain events).

CREATE TABLE email_template (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    subject     VARCHAR(300) NOT NULL,
    body_html   TEXT         NOT NULL,
    locale      VARCHAR(10)  NOT NULL DEFAULT 'en',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL,
    CONSTRAINT uq_email_template_code_locale UNIQUE (code, locale)
);

CREATE TABLE notification_log (
    id                  UUID         PRIMARY KEY,
    recipient           VARCHAR(320) NOT NULL,
    template_code       VARCHAR(100) NOT NULL,
    subject             VARCHAR(300),
    status              VARCHAR(20)  NOT NULL,
    provider_message_id VARCHAR(200),
    error_detail        TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL
);
CREATE INDEX idx_notification_log_recipient ON notification_log (recipient);
CREATE INDEX idx_notification_log_template_code ON notification_log (template_code);

-- Spring Modulith JDBC event publication registry (the transactional outbox) — schema v2,
-- taken verbatim from spring-modulith-events-jdbc (adds status/retry columns over v1).
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Seed a sample template so the feature works out of the box. Subject is a Thymeleaf TEXT template,
-- body an HTML template; [(${var})] / [[${var}]] are substituted at send time.
INSERT INTO email_template (id, code, subject, body_html, locale, active, description,
                            created_at, updated_at, created_by, version)
VALUES (
    gen_random_uuid(),
    'WELCOME',
    'Welcome to Anytime Work, [(${name})]!',
    '<p>Hi [[${name}]],</p><p>Your Anytime Work account is ready to use.</p>',
    'en',
    TRUE,
    'Sent to a newly onboarded user',
    now(), now(), 'system', 0
);
