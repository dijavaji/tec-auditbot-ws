-- V2__create_audit_gap.sql
-- Creación de la tabla de gaps de conocimiento detectados (US-03)

CREATE TABLE audit_gap (
    gap_id           CHAR(36)        NOT NULL,
    audit_id         CHAR(36)        NOT NULL,
    message_id       VARCHAR(128)    NOT NULL,
    question_text    TEXT            NOT NULL,
    confidence_score DECIMAL(4,3)    NOT NULL,
    created_date     DATETIME(6)     NOT NULL,

    CONSTRAINT pk_audit_gap PRIMARY KEY (gap_id),
    CONSTRAINT fk_audit_gap_audit_result 
        FOREIGN KEY (audit_id) REFERENCES audit_result (audit_id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_audit_gap_audit_id ON audit_gap (audit_id);
