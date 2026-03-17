from minio import Minio
from minio.error import S3Error
import logging
from config import MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET
import io

logger = logging.getLogger(__name__)

# Initialize MinIO
minio_client = Minio(
    MINIO_ENDPOINT,
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=False
)

def ensure_bucket_exists():
    try:
        if not minio_client.bucket_exists(MINIO_BUCKET):
            minio_client.make_bucket(MINIO_BUCKET)
    except Exception as e:
        logger.error(f"Error ensuring bucket exists: {e}")

def get_object(object_name: str) -> bytes:
    try:
        response = minio_client.get_object(MINIO_BUCKET, object_name)
        content = response.read()
        response.close()
        response.release_conn()
        return content
    except S3Error as e:
        logger.error(f"MinIO get_object error for {object_name}: {e}")
        raise

def upload_text(object_name: str, content: str):
    try:
        encoded_content = content.encode("utf-8")
        data = io.BytesIO(encoded_content)
        length = len(encoded_content)
        minio_client.put_object(MINIO_BUCKET, object_name, data, length, content_type="text/plain")
        logger.info(f"Uploaded text to MinIO: {object_name}")
    except S3Error as e:
        logger.error(f"MinIO put_object error for {object_name}: {e}")
        raise

def check_exists(object_name: str) -> bool:
    try:
        minio_client.stat_object(MINIO_BUCKET, object_name)
        return True
    except S3Error as e:
        if e.code == "NoSuchKey":
            return False
        raise
