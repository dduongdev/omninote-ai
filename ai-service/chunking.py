from typing import List
from schemas import ChunkData
import logging
from langchain.text_splitter import RecursiveCharacterTextSplitter

logger = logging.getLogger(__name__)

class ContentChunker:
    def __init__(self, chunk_size: int = 250, overlap: int = 20):
        # We now count chunk size by characters, not words.
        # We scale chunk size by ~5 chars per word so chunks remain similar size.
        self.chunk_size = chunk_size * 5
        self.overlap = overlap * 5
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=self.chunk_size,
            chunk_overlap=self.overlap,
            add_start_index=True
        )

    def chunk(self, text: str, doc_id: str, metadata: dict = None) -> List[ChunkData]:
        if metadata is None:
            metadata = {}
            
        chunks = []
        docs = self.splitter.create_documents([text])
        
        for doc in docs:
            chunk_content = doc.page_content
            start_idx = doc.metadata.get("start_index", 0)
            end_idx = start_idx + len(chunk_content)
            
            chunk_data = ChunkData(
                doc_id=str(doc_id),
                content=chunk_content,
                start_idx=start_idx,
                end_idx=end_idx,
                metadata=metadata.copy()
            )
            chunks.append(chunk_data)
            
        logger.info(f"Generated {len(chunks)} chunks for doc {doc_id}")
        return chunks
