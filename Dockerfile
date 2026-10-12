
# Stage 1: Build application
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

RUN apk add --no-cache maven

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S casmsgroup \
    && adduser -S casmsuser -G casmsgroup

COPY --from=builder /build/target/*.jar app.jar

USER casmsuser:casmsgroup

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
