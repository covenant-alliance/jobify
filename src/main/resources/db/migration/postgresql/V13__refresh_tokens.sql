-- Refresh tokens (#95): opaque, stored only as a SHA-256 hash, replaced on every use. One family per sign-in.
create table refresh_tokens (
    id          varchar(255) primary key,
    token_hash  varchar(64) not null,
    username    varchar(255) not null,
    family_id   varchar(255) not null,
    created_at  timestamp(6) not null,
    expires_at  timestamp(6) not null,
    used_at     timestamp(6),
    revoked_at  timestamp(6),
    constraint uk_refresh_tokens_hash unique (token_hash)
);
create index idx_refresh_tokens_username on refresh_tokens (username);
create index idx_refresh_tokens_family on refresh_tokens (family_id);
create index idx_refresh_tokens_expires on refresh_tokens (expires_at);
