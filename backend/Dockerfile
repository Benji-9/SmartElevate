# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /workspace

# Primero wrapper + pom para cachear dependencias entre builds.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/smartelevate-api-*.jar app.jar

# ---- Runtime ----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S -G spring spring
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
USER spring

# El puerto lo define la plataforma de hosting vía PORT (Spring lo lee en application.yml).
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
