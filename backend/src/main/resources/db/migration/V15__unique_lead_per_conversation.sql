-- Etap 20: a raced double-submit of the public contact form (double-click, retried
-- request) could otherwise create two leads from the same conversation. This makes the
-- database the final guard, independent of any application-level check-then-act race.
ALTER TABLE leads ADD CONSTRAINT uq_leads_conversation_id UNIQUE (conversation_id);
