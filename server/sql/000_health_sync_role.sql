-- Usage:
-- psql --set=health_sync_password='LONG_RANDOM_PASSWORD' -f 000_health_sync_role.sql
\if :{?health_sync_password}
\else
\quit
\endif

SELECT format('CREATE ROLE henji_sync LOGIN PASSWORD %L', :'health_sync_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'henji_sync')
\gexec

ALTER ROLE henji_sync
    WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 8
    PASSWORD :'health_sync_password';
ALTER ROLE henji_sync IN DATABASE personal_knowledge SET search_path = health, public;
GRANT CONNECT ON DATABASE personal_knowledge TO henji_sync;
