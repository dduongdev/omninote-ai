import time
import logging
import pika
import threading
from database import SessionLocal
from models import OutboxEvent
from config import RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_EXCHANGE, RABBITMQ_INGEST_QUEUE

logger = logging.getLogger(__name__)

def publish_outbox_events():
    while True:
        db = SessionLocal()
        try:
            events = db.query(OutboxEvent).filter(OutboxEvent.is_published == False).all()
            if events:
                credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD); connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, credentials=credentials))
                channel = connection.channel()
                
                channel.exchange_declare(exchange=RABBITMQ_EXCHANGE, exchange_type='topic', durable=True)
                channel.queue_declare(queue=RABBITMQ_INGEST_QUEUE, durable=True)
                
                for r_key in ["document.ingest.succeed", "document.ingest.failed", "MILVUS_SOFT_DELETED_SUCCESS", "MILVUS_SOFT_DELETED_FAILED"]:
                    channel.queue_bind(exchange=RABBITMQ_EXCHANGE, queue=RABBITMQ_INGEST_QUEUE, routing_key=r_key)

                for event in events:
                    channel.basic_publish(
                        exchange=RABBITMQ_EXCHANGE,
                        routing_key=event.event_type,
                        body=event.payload,
                        properties=pika.BasicProperties(
                            delivery_mode=2, 
                            content_type='application/json'
                        )
                    )
                    event.is_published = True
                    logger.info(f"Published OutboxEvent #{event.id} -> {event.event_type}")

                db.commit()
                connection.close()
        except Exception as e:
            logger.error(f"Error publishing outbox events: {e}")
            db.rollback()
        finally:
            db.close()

        time.sleep(5)

def start_outbox_publisher():
    thread = threading.Thread(target=publish_outbox_events, daemon=True)
    thread.start()
    logger.info("Started Background Outbox Publisher.")
