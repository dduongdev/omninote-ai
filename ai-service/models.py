from sqlalchemy import Column, Integer, String, Boolean, DateTime, UniqueConstraint, BigInteger
from sqlalchemy.orm import declarative_base
from datetime import datetime

Base = declarative_base()

class OutboxEvent(Base):
    __tablename__ = 'outbox_events'

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    aggregate_type = Column(String(255), nullable=False)
    aggregate_id = Column(BigInteger, nullable=False)
    event_type = Column(String(255), nullable=False)
    payload = Column(String, nullable=True) # JSON payload
    is_published = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    updated_at = Column(DateTime, nullable=False, default=datetime.utcnow, onupdate=datetime.utcnow)

