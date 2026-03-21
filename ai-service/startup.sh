#!/bin/sh

# The RabbitMQ consumer is now started within the FastAPI process
# to share the AI models in memory and prevent Out-Of-Memory (OOM) errors.

# Start the FastAPI server (chat_service) on port 8080
uvicorn chat_service:app --host 0.0.0.0 --port 8080

