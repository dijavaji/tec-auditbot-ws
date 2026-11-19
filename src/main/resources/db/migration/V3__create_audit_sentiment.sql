-- V3__create_audit_sentiment.sql
-- Creación de la tabla de análisis de sentimiento por mensaje (US-04)

CREATE TABLE audit_sentiment (
    sentiment_id   CHAR(36)        NOT NULL,
    audit_id       CHAR(36)        NOT NULL,
    message_id     VARCHAR(128)    NOT NULL,
    label          VARCHAR(32)     NOT NULL,
    score          DECIMAL(4,3)    NOT NULL,
    sequence_order INT             NOT NULL,
    created_date   DATETIME(6)     NOT NULL,

    CONSTRAINT pk_audit_sentiment PRIMARY KEY (sentiment_id),
    CONSTRAINT fk_audit_sentiment_audit_result 
        FOREIGN KEY (audit_id) REFERENCES audit_result (audit_id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_audit_sentiment_audit_id ON audit_sentiment (audit_id);
