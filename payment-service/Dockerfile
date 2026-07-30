FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
RUN mvn -f payment-service/pom.xml -N dependency:go-offline || true

COPY payment-service ./payment-service
RUN mvn -f payment-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/payment-service/target/payment-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]