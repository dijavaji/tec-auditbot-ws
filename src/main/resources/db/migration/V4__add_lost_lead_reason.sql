-- V4__add_lost_lead_reason.sql
-- Agregar motivo de oportunidad de venta perdida en audit_result (US-05)

ALTER TABLE audit_result 
    ADD COLUMN lost_lead_reason VARCHAR(500) NULL;
