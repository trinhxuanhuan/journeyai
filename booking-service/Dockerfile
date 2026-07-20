FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY booking-service/pom.xml ./booking-service/pom.xml
RUN mvn -f booking-service/pom.xml -N dependency:go-offline || true

COPY booking-service ./booking-service
RUN mvn -f booking-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/booking-service/target/booking-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]