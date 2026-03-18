import json
import logging
import time
from minio_client import get_object, upload_text, check_exists, delete_object
from extractor import ExtractorFactory
from preprocessor import PhobertPreprocessingChain
from chunking import ContentChunker
from sentence_transformers import SentenceTransformer
from vector_store import connect_milvus, get_or_create_collection, ensure_partition, document_exists, insert_chunks, delete_document_chunks
from database import SessionLocal
from models import OutboxEvent
from config import COLLECTION_NAME

logger = logging.getLogger(__name__)

connect_milvus()
collection = get_or_create_collection(COLLECTION_NAME)

embedder = SentenceTransformer('keepitreal/vietnamese-sbert')

class DocumentProcessingService:
    def process_document(self, conversation_id: int, doc_id: str, object_name: str, max_retries: int = 3):
        partition_name = f"part_{conversation_id}"

        for attempt in range(1, max_retries + 1):
            db = SessionLocal()
            try:
                if object_name.endswith(".pdf"):
                    base_name = object_name[:-4]
                else:
                    base_name = object_name
                    
                extracted_object_name = f"{base_name}_extracted.txt"
                
                text = ""

                if check_exists(extracted_object_name):
                    logger.info("Found extracted file in MinIO, downloading...")
                    content = get_object(extracted_object_name)
                    text = content.decode("utf-8")
                else:
                    logger.info("Extracted file not found, extracting from original...")
                    content = get_object(object_name)
                    extractor = ExtractorFactory.get_extractor(object_name)
                    text = extractor.extract(object_name, content)
                    upload_text(extracted_object_name, text)

                ensure_partition(collection, partition_name)
                
                if document_exists(collection, partition_name, str(doc_id)):
                    logger.info(f"Document {doc_id} already exists in Milvus. Skipping embedding.")
                else:
                    logger.info(f"Document {doc_id} not found in Milvus. Proceeding with embedding...")

                    chunker = ContentChunker(chunk_size=500, overlap=50)
                    metadata = {"source_name": object_name, "category": "document"}
                    chunks = chunker.chunk(text, str(doc_id), metadata)
                    
                    if chunks:
                        preprocessor = PhobertPreprocessingChain()
                        logger.info(f"Preprocessing and Embedding {len(chunks)} chunks...")

                        preprocessed_texts = [preprocessor.process(c.content) for c in chunks]
                        
                        # 5. Embedding
                        logger.info(f"Embedding {len(preprocessed_texts)} chunks locally...")
                        embeddings = embedder.encode(preprocessed_texts).tolist()

                        for i, chunk in enumerate(chunks):
                            chunk.vector = embeddings[i]

                        insert_chunks(collection, partition_name, chunks)

                logger.info("Saving document.ingest.succeed OutboxEvent to DB...")
                outbox_payload = {
                    "doc_id": doc_id,
                    "extracted_object_name": extracted_object_name
                }
                event = OutboxEvent(
                    aggregate_type="Document",
                    aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                    event_type="document.ingest.succeed",
                    payload=json.dumps(outbox_payload)
                )
                db.add(event)
                db.commit()
                logger.info("Document processing successfully finished.")
                
                return 

            except Exception as e:
                db.rollback() 
                logger.warning(f"Attempt {attempt}/{max_retries} failed for doc {doc_id}. Error: {e}")
                
                if attempt == max_retries:
                    logger.error(f"All {max_retries} attempts failed. Saving document.ingest.failed event.")

                    delete_document_chunks(collection, partition_name, str(doc_id))
                    delete_object(extracted_object_name)

                    
                    try:
                        failed_payload = {
                            "doc_id": doc_id
                        }
                        failed_event = OutboxEvent(
                            aggregate_type="Document",
                            aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                            event_type="document.ingest.failed",
                            payload=json.dumps(failed_payload)
                        )
                        db.add(failed_event)
                        db.commit()
                        logger.info("Failed event saved. Dropping current MQ event.")
                    except Exception as fatal_e:
                        logger.critical(f"FATAL: Could not even save failed OutboxEvent: {fatal_e}")
                        raise fatal_e
                    
                    return 
                
                time.sleep(2)
            finally:
                db.close()
