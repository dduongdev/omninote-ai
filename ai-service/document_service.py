import json
import logging
import time
from minio_client import get_object, upload_text, check_exists
from extractor import ExtractorFactory
from preprocessor import PhobertPreprocessingChain
from chunking import ContentChunker
from sentence_transformers import SentenceTransformer
from vector_store import connect_milvus, get_or_create_collection, ensure_partition, document_exists, insert_chunks
from database import SessionLocal
from models import OutboxEvent
from config import COLLECTION_NAME

logger = logging.getLogger(__name__)

# Initialize Milvus
connect_milvus()
collection = get_or_create_collection(COLLECTION_NAME)

# Initialize Embedder (Shared across invocations)
embedder = SentenceTransformer('keepitreal/vietnamese-sbert')

class DocumentProcessingService:
    def process_document(self, conversation_id: int, doc_id: str, object_name: str, max_retries: int = 3):
        partition_name = f"part_{conversation_id}"

        for attempt in range(1, max_retries + 1):
            db = SessionLocal()
            try:
                # 1. Extract
                extracted_object_name = f"{object_name}_extracted"
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

                # 2. Idempotency Check in Milvus
                ensure_partition(collection, partition_name)
                
                if document_exists(collection, partition_name, str(doc_id)):
                    logger.info(f"Document {doc_id} already exists in Milvus. Skipping embedding.")
                else:
                    logger.info(f"Document {doc_id} not found in Milvus. Proceeding with embedding...")
                    
                    # 3. Chunking (Trực tiếp từ raw text để giữ đúng start_idx, end_idx)
                    chunker = ContentChunker(chunk_size=500, overlap=50)
                    metadata = {"source_name": object_name, "category": "document"}
                    chunks = chunker.chunk(text, str(doc_id), metadata)
                    
                    if chunks:
                        # 4. Preprocessing từng chunk riêng biệt
                        preprocessor = PhobertPreprocessingChain()
                        logger.info(f"Preprocessing and Embedding {len(chunks)} chunks...")
                        
                        # Dùng text đã preprocess để embed (có word segment của PhoBERT)
                        preprocessed_texts = [preprocessor.process(c.content) for c in chunks]
                        
                        # 5. Embedding
                        embeddings = embedder.encode(preprocessed_texts).tolist()

                        for i, chunk in enumerate(chunks):
                            chunk.vector = embeddings[i]
                            # Lưu ý: chunk.content lúc này vẫn giữ nguyên văn bản gốc từ file
                            # để UI có thể map chính xác (tùy chọn: có thể gán lại bằng clean_text nếu cần)

                        # 6. Insert into Milvus
                        insert_chunks(collection, partition_name, chunks)

                # 7. Local Outbox insertion (Thành công - không cần explicit transaction rollback loop)
                logger.info("Saving document.ingested OutboxEvent to DB...")
                outbox_payload = {
                    "doc_id": doc_id,
                    "object_name": object_name
                }
                event = OutboxEvent(
                    aggregate_type="Document",
                    aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                    event_type="document.ingested",
                    payload=json.dumps(outbox_payload)
                )
                db.add(event)
                db.commit()
                logger.info("Document processing successfully finished.")
                
                # Trả về thành công, vòng lặp dừng lại. Consumer sẽ tự ACK.
                return 

            except Exception as e:
                db.rollback() # Trả lại session DB sạch cho vòng lặp hoặc lưu event thất bại
                logger.warning(f"Attempt {attempt}/{max_retries} failed for doc {doc_id}. Error: {e}")
                
                if attempt == max_retries:
                    logger.error(f"All {max_retries} attempts failed. Saving document.ingest_failed event.")
                    try:
                        failed_payload = {
                            "conversation_id": conversation_id,
                            "doc_id": doc_id,
                            "error": str(e)
                        }
                        failed_event = OutboxEvent(
                            aggregate_type="Document",
                            aggregate_id=int(doc_id) if str(doc_id).isdigit() else 0,
                            event_type="document.ingest_failed",
                            payload=json.dumps(failed_payload)
                        )
                        db.add(failed_event)
                        db.commit()
                        logger.info("Failed event saved. Dropping current MQ event.")
                    except Exception as fatal_e:
                        logger.critical(f"FATAL: Could not even save failed OutboxEvent: {fatal_e}")
                        # Nếu chính DB để outbox sập, buột lòng raise để trigger `requeue=True` ở consumer
                        raise fatal_e
                    
                    # Return thay vì Raise Exception để Consumer ở vòng ngoài sẽ thực hiện .basic_ack() loại bỏ message khỏi hàng đợi.
                    return 
                
                # Đợi một chút trước khi Re-try trong những lần đầu
                time.sleep(2)
            finally:
                db.close()
