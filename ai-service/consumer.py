import pika
import json
import logging
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

def process_and_ack(ch, connection, delivery_tag, conversation_id, doc_id, object_name):
    try:
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

        if not all([conversation_id, doc_id, object_name]):
            raise ValueError("Missing required fields in payload")
            
        connection = ch.connection
        executor.submit(process_and_ack, ch, connection, method.delivery_tag, conversation_id, doc_id, object_name)

    except Exception as e:
        logger.error(f"Error processing message: {e}", exc_info=True)
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        logger.info("Message Nacked and Requeued directly.")

def main():
    ensure_bucket_exists()
    start_outbox_publisher() 
    logger.info("Starting RabbitMQ consumer...")
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, credentials=credentials, heartbeat=600, blocked_connection_timeout=300))
    channel = connection.channel()
    
    channel.exchange_declare(exchange=RABBITMQ_EXCHANGE, exchange_type='topic', durable=True)
    
    channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)
    
    channel.queue_bind(exchange=RABBITMQ_EXCHANGE, queue=RABBITMQ_QUEUE, routing_key=RABBITMQ_ROUTING_KEY)

    channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=process_message)

    logger.info(f"Waiting for messages in {RABBITMQ_QUEUE}. To exit press CTRL+C")
    channel.start_consuming()

if __name__ == "__main__":
    main()
