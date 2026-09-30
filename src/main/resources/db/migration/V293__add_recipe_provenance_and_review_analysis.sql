alter table recipes
    add column if not exists source_type varchar(32) not null default 'LEGACY_UNKNOWN',
    add column if not exists source_ai_request_id bigint,
    add column if not exists source_import_candidate_id bigint;

create table if not exists recipe_review_analyses (
    id bigserial primary key,
    recipe_id bigint not null references recipes(id),
    content_hash varchar(64) not null,
    status varchar(24) not null,
    risk_level varchar(32),
    quality_score integer,
    deterministic_score integer,
    ai_score integer,
    confidence double precision,
    review_required boolean,
    critical_issue boolean,
    summary text,
    deterministic_result_json text,
    ai_result_json text,
    error_message text,
    provider varchar(40),
    model varchar(160),
    prompt_version varchar(160),
    latency_ms bigint,
    total_tokens integer,
    estimated_cost double precision,
    cost_currency varchar(16),
    requested_by varchar(320),
    created_at timestamp not null,
    completed_at timestamp
);

create index if not exists idx_recipe_review_analysis_recipe_created
    on recipe_review_analyses(recipe_id, created_at desc);
create index if not exists idx_recipe_review_analysis_status
    on recipe_review_analyses(status);
