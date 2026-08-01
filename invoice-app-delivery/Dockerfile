FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Optimize startup performance using Spring Boot layertools
FROM eclipse-temurin:21-jre-alpine AS layers

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Extracts the pre-computed Spring Boot layout structures
RUN java -Djarmode=layertools -jar app.jar extract

# Production runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user for security
RUN addgroup -S invoiceapp && adduser -S invoiceapp -G invoiceapp
USER invoiceapp

COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8082

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]