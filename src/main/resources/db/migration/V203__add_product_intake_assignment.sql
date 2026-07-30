alter table food_product_review_cases
    add column if not exists assigned_admin_email varchar(255),
    add column if not exists review_claimed_at timestamp;

create index if not exists idx_food_review_cases_assignment_queue
    on food_product_review_cases (assigned_admin_email, status, created_at desc);

comment on column food_product_review_cases.review_claimed_at is
    'Timestamp when the current assignment was claimed or reassigned; cleared on release.';