FROM maven:3.8.1-adoptopenjdk-11 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:resolve dependency:resolve-plugins

COPY src src
RUN mvn package -Dskip.unit.tests=true -Dskip.integration.tests=true

FROM tomcat as appserver
RUN mkdir /app-artifacts
COPY documents/app-artifacts /app-artifacts

RUN mkdir /schema
COPY src/test/resources/AppData/Schema /schema

VOLUME /config
VOLUME /output
VOLUME /logs

EXPOSE 8081
RUN rm -fr /usr/local/tomcat/webapps/ROOT.war
COPY --from=builder /build/target/ecr-now.war /usr/local/tomcat/webapps/ROOT.war