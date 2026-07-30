# https://hub.docker.com/_/eclipse-temurin/
FROM eclipse-temurin:25 AS dbfiles

ADD target/liquibase.tar.gz /opt/liquibase/
COPY target/dbdriver*.jar /opt/liquibase/lib/
COPY target/migrations*.jar /opt/liquibase/
COPY src/main/docker/db.changelog.xml /opt/liquibase/

################################################################
# See this: https://dzone.com/articles/using-liquibase-without #
################################################################
WORKDIR /opt/liquibase
RUN ./liquibase --classpath=migrations-howibuy.jar:migrations-sfc.jar --changelog-file=db.changelog.xml --url=offline:postgresql?outputLiquibaseSql=true --driver=org.postgresql.Driver --output-default-schema=true --default-schema-name=th_howibuy update-sql --contexts=none > 1-howibuy.sql


# https://hub.docker.com/_/postgres - also change DockerImageNames!
FROM postgres:18-alpine

COPY src/main/docker/0-create-dbs.sql /docker-entrypoint-initdb.d/
COPY --from=dbfiles /opt/liquibase/*.sql /docker-entrypoint-initdb.d/sql-scripts/
COPY src/main/docker/1-populate-dbs.sh /docker-entrypoint-initdb.d/

# This sets the name of the DB created when the image runs
ENV POSTGRES_DB tealhelix
