-- BETTERME
CREATE USER th_betterme WITH PASSWORD 'th_betterme';
CREATE SCHEMA AUTHORIZATION th_betterme;
ALTER USER th_betterme SET search_path = th_betterme, public;
GRANT CONNECT ON DATABASE tealhelix TO th_betterme;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA th_betterme TO th_betterme;
ALTER SCHEMA th_betterme OWNER TO th_betterme;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA th_betterme GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO th_betterme;
