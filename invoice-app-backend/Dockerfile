FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Extract layers for faster deployment startup ──────────
FROM eclipse-temurin:21-jre-alpine AS layers

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Extracts the pre-computed Spring Boot layout structures
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: Final secure runtime image ─────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a restricted application runner context to mitigate root privilege escalation vulnerabilities
RUN addgroup -S invoiceapp && adduser -S invoiceapp -G invoiceapp
USER invoiceapp

COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]