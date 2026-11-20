-- Sin valores inventados para vacantes existentes.
ALTER TABLE vacancies ADD COLUMN offer_details jsonb;
ALTER TABLE vacancies ADD CONSTRAINT vacancy_offer_object
    CHECK (offer_details IS NULL OR jsonb_typeof(offer_details) = 'object');
