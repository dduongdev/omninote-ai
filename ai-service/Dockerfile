# syntax=docker/dockerfile:1
FROM python:3.11-slim

WORKDIR /app

# Install OS libraries for PaddleOCR (OpenCV requirements) + cleanup to reduce image size
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy POM/Requirements before source code to utilize Docker Layer Caching
COPY requirements.txt .

# Use BuildKit cache to speed up pip installs whenever requirements.txt is changed
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip && \
    pip install -r requirements.txt

# Copy source code after dependencies are installed
COPY . .

EXPOSE 8080
# Run consumer script
CMD ["python", "consumer.py"]


