FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY tour-service/pom.xml ./tour-service/pom.xml
RUN mvn -f tour-service/pom.xml -N dependency:go-offline || true

COPY tour-service ./tour-service
RUN mvn -f tour-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/tour-service/target/tour-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]