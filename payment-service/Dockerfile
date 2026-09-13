FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY event-contracts/pom.xml event-contracts/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY reservation-service/pom.xml reservation-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY event-contracts/src event-contracts/src
COPY payment-service/src payment-service/src
RUN mvn -pl payment-service -am -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java","-jar","app.jar"]
