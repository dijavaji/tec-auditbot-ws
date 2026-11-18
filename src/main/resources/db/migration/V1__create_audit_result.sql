-- V1__create_audit_result.sql
-- Tabla de control de auditorías (US-02).
-- Las columnas de US-03 a US-05 se declaran nullable desde esta migración
-- para evitar migraciones adicionales por historia.

CREATE TABLE audit_result (
    audit_id             CHAR(36)        NOT NULL,
    chat_id              VARCHAR(128)    NOT NULL,
    assistant_id         VARCHAR(128)    NOT NULL,
    status               VARCHAR(20)     NOT NULL,
    event_id             CHAR(36)        NOT NULL,
    attempt_count        INT             NOT NULL DEFAULT 1,
    error_message        VARCHAR(500)    NULL,
    frustration_detected TINYINT(1)      NULL,
    lost_lead_detected   TINYINT(1)      NULL,
    lead_score           DECIMAL(4,3)    NULL,
    gaps_count           INT             NULL,
    created_date         DATETIME(6)     NOT NULL,
    updated_date         DATETIME(6)     NOT NULL,
    audited_date         DATETIME(6)     NULL,

    CONSTRAINT pk_audit_result        PRIMARY KEY (audit_id),
    CONSTRAINT uk_audit_result_chat_id UNIQUE      (chat_id)
);

CREATE INDEX idx_audit_result_status ON audit_result (status);
