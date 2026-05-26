CREATE TABLE IF NOT EXISTS food_diary_notes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    diary_date DATE NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_food_diary_notes_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_food_diary_notes_user_date UNIQUE (user_id, diary_date)
);
