# Elasticsearch Dockerfile for Render.com
# Note: Elasticsearch requires significant memory and may need paid plan

FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.0

# Disable security features for development/simplified setup
ENV xpack.security.enabled=false
ENV discovery.type=single-node
ENV "ES_JAVA_OPTS=-Xms512m -Xmx512m"
ENV network.host=0.0.0.0

EXPOSE 9200 9300

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:9200/_cluster/health || exit 1

# Note: Elasticsearch on Render free tier may not work due to memory constraints
# Consider using a managed Elasticsearch service or upgrading to paid plan

