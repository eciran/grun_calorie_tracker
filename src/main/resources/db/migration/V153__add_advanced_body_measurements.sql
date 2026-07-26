ALTER TABLE body_measurements
    ADD COLUMN shoulder_cm DOUBLE PRECISION,
    ADD COLUMN forearm_cm DOUBLE PRECISION,
    ADD COLUMN calf_cm DOUBLE PRECISION,
    ADD COLUMN left_upper_arm_cm DOUBLE PRECISION,
    ADD COLUMN right_upper_arm_cm DOUBLE PRECISION,
    ADD COLUMN left_thigh_cm DOUBLE PRECISION,
    ADD COLUMN right_thigh_cm DOUBLE PRECISION,
    ADD COLUMN left_calf_cm DOUBLE PRECISION,
    ADD COLUMN right_calf_cm DOUBLE PRECISION;

ALTER TABLE body_measurements
    DROP CONSTRAINT chk_body_measurement_values,
    ADD CONSTRAINT chk_body_measurement_values CHECK (
        weight_kg IS NOT NULL OR body_fat_percentage IS NOT NULL OR waist_cm IS NOT NULL OR
        chest_cm IS NOT NULL OR hip_cm IS NOT NULL OR upper_arm_cm IS NOT NULL OR
        thigh_cm IS NOT NULL OR neck_cm IS NOT NULL OR shoulder_cm IS NOT NULL OR
        forearm_cm IS NOT NULL OR calf_cm IS NOT NULL OR left_upper_arm_cm IS NOT NULL OR
        right_upper_arm_cm IS NOT NULL OR left_thigh_cm IS NOT NULL OR right_thigh_cm IS NOT NULL OR
        left_calf_cm IS NOT NULL OR right_calf_cm IS NOT NULL
    ),
    ADD CONSTRAINT chk_body_measurement_shoulder CHECK (shoulder_cm IS NULL OR shoulder_cm BETWEEN 20 AND 300),
    ADD CONSTRAINT chk_body_measurement_forearm CHECK (forearm_cm IS NULL OR forearm_cm BETWEEN 5 AND 100),
    ADD CONSTRAINT chk_body_measurement_calf CHECK (calf_cm IS NULL OR calf_cm BETWEEN 5 AND 150),
    ADD CONSTRAINT chk_body_measurement_left_arm CHECK (left_upper_arm_cm IS NULL OR left_upper_arm_cm BETWEEN 5 AND 150),
    ADD CONSTRAINT chk_body_measurement_right_arm CHECK (right_upper_arm_cm IS NULL OR right_upper_arm_cm BETWEEN 5 AND 150),
    ADD CONSTRAINT chk_body_measurement_left_thigh CHECK (left_thigh_cm IS NULL OR left_thigh_cm BETWEEN 10 AND 200),
    ADD CONSTRAINT chk_body_measurement_right_thigh CHECK (right_thigh_cm IS NULL OR right_thigh_cm BETWEEN 10 AND 200),
    ADD CONSTRAINT chk_body_measurement_left_calf CHECK (left_calf_cm IS NULL OR left_calf_cm BETWEEN 5 AND 150),
    ADD CONSTRAINT chk_body_measurement_right_calf CHECK (right_calf_cm IS NULL OR right_calf_cm BETWEEN 5 AND 150);
