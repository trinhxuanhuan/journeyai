# Build context = root repo (không phải ./auth-service) — cần thấy parent pom.xml.
# Xem docker-compose.yml: context: . , dockerfile: auth-service/Dockerfile

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy parent pom trước để cache layer dependency khi source code đổi
COPY pom.xml ./pom.xml
COPY auth-service/pom.xml ./auth-service/pom.xml
RUN mvn -f auth-service/pom.xml -N dependency:go-offline || true

COPY auth-service ./auth-service
RUN mvn -f auth-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/auth-service/target/auth-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
