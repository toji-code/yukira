# ==============================================================================
# YUKIRA Quantitative Engine Dockerfile
# Pure vectorized Python mathematical calculation kernel (Phase 2H / V1)
# ==============================================================================

FROM python:3.12-slim AS runtime

# Set environment variables for Python runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY src/ /app/src/

# Create non-root system user
RUN groupadd -r yukira && useradd -r -g yukira -d /app -s /sbin/nologin yukira \
    && chown -R yukira:yukira /app

USER yukira

# Expose internal microservice port
EXPOSE 8001

# Healthcheck verifying FastAPI engine endpoint
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')" || exit 1

# Launch uvicorn ASGI server
CMD ["uvicorn", "src.api.main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "2"]

