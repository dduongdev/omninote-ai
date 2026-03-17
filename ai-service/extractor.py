from abc import ABC, abstractmethod
import logging

logger = logging.getLogger(__name__)

class BaseExtractor(ABC):
    @abstractmethod
    def extract(self, filename: str, content: bytes) -> str:
        pass

class TextExtractor(BaseExtractor):
    def extract(self, filename: str, content: bytes) -> str:
        logger.info(f"Extracting text from {filename} using TextExtractor")
        return content.decode("utf-8")

class PDFExtractor(BaseExtractor):
    def __init__(self):
        super().__init__()
        self.ocr = None

    def extract(self, filename: str, content: bytes) -> str:
        logger.info(f"Extracting text from {filename} using PDFExtractor (PaddleOCR)")
        import tempfile
        import os
        
        if self.ocr is None:
            from paddleocr import PaddleOCR
            self.ocr = PaddleOCR(use_angle_cls=True, lang='vi', show_log=False)
            
        with tempfile.NamedTemporaryFile(suffix='.pdf', delete=False) as temp_pdf:
            temp_pdf.write(content)
            temp_pdf_path = temp_pdf.name
            
        try:
            result = self.ocr.ocr(temp_pdf_path, cls=True)
            
            extracted_text = []
            if result is not None:
                for page in result:
                    if page is None:
                        continue
                    for line in page:
                        text = line[1][0]
                        extracted_text.append(text)
            
            return "\n".join(extracted_text)
        except Exception as e:
            logger.error(f"Error extracting PDF with PaddleOCR: {e}")
            return ""
        finally:
            if os.path.exists(temp_pdf_path):
                os.remove(temp_pdf_path)

class MarkdownExtractor(BaseExtractor):
    def extract(self, filename: str, content: bytes) -> str:
        logger.info(f"Extracting text from {filename} using MarkdownExtractor")
        return content.decode("utf-8")

class ExtractorFactory:
    @staticmethod
    def get_extractor(filename: str) -> BaseExtractor:
        if filename.endswith(".txt"):
            return TextExtractor()
        elif filename.endswith(".pdf"):
            return PDFExtractor()
        elif filename.endswith(".md"):
            return MarkdownExtractor()
        else:
            logger.warning(f"Unsupported file extension for {filename}, defaulting to TextExtractor")
            return TextExtractor()
