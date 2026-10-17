-- Additive, versioned source facts. Old rows are not restamped or reinterpreted.
ALTER TABLE food_typed_nutrient_evidence
    ADD COLUMN contract_version VARCHAR(60),
    ADD COLUMN observation_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN declared_source_amount NUMERIC,
    ADD COLUMN source_field VARCHAR(30),
    ALTER COLUMN nutrient_id DROP NOT NULL,
    ALTER COLUMN nutrient_number DROP NOT NULL,
    DROP CONSTRAINT food_typed_nutrient_evidence_provider_check,
    DROP CONSTRAINT food_typed_nutrient_evidence_basis_check,
    DROP CONSTRAINT chk_typed_nutrient_contract;

-- Explicit IS TRUE prevents NULL from bypassing PostgreSQL CHECK semantics.
ALTER TABLE food_typed_nutrient_evidence ADD CONSTRAINT chk_typed_nutrient_contract CHECK ((
    (contract_version IS NULL AND observation_only = FALSE AND provider = 'USDA_FOODDATA'
        AND basis = 'PER_100_G' AND declared_source_amount IS NULL AND source_field IS NULL AND (
        (field_name = 'vitamin_a' AND nutrient_id = 1106 AND nutrient_number = '320'
            AND source_unit = 'ug' AND semantic = 'VITAMIN_A_RAE' AND canonical_unit = 'MCG_RAE') OR
        (field_name = 'vitamin_d' AND nutrient_id = 1114 AND nutrient_number = '328'
            AND source_unit = 'ug' AND semantic = 'VITAMIN_D_D2_D3' AND canonical_unit = 'MCG') OR
        (field_name = 'sugar' AND nutrient_id = 2000 AND nutrient_number = '269'
            AND source_unit = 'g' AND semantic = 'USDA_TOTAL_SUGARS_2000' AND canonical_unit = 'G')))
    OR (contract_version = 'typed-source-observation-r5-v2' AND observation_only = TRUE
        AND declared_source_amount >= 0 AND declared_source_amount <> 'NaN'::numeric
        AND declared_source_amount <> 'Infinity'::numeric AND declared_source_amount <> '-Infinity'::numeric AND (
        (provider = 'USDA_FOODDATA' AND basis = 'PER_100_G' AND source_field IS NULL
            AND declared_source_amount = source_amount AND (
            (field_name = 'potassium' AND nutrient_id = 1092 AND nutrient_number = '306'
                AND source_unit = 'mg' AND semantic = 'POTASSIUM' AND canonical_unit = 'MG') OR
            (field_name = 'vitamin_e' AND nutrient_id = 1109 AND nutrient_number = '323'
                AND source_unit = 'mg' AND semantic = 'VITAMIN_E_ALPHA_TOCOPHEROL' AND canonical_unit = 'MG') OR
            (field_name = 'vitamin_b12' AND nutrient_id = 1178 AND nutrient_number = '418'
                AND source_unit = 'ug' AND semantic = 'VITAMIN_B12' AND canonical_unit = 'MCG')))
        OR (provider = 'OPEN_FOOD_FACTS' AND basis IN ('PER_100_G','PER_100_ML')
            AND nutrient_id IS NULL AND nutrient_number IS NULL AND source_unit = 'g' AND (
            (field_name = 'sodium' AND source_field = 'sodium_100g' AND semantic = 'SODIUM'
                AND canonical_unit = 'MG' AND source_amount = declared_source_amount * 1000) OR
            (field_name = 'fiber' AND source_field = 'fiber_100g' AND semantic = 'DIETARY_FIBER_REPORTED'
                AND canonical_unit = 'G' AND source_amount = declared_source_amount))))
    )
) IS TRUE);
