ALTER TABLE expenses
ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE expenses
ADD CONSTRAINT fk_expenses_user
FOREIGN KEY (user_id)
REFERENCES users(id);

CREATE INDEX idx_expenses_user_category_created
ON expenses (user_id, category, created_time);