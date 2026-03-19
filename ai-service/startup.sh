#!/bin/sh

# Start the RabbitMQ consumer in the background
python consumer.py &

# Start the FastAPI server (chat_service) on port 8080
uvicorn chat_service:app --host 0.0.0.0 --port 8080

