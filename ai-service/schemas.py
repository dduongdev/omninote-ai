from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List

class ChunkData(BaseModel):
    chunk_id: Optional[int] = None
    vector: Optional[List[float]] = None
    doc_id: str
    content: str
    start_idx: int
    end_idx: int
    metadata: Dict[str, Any] = Field(default_factory=dict)
