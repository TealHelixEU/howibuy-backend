# syntax=docker/dockerfile:1

FROM maven:3.9.15-eclipse-temurin-25 AS build

WORKDIR /workspace

# Copy only POM files first so dependency resolution can be cached.
# Regenerate this block with: ./tealhelix-architecture/src/scripts/update-dockerfile-pom-copy.sh
# BEGIN auto-pom-copy
COPY howibuy-container/howibuy-dao-hibernate-reactive/pom.xml howibuy-container/howibuy-dao-hibernate-reactive/pom.xml
COPY howibuy-container/howibuy-dao/pom.xml howibuy-container/howibuy-dao/pom.xml
COPY howibuy-container/howibuy-jaxrs/pom.xml howibuy-container/howibuy-jaxrs/pom.xml
COPY howibuy-container/howibuy/pom.xml howibuy-container/howibuy/pom.xml
COPY howibuy-container/howibuy-service-interfaces/pom.xml howibuy-container/howibuy-service-interfaces/pom.xml
COPY howibuy-container/howibuy-services/pom.xml howibuy-container/howibuy-services/pom.xml
COPY howibuy-container/howibuy-testutils/pom.xml howibuy-container/howibuy-testutils/pom.xml
COPY howibuy-container/pom.xml howibuy-container/pom.xml
COPY pom.xml pom.xml
COPY tealhelix-architecture/howibuy-model-json/pom.xml tealhelix-architecture/howibuy-model-json/pom.xml
COPY tealhelix-architecture/howibuy-model/pom.xml tealhelix-architecture/howibuy-model/pom.xml
COPY tealhelix-architecture/pom.xml tealhelix-architecture/pom.xml
COPY tealhelix-common/pom.xml tealhelix-common/pom.xml
COPY tealhelix-common/tealhelix-common-dao-reactive-hibernate/pom.xml tealhelix-common/tealhelix-common-dao-reactive-hibernate/pom.xml
COPY tealhelix-common/tealhelix-common-dao-reactive/pom.xml tealhelix-common/tealhelix-common-dao-reactive/pom.xml
COPY tealhelix-common/tealhelix-common-services-impl/pom.xml tealhelix-common/tealhelix-common-services-impl/pom.xml
COPY tealhelix-common/tealhelix-common-services/pom.xml tealhelix-common/tealhelix-common-services/pom.xml
COPY tealhelix-common/tealhelix-common-testutils/pom.xml tealhelix-common/tealhelix-common-testutils/pom.xml
COPY tealhelix-common/tealhelix-common-types/pom.xml tealhelix-common/tealhelix-common-types/pom.xml
COPY tealhelix-common/tealhelix-common-utils/pom.xml tealhelix-common/tealhelix-common-utils/pom.xml
COPY tealhelix-common/tealhelix-common-web/pom.xml tealhelix-common/tealhelix-common-web/pom.xml
COPY tealhelix-docker/pom.xml tealhelix-docker/pom.xml
COPY tealhelix-docker/tealhelix-docker-keycloak/pom.xml tealhelix-docker/tealhelix-docker-keycloak/pom.xml
COPY tealhelix-docker/tealhelix-docker-postgres/pom.xml tealhelix-docker/tealhelix-docker-postgres/pom.xml
# END auto-pom-copy

RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

COPY . .

# Build the full multi-module project and skip test execution.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /opt/quarkus

COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/*.jar ./
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/howibuy-container/howibuy/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8180

ENTRYPOINT ["java", "-jar", "/opt/quarkus/quarkus-run.jar"]
