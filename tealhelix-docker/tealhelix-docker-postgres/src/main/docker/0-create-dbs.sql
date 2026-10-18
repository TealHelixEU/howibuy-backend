-- HOWIBUY
CREATE USER th_howibuy WITH PASSWORD 'th_howibuy';
CREATE SCHEMA AUTHORIZATION th_howibuy;
ALTER USER th_howibuy SET search_path = th_howibuy, public;
GRANT CONNECT ON DATABASE tealhelix TO th_howibuy;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA th_howibuy TO th_howibuy;
ALTER SCHEMA th_howibuy OWNER TO th_howibuy;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA th_howibuy GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO th_howibuy;

-- CATALOGUE (Ingestion)
CREATE USER th_catalogue WITH PASSWORD 'th_catalogue';
CREATE SCHEMA AUTHORIZATION th_catalogue;
ALTER USER th_catalogue SET search_path = th_catalogue, public;
GRANT CONNECT ON DATABASE tealhelix TO th_catalogue;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA th_catalogue TO th_catalogue;
ALTER SCHEMA th_catalogue OWNER TO th_catalogue;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA th_catalogue GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO th_catalogue;

-- KEYCLOAK
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE SCHEMA AUTHORIZATION keycloak;
ALTER USER keycloak SET search_path = keycloak, public;
GRANT CONNECT ON DATABASE tealhelix TO keycloak;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA keycloak TO keycloak;
ALTER SCHEMA keycloak OWNER TO keycloak;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA keycloak GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO keycloak;
