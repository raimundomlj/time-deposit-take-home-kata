FROM maven:3.9.11-eclipse-temurin-21 AS tools
WORKDIR /workspace
COPY pom.xml openapi.yaml ./
COPY src src
COPY examples/deposits.sql examples/deposits.sql

FROM tools AS build
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/time-deposit-kata-kotlin-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
