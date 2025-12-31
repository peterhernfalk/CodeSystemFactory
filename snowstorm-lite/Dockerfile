FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl wget

# Download Snowstorm Lite
# Check https://github.com/IHTSDO/snowstorm-lite/releases for latest version
ARG SNOWSTORM_VERSION=8.12.0
RUN wget -O snowstorm.jar \
    https://github.com/IHTSDO/snowstorm-lite/releases/download/v${SNOWSTORM_VERSION}/snowstorm-lite-${SNOWSTORM_VERSION}.jar || \
    echo "Warning: Could not download Snowstorm Lite. Please check version and URL."

# Expose Snowstorm port (default: 8080)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run Snowstorm Lite
# Note: Snowstorm Lite uses embedded Elasticsearch
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "snowstorm.jar"]

