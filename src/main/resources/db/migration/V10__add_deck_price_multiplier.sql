-- V10: Add price_multiplier column to Deck table for deck-tier pricing
ALTER TABLE "Deck" ADD COLUMN IF NOT EXISTS "price_multiplier" NUMERIC(5, 2) NOT NULL DEFAULT 1.00;
