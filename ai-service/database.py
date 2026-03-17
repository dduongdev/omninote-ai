from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from models import Base
from config import POSTGRES_URI
import logging

logger = logging.getLogger(__name__)

engine = create_engine(POSTGRES_URI)

# Initialize schema
try:
    Base.metadata.create_all(engine)
    logger.info("Database schema initialized.")
except Exception as e:
    logger.error(f"Error initializing DB schema: {e}")

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
