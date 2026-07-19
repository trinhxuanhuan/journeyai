FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
RUN mvn -f user-service/pom.xml -N dependency:go-offline || true

COPY user-service ./user-service
RUN mvn -f user-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/user-service/target/user-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]