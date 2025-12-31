FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl wget

# Download Full Snowstorm
# Check https://github.com/IHTSDO/snowstorm/releases for latest version
ARG SNOWSTORM_VERSION=8.12.0
RUN wget -O snowstorm.jar \
    https://github.com/IHTSDO/snowstorm/releases/download/v${SNOWSTORM_VERSION}/snowstorm-${SNOWSTORM_VERSION}.jar || \
    echo "Warning: Could not download Snowstorm. Please check version and URL."

# Copy application configuration if needed
# COPY application.yml /app/application.yml

# Expose Snowstorm port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run Snowstorm
# Configure Elasticsearch connection via environment variables
ENTRYPOINT ["java", "-Xmx1024m", "-Xms512m", "-jar", "snowstorm.jar"]

