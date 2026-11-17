From eclipse-temurin:21-jdk as builder

# Set the working directory
WORKDIR /app

# Copy the pom.xml and the source code
COPY . .

# Grant execution permissions to mvnw
RUN chmod +x ./mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine

# Run without root privileges
RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

USER app

# Expose the application port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["sh", "-c", "wget -q --spider http://localhost:${PORT:-8081}/health"]

# Set the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
