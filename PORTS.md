# Port assignments

We need several microservices to run in parallel in a local development environment.
For that reason we need to make sure each microservice is assigned its own ports.
This is a registry of the ports per microservice for reference and allocation.

The relevant parts of the configuration are:

- HTTP & HTTP test ports: `application.properties`
- Debug port: `pom.xml`, in the configuration of the `quarkus-maven-plugin` for the `dev` goal, under `configuration/debug`.

| Microservice (Maven module name) | HTTP | Test HTTP | Debug |
|----------------------------------|------|-----------|-------|
| **howibuy**                      | 8180 | 8181      | 5105  |
| **Keycloak**                     | 8280 | -         | -     |
