#!/bin/bash

if [ -f /opt/keycloak/bin/TEALHELIX-REALM-IMPORTED ]; then
	exec /opt/keycloak/bin/kc.sh --verbose start-dev $@
else
	touch /opt/keycloak/bin/TEALHELIX-REALM-IMPORTED
	exec /opt/keycloak/bin/kc.sh --verbose start-dev --import-realm $@
fi
