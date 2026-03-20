import pika
import json
import logging
import time
import concurrent.futures
from config import RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_QUEUE, RABBITMQ_EXCHANGE, RABBITMQ_ROUTING_KEY
from document_service import DocumentProcessingService
from minio_client import ensure_bucket_exists
from outbox_publisher import start_outbox_publisher
from vector_store import connect_milvus

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

document_service = DocumentProcessingService()
executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)

def ack_message(ch, delivery_tag):
    if ch.is_open:
        ch.basic_ack(delivery_tag)
        logger.info("Message processed successfully. Acked.")
    else:
        logger.warning("Channel is closed, cannot ack message")

def nack_message(ch, delivery_tag):
    if ch.is_open:
        ch.basic_nack(delivery_tag=delivery_tag, requeue=True)
        logger.info("Message Nacked and Requeued.")
    else:
        logger.warning("Channel is closed, cannot nack message")

def process_and_ack(ch, connection, delivery_tag, conversation_id, doc_id, object_name, action):
    try:
        if action == "drop_partition":
            document_service.drop_partition(conversation_id)
        elif action == "delete":
            document_service.delete_document(conversation_id, doc_id)
        else:
            document_service.process_document(conversation_id, doc_id, object_name)
        connection.add_callback_threadsafe(lambda: ack_message(ch, delivery_tag))
    except Exception as e:
        logger.error(f"Error in background processing: {e}", exc_info=True)
        connection.add_callback_threadsafe(lambda: nack_message(ch, delivery_tag))

def process_message(ch, method, properties, body):
    logger.info(f"Received message: {body.decode()}")
    try:
        data = json.loads(body.decode())
        if isinstance(data, str):
            data = json.loads(data)
        conversation_id = data.get("conversationId")
        doc_id = data.get("documentId")
        object_name = data.get("objectName")
        routing_key = method.routing_key
        action = data.get("action", "ingest")
        if routing_key == "DROP_PARTITION_COMMAND":
            action = "drop_partition"
        elif routing_key == "document.deleting":
            action = "delete"

        if not conversation_id:
            raise ValueError("Missing conversationId in payload")
        if action != "drop_partition" and not doc_id:
            raise ValueError("Missing documentId in payload")
            
        connection = ch.connection
        executor.submit(process_and_ack, ch, connection, method.delivery_tag, conversation_id, doc_id, object_name, action)

    except Exception as e:
        logger.error(f"Error processing message: {e}", exc_info=True)
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        logger.info("Message Nacked and Requeued directly.")

def connect_rabbitmq(max_retries=10, retry_delay=5):
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
    for attempt in range(1, max_retries + 1):
        try:
            connection = pika.BlockingConnection(
                pika.ConnectionParameters(
                    host=RABBITMQ_HOST,
                    credentials=credentials,
                    heartbeat=600,
                    blocked_connection_timeout=300
                )
            )
            logger.info("Connected to RabbitMQ successfully.")
            return connection
        except pika.exceptions.AMQPConnectionError:
            logger.warning("RabbitMQ not ready, retry %d/%d in %ds...", attempt, max_retries, retry_delay)
            time.sleep(retry_delay)
    raise RuntimeError(f"Could not connect to RabbitMQ after {max_retries} retries")

def main():
    ensure_bucket_exists()
    start_outbox_publisher() 
    logger.info("Starting RabbitMQ consumer...")

    connection = connect_rabbitmq()
    channel = connection.channel()
    
    channel.exchange_declare(exchange=RABBITMQ_EXCHANGE, exchange_type='topic', durable=True)
    
    channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)
    
    for routing_key in [RABBITMQ_ROUTING_KEY, "document.deleting", "DROP_PARTITION_COMMAND"]:
        channel.queue_bind(exchange=RABBITMQ_EXCHANGE, queue=RABBITMQ_QUEUE, routing_key=routing_key)

    channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=process_message)

    logger.info(f"Waiting for messages in {RABBITMQ_QUEUE}. To exit press CTRL+C")
    channel.start_consuming()

if __name__ == "__main__":
    main()
