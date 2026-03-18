import os
from dotenv import load_dotenv

load_dotenv()

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "omninote")

POSTGRES_URI = os.getenv("POSTGRES_DB_URI", "postgresql://postgres:postgres@localhost:5432/omninotedb")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD", "guest")
RABBITMQ_EXCHANGE = os.getenv("RABBITMQ_EXCHANGE", "document.topic.exchange")
RABBITMQ_QUEUE = os.getenv("RABBITMQ_QUEUE", "document.upload.queue")
RABBITMQ_ROUTING_KEY = os.getenv("RABBITMQ_ROUTING_KEY", "document.uploaded")
RABBITMQ_INGEST_QUEUE = os.getenv("RABBITMQ_INGEST_QUEUE", "document.ingest.queue")

MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
COLLECTION_NAME = "documents_collection"
HF_TOKEN = os.getenv("HF_TOKEN", "")
