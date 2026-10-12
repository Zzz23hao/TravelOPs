FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
# Resolve dependencies in their own layer so source-only changes reuse the cache.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests run in CI; the image build only packages.
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/travelops-core-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
