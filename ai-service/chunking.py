from typing import List
from schemas import ChunkData
import logging

logger = logging.getLogger(__name__)

class ContentChunker:
    def __init__(self, chunk_size: int = 500, overlap: int = 50):
        self.chunk_size = chunk_size
        self.overlap = overlap

    def chunk(self, text: str, doc_id: str, metadata: dict = None) -> List[ChunkData]:
        if metadata is None:
            metadata = {}
            
        words = text.split()
        chunks = []
        current_char_idx = 0
        
        for i in range(0, len(words), self.chunk_size - self.overlap):
            chunk_words = words[i:i + self.chunk_size]
            if not chunk_words:
                break
                
            chunk_content = " ".join(chunk_words)
            
            # Note: This is an approximate character index since splitting by words removes original spacing. 
            # In a production environment, split via Character Text Splitter natively to track exact indices.
            start_idx = text.find(chunk_content, current_char_idx)
            if start_idx == -1:
                start_idx = current_char_idx # fallback
                
            end_idx = start_idx + len(chunk_content)
            
            chunk_data = ChunkData(
                doc_id=str(doc_id),
                content=chunk_content,
                start_idx=start_idx,
                end_idx=end_idx,
                metadata=metadata.copy()
            )
            chunks.append(chunk_data)
            
            current_char_idx = start_idx + len(" ".join(chunk_words[:self.chunk_size - self.overlap]))
            
        logger.info(f"Generated {len(chunks)} chunks for doc {doc_id}")
        return chunks
