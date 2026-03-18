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

    def _has_fonts(self, doc) -> bool:
        """Check if PDF has embedded fonts."""
        for page_num in range(len(doc)):
            page_fonts = doc.get_page_fonts(page_num)
            if page_fonts:
                return True
        return False

    def extract(self, filename: str, content: bytes) -> str:
        logger.info(f"Extracting text from {filename} using PDFExtractor")
        import tempfile
        import os
        import fitz

        with tempfile.NamedTemporaryFile(suffix='.pdf', delete=False) as temp_pdf:
            temp_pdf.write(content)
            temp_pdf_path = temp_pdf.name
            
        text_content = ""
        try:
            doc = fitz.open(temp_pdf_path)
            
            if self._has_fonts(doc):
                logger.info("PDF contains fonts. Using PyMuPDF for extraction...")
                pages_text = []
                for page in doc:
                    pages_text.append(page.get_text())
                text_content = "\n".join(pages_text).strip()
            
            doc.close()
            
            if not text_content:
                logger.info("PDF is empty or contains no fonts. Falling back to OCR (PaddleOCR)...")
                
                if self.ocr is None:
                    from paddleocr import PaddleOCR
                    self.ocr = PaddleOCR(use_angle_cls=True, lang='vi', show_log=False) 
                    
                result = self.ocr.ocr(temp_pdf_path, cls=True)

                extracted_text = []
                if result is not None:
                    for page in result:
                        if page is None:
                            continue
                        for line in page:
                            text = line[1][0]
                            extracted_text.append(text)

                text_content = "\n".join(extracted_text)

            return text_content
            
        except Exception as e:
            logger.error(f"Error extracting PDF: {e}")
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
