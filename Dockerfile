# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S wallet && adduser -S wallet -G wallet
USER wallet

COPY --from=builder /workspace/build/libs/*.jar app.jar

# INSTANCE_PORT, INSTANCE_ID and SPRING_PROFILES_ACTIVE are injected by docker-compose
EXPOSE ${INSTANCE_PORT:-8080}

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
