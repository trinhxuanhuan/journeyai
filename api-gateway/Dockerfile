# Build context = root repo (không phải ./api-gateway) — cần thấy parent pom.xml.
# Xem docker-compose.yml: context: . , dockerfile: api-gateway/Dockerfile

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
RUN mvn -f api-gateway/pom.xml -N dependency:go-offline || true

COPY api-gateway ./api-gateway
RUN mvn -f api-gateway/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/api-gateway/target/api-gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]