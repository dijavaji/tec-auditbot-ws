-- V5__create_audit_recommendation.sql
-- Creación de la tabla de recomendaciones de mejora generadas automáticamente (US-07)

CREATE TABLE audit_recommendation (
    recommendation_id CHAR(36)        NOT NULL,
    audit_id          CHAR(36)        NOT NULL,
    type              VARCHAR(32)     NOT NULL,
    suggested_text    TEXT            NOT NULL,
    origin_message_id VARCHAR(128)    NULL,
    created_date      DATETIME(6)     NOT NULL,

    CONSTRAINT pk_audit_recommendation PRIMARY KEY (recommendation_id),
    CONSTRAINT fk_audit_recommendation_audit_result 
        FOREIGN KEY (audit_id) REFERENCES audit_result (audit_id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_audit_recommendation_audit_id ON audit_recommendation (audit_id);
