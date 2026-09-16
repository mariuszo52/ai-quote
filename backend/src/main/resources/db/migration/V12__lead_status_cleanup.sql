-- LeadStatus dropped VIEWED (Etap 14 simplified the lifecycle to NEW/CONTACTED/
-- QUOTE_SENT/WON/LOST) — remap existing rows instead of leaving them unparseable.
UPDATE leads SET status = 'CONTACTED' WHERE status = 'VIEWED';
