# ==============================================================================
# YUKIRA Backend Orchestrator Dockerfile
# Spring Boot 4 / Java 21 REST API & Calculation Orchestrator
# ==============================================================================

# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and POM for dependency resolution
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Grant execution rights and download offline dependencies where possible
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine AS runtime

# System utilities for healthchecks
RUN apk add --no-cache curl wget bash

WORKDIR /app

# Create non-root system user and storage paths
RUN addgroup -S yukira && adduser -S yukira -G yukira \
    && mkdir -p /var/data/yukira /app \
    && chown -R yukira:yukira /var/data/yukira /app

# Copy built application jar from builder stage
COPY --from=builder /workspace/target/*.jar /app/app.jar

USER yukira

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0" \
    YUKIRA_STORAGE_PATH=/var/data/yukira

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]

