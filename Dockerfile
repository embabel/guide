# Use JRE for runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the pre-built jar from local target directory
# Build the JAR locally first with: mvn clean package -DskipTests
COPY target/*.jar app.jar

# Expose the application port
EXPOSE 1337

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
