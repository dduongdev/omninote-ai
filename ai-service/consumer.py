import pika
import json
import logging
from config import RABBITMQ_HOST, RABBITMQ_QUEUE
from document_service import DocumentProcessingService
from minio_client import ensure_bucket_exists

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

document_service = DocumentProcessingService()

def process_message(ch, method, properties, body):
    logger.info(f"Received message: {body.decode()}")
    try:
        data = json.loads(body.decode())
        conversation_id = data.get("conversationId")
        doc_id = data.get("documentId")
        object_name = data.get("objectName")

        if not all([conversation_id, doc_id, object_name]):
            raise ValueError("Missing required fields in payload")
            
        document_service.process_document(conversation_id, doc_id, object_name)

        # Ack
        ch.basic_ack(delivery_tag=method.delivery_tag)
        logger.info("Message processed successfully. Acked.")

    except Exception as e:
        logger.error(f"Error processing message: {e}", exc_info=True)
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        logger.info("Message Nacked and Requeued.")

def main():
    ensure_bucket_exists() 
    logger.info("Starting RabbitMQ consumer...")
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST))
    channel = connection.channel()
    
    channel.queue_declare(queue=RABBITMQ_QUEUE, durable=True)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=RABBITMQ_QUEUE, on_message_callback=process_message)

    logger.info(f"Waiting for messages in {RABBITMQ_QUEUE}. To exit press CTRL+C")
    channel.start_consuming()

if __name__ == "__main__":
    main()
