# syntax=docker/dockerfile:1

FROM maven:3.9.15-eclipse-temurin-25 AS build

WORKDIR /workspace

# Copy only POM files first so dependency resolution can be cached.
# Regenerate this block with: ./tealhelix-architecture/src/scripts/update-dockerfile-pom-copy.sh
# BEGIN auto-pom-copy
COPY catalogue-container/catalogue-dao-hibernate-reactive/pom.xml catalogue-container/catalogue-dao-hibernate-reactive/pom.xml
COPY catalogue-container/catalogue-dao/pom.xml catalogue-container/catalogue-dao/pom.xml
COPY catalogue-container/catalogue-feeds/pom.xml catalogue-container/catalogue-feeds/pom.xml
COPY catalogue-container/catalogue-jaxrs/pom.xml catalogue-container/catalogue-jaxrs/pom.xml
COPY catalogue-container/catalogue/pom.xml catalogue-container/catalogue/pom.xml
COPY catalogue-container/catalogue-service-interfaces/pom.xml catalogue-container/catalogue-service-interfaces/pom.xml
COPY catalogue-container/catalogue-services/pom.xml catalogue-container/catalogue-services/pom.xml
COPY catalogue-container/catalogue-testutils/pom.xml catalogue-container/catalogue-testutils/pom.xml
COPY catalogue-container/pom.xml catalogue-container/pom.xml
COPY catalogue-sync-cli-container/catalogue-sync-cli/pom.xml catalogue-sync-cli-container/catalogue-sync-cli/pom.xml
COPY catalogue-sync-cli-container/catalogue-sync-e2e/pom.xml catalogue-sync-cli-container/catalogue-sync-e2e/pom.xml
COPY catalogue-sync-cli-container/pom.xml catalogue-sync-cli-container/pom.xml
COPY howibuy-container/howibuy-dao-hibernate-reactive/pom.xml howibuy-container/howibuy-dao-hibernate-reactive/pom.xml
COPY howibuy-container/howibuy-dao/pom.xml howibuy-container/howibuy-dao/pom.xml
COPY howibuy-container/howibuy-jaxrs/pom.xml howibuy-container/howibuy-jaxrs/pom.xml
COPY howibuy-container/howibuy/pom.xml howibuy-container/howibuy/pom.xml
COPY howibuy-container/howibuy-service-interfaces/pom.xml howibuy-container/howibuy-service-interfaces/pom.xml
COPY howibuy-container/howibuy-services-model/pom.xml howibuy-container/howibuy-services-model/pom.xml
COPY howibuy-container/howibuy-services/pom.xml howibuy-container/howibuy-services/pom.xml
COPY howibuy-container/pom.xml howibuy-container/pom.xml
COPY howibuy-container/sfc-dao-hibernate-reactive/pom.xml howibuy-container/sfc-dao-hibernate-reactive/pom.xml
COPY howibuy-container/sfc-dao/pom.xml howibuy-container/sfc-dao/pom.xml
COPY howibuy-container/sfc-jaxrs/pom.xml howibuy-container/sfc-jaxrs/pom.xml
COPY howibuy-container/sfc-service-interfaces/pom.xml howibuy-container/sfc-service-interfaces/pom.xml
COPY howibuy-container/sfc-services/pom.xml howibuy-container/sfc-services/pom.xml
COPY howibuy-container/sustainability-scoring/pom.xml howibuy-container/sustainability-scoring/pom.xml
COPY pom.xml pom.xml
COPY tealhelix-architecture/catalogue-model/pom.xml tealhelix-architecture/catalogue-model/pom.xml
COPY tealhelix-architecture/catalogue-model-testutils/pom.xml tealhelix-architecture/catalogue-model-testutils/pom.xml
COPY tealhelix-architecture/howibuy-model-json/pom.xml tealhelix-architecture/howibuy-model-json/pom.xml
COPY tealhelix-architecture/howibuy-model/pom.xml tealhelix-architecture/howibuy-model/pom.xml
COPY tealhelix-architecture/howibuy-model-testutils/pom.xml tealhelix-architecture/howibuy-model-testutils/pom.xml
COPY tealhelix-architecture/pom.xml tealhelix-architecture/pom.xml
COPY tealhelix-architecture/sfc-model/pom.xml tealhelix-architecture/sfc-model/pom.xml
COPY tealhelix-common/pom.xml tealhelix-common/pom.xml
COPY tealhelix-common/tealhelix-common-dao-reactive-hibernate/pom.xml tealhelix-common/tealhelix-common-dao-reactive-hibernate/pom.xml
COPY tealhelix-common/tealhelix-common-dao-reactive/pom.xml tealhelix-common/tealhelix-common-dao-reactive/pom.xml
COPY tealhelix-common/tealhelix-common-jee/pom.xml tealhelix-common/tealhelix-common-jee/pom.xml
COPY tealhelix-common/tealhelix-common-services-impl/pom.xml tealhelix-common/tealhelix-common-services-impl/pom.xml
COPY tealhelix-common/tealhelix-common-services/pom.xml tealhelix-common/tealhelix-common-services/pom.xml
COPY tealhelix-common/tealhelix-common-testutils/pom.xml tealhelix-common/tealhelix-common-testutils/pom.xml
COPY tealhelix-common/tealhelix-common-types-json/pom.xml tealhelix-common/tealhelix-common-types-json/pom.xml
COPY tealhelix-common/tealhelix-common-types/pom.xml tealhelix-common/tealhelix-common-types/pom.xml
COPY tealhelix-common/tealhelix-common-utils/pom.xml tealhelix-common/tealhelix-common-utils/pom.xml
COPY tealhelix-common/tealhelix-common-web/pom.xml tealhelix-common/tealhelix-common-web/pom.xml
COPY tealhelix-docker/pom.xml tealhelix-docker/pom.xml
COPY tealhelix-docker/tealhelix-docker-keycloak/pom.xml tealhelix-docker/tealhelix-docker-keycloak/pom.xml
COPY tealhelix-docker/tealhelix-docker-postgres/pom.xml tealhelix-docker/tealhelix-docker-postgres/pom.xml
# END auto-pom-copy

RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

COPY . .

# Swagger UI configuration is fixed at augmentation time, so it has to be present in the environment
# of the Maven build rather than in the runtime container. This one is used to overcome the path swap
# in the production URLs: api/howibuy/v1 becomes howibuy/api/v1 (api/catalogue/v1 becomes catalogue/api/v1 when building
# the catalogue target). The build compiles every deployable with the one value given, so it is right only for the
# deployable of the target being built.
ARG QUARKUS_SWAGGER_UI_URLS_DEFAULT

# The JAX-RS application path is baked into the OpenAPI path keys during the same augmentation, so leaving it out is
# also fixed here. The published paths are then relative to the base path named by QUARKUS_SMALLRYE_OPENAPI_SERVERS,
# which is runtime configuration and belongs in the container environment. The two go together: with the application
# path gone and no base path named, the document stops saying where its operations are reached at all.
ARG MP_OPENAPI_EXTENSIONS_SMALLRYE_APPLICATION_PATH_DISABLE

# Build the full multi-module project and skip test execution.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests

# One final stage per deployable, picked with --target (the target of a Compose build). The Ingestion of Retailer
# Catalogues comes first, so that a build without a target still makes HowiBuy, the last stage.
FROM eclipse-temurin:25-jre AS catalogue
WORKDIR /opt/quarkus

COPY --from=build /workspace/catalogue-container/catalogue/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/catalogue-container/catalogue/target/quarkus-app/*.jar ./
COPY --from=build /workspace/catalogue-container/catalogue/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/catalogue-container/catalogue/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8380

ENTRYPOINT ["java", "-jar", "/opt/quarkus/quarkus-run.jar"]

FROM eclipse-temurin:25-jre AS howibuy
WORKDIR /opt/quarkus

COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/*.jar ./
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8180

ENTRYPOINT ["java", "-jar", "/opt/quarkus/quarkus-run.jar"]
