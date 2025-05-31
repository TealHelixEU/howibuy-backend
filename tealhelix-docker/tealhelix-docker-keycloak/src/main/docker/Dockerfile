# Source: https://www.keycloak.org/server/containers
FROM quay.io/keycloak/keycloak:${version.keycloak} AS builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Configure a database vendor
ENV KC_DB=postgres

WORKDIR /opt/keycloak
# for demonstration purposes only, please make sure to use proper certificates in production instead
RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${version.keycloak}
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=keycloak:keycloak src/main/docker/start-keycloak.sh /opt/keycloak/bin/
COPY --chown=keycloak:keycloak src/main/docker/tealhelix-realm.json /opt/keycloak/data/import/
RUN chmod a+x /opt/keycloak/bin/start-keycloak.sh

# change these values to point to a running postgres instance
ENV KC_DB=postgres
ENV KC_DB_URL_HOST=postgresql
ENV KC_DB_URL_DATABASE=tealhelix
ENV KC_DB_SCHEMA=keycloak
ENV KC_DB_USERNAME=keycloak
ENV KC_DB_PASSWORD=keycloak
ENV KC_HOSTNAME=localhost
ENV KC_BOOTSTRAP_ADMIN_USERNAME=admin
ENV KC_BOOTSTRAP_ADMIN_PASSWORD=admin
# This entry point takes care of importing the realm the first time it runs
ENTRYPOINT ["/opt/keycloak/bin/start-keycloak.sh"]
