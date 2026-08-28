FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
RUN mvn -f notification-service/pom.xml -N dependency:go-offline || true

COPY notification-service ./notification-service
RUN mvn -f notification-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/notification-service/target/notification-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
