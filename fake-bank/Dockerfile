FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S bank && adduser -S bank -G bank
USER bank

COPY --from=builder /workspace/build/libs/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
