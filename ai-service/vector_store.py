from pymilvus import connections, FieldSchema, CollectionSchema, DataType, Collection, utility
import logging
from config import MILVUS_HOST, MILVUS_PORT, COLLECTION_NAME
from typing import List
from schemas import ChunkData

logger = logging.getLogger(__name__)

def connect_milvus():
    try:
        connections.connect("default", host=MILVUS_HOST, port=MILVUS_PORT)
        logger.info(f"Connected to Milvus at {MILVUS_HOST}:{MILVUS_PORT}")
    except Exception as e:
        logger.error(f"Failed to connect to Milvus: {e}")
        raise

def get_or_create_collection(collection_name: str) -> Collection:
    if not utility.has_collection(collection_name):
        fields = [
            FieldSchema(name="chunk_id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="doc_id", dtype=DataType.VARCHAR, max_length=255),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="start_idx", dtype=DataType.INT64),
            FieldSchema(name="end_idx", dtype=DataType.INT64),
            FieldSchema(name="metadata", dtype=DataType.JSON),
            FieldSchema(name="is_deleted", dtype=DataType.BOOL, default_value=False),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=768)
        ]
        schema = CollectionSchema(fields, description="Documents chunk collection")
        collection = Collection(collection_name, schema)
        
        index_params = {
            "metric_type": "COSINE", 
            "index_type": "HNSW", 
            "params": {"M": 8, "efConstruction": 64}
        }
        collection.create_index(field_name="vector", index_params=index_params)
        logger.info(f"Created Milvus collection and index: {collection_name}")
        return collection
    else:
        return Collection(collection_name)

def ensure_partition(collection: Collection, partition_name: str):
    if not collection.has_partition(partition_name):
        collection.create_partition(partition_name)
        logger.info(f"Created partition {partition_name}")

def document_exists(collection: Collection, partition_name: str, doc_id: str) -> bool:
    collection.load()
    expr = f"doc_id == '{doc_id}'"
    results = collection.query(expr=expr, partition_names=[partition_name], limit=1)
    return len(results) > 0

def insert_chunks(collection: Collection, partition_name: str, chunks: List[ChunkData]):
    if not chunks:
        return
    
    doc_ids = [c.doc_id for c in chunks]
    contents = [c.content for c in chunks]
    start_idxs = [c.start_idx for c in chunks]
    end_idxs = [c.end_idx for c in chunks]
    metadatas = [c.metadata for c in chunks]
    is_deleteds = [False for _ in chunks]
    vectors = [c.vector for c in chunks]

    entities = [
        doc_ids,
        contents,
        start_idxs,
        end_idxs,
        metadatas,
        is_deleteds,
        vectors
    ]
    
    collection.insert(entities, partition_name=partition_name)
    collection.flush()
    logger.info(f"Inserted {len(chunks)} chunks into Milvus partition {partition_name}")

def delete_document_chunks(collection: Collection, partition_name: str, doc_id: str):
    try:
        expr = f"doc_id == '{doc_id}'"
        collection.delete(expr=expr, partition_name=partition_name)
        collection.flush()
        logger.info(f"Deleted all chunks for doc_id {doc_id} in partition {partition_name}")
    except Exception as e:
        logger.error(f"Failed to delete chunks for doc_id {doc_id}: {e}")

def drop_partition_if_exists(collection: Collection, partition_name: str):
    try:
        if not collection.has_partition(partition_name):
            logger.info(f"Partition {partition_name} does not exist. Nothing to drop.")
            return
        # Milvus requires release before drop; re-load afterwards to keep search working
        collection.release()
        collection.drop_partition(partition_name)
        collection.load()
        logger.info(f"Dropped partition {partition_name} and reloaded collection")
    except Exception as e:
        logger.error(f"Failed to drop partition {partition_name}: {e}")
        try:
            collection.load()
        except Exception:
            logger.error(f"Failed to reload collection after drop_partition error")
        raise e
