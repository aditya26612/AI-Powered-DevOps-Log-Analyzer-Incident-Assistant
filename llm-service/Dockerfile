# Multi-stage Dockerfile for LLM Service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .

COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S devinsight && adduser -S -G devinsight devinsight
COPY --from=builder /app/target/*.jar app.jar
USER devinsight

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]

