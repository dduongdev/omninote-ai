# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy only pom.xml first to download dependencies
COPY pom.xml .
# Use BuildKit cache mount for Maven dependencies and parallel build
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -T 1C

# Copy source code and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B -T 1C

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk --no-cache add curl

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

