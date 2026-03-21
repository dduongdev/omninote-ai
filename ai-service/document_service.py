import json
import logging
import time
import torch
from minio_client import get_object, upload_text, check_exists, delete_object
from extractor import ExtractorFactory
from preprocessor import PhobertPreprocessingChain
from chunking import ContentChunker
from FlagEmbedding import BGEM3FlagModel
from vector_store import connect_milvus, get_or_create_collection, ensure_partition, document_exists, insert_chunks, delete_document_chunks, drop_partition_if_exists
from database import SessionLocal
from models import OutboxEvent
from config import COLLECTION_NAME

logger = logging.getLogger(__name__)

connect_milvus()
collection = get_or_create_collection(COLLECTION_NAME)

def get_embedder():
    try:
        from chat_service import AI_MODELS
        if "embedder" in AI_MODELS and AI_MODELS["embedder"] is not None:
            return AI_MODELS["embedder"]
    except ImportError:
        pass
    
    # Fallback if running document_service standalone
    global _local_embedder
    if '_local_embedder' not in globals():
        from FlagEmbedding import BGEM3FlagModel
        logger.info("Loading local explicit BGEM3FlagModel...")
        device = "cuda" if torch.cuda.is_available() else "cpu"
        use_fp16 = torch.cuda.is_available()
        _local_embedder = BGEM3FlagModel('BAAI/bge-m3', use_fp16=use_fp16, device=device)
    return _local_embedder

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

                    chunker = ContentChunker(chunk_size=250, overlap=20)
                    metadata = {"source_name": object_name, "category": "document"}
                    chunks = chunker.chunk(text, str(doc_id), metadata)
                    
                    if chunks:
                        preprocessor = PhobertPreprocessingChain()
                        logger.info(f"Preprocessing and Embedding {len(chunks)} chunks...")

                        preprocessed_texts = [preprocessor.process(c.content) for c in chunks]
                        
                        # 5. Embedding (Hybrid: Dense + Sparse)
                        logger.info(f"Embedding {len(preprocessed_texts)} chunks locally (Hybrid)...")

                        current_embedder = get_embedder()
                        outputs = current_embedder.encode(preprocessed_texts, return_dense=True, return_sparse=True, return_colbert_vecs=False)
                        dense_embeddings = outputs['dense_vecs'].tolist()
                        lexical_weights = outputs['lexical_weights']

                        for i, chunk in enumerate(chunks):
                            chunk.vector = dense_embeddings[i]
                            # PyMilvus expects dict[int, float] cho SPARSE_FLOAT_VECTOR (int keyword id)
                            chunk.sparse_vector = {int(k): float(v) for k, v in lexical_weights[i].items()}

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

    def delete_document(self, conversation_id: int, doc_id: str):
        partition_name = f"part_{conversation_id}"
        db = SessionLocal()
        try:
            delete_document_chunks(collection, partition_name, str(doc_id))

            payload = {"doc_id": doc_id}
            event = OutboxEvent(
                aggregate_type="Document",
                aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                event_type="MILVUS_SOFT_DELETED_SUCCESS",
                payload=json.dumps(payload)
            )
            db.add(event)
            db.commit()
            logger.info(f"Hard deleted doc {doc_id} from Milvus and saved success event.")
        except Exception as e:
            db.rollback()
            logger.error(f"Failed to delete doc {doc_id} from Milvus. Error: {e}")

            payload = {"doc_id": doc_id, "error": str(e)}
            failed_event = OutboxEvent(
                aggregate_type="Document",
                aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                event_type="MILVUS_SOFT_DELETED_FAILED",
                payload=json.dumps(payload)
            )
            try:
                db.add(failed_event)
                db.commit()
            except Exception as fe:
                db.rollback()
                logger.critical(f"FATAL: Could not save failed OutboxEvent for delete: {fe}")
        finally:
            db.close()

    def drop_partition(self, conversation_id: int):
        partition_name = f"part_{conversation_id}"
        try:
            drop_partition_if_exists(collection, partition_name)
            logger.info(f"Dropped partition {partition_name} for conversation {conversation_id}.")
        except Exception as e:
            logger.error(f"Failed to drop partition {partition_name}. Error: {e}")
            raise
