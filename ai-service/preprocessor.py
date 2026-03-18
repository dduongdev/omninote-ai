import re
import logging
from abc import ABC, abstractmethod

logger = logging.getLogger(__name__)

class Preprocessor(ABC):
    def __init__(self, next_handler=None):
        self._next_handler = next_handler

    @abstractmethod
    def handle(self, text: str) -> str:
        if self._next_handler:
            return self._next_handler.handle(text)
        return text

class WhitespacePreprocessor(Preprocessor):
    def handle(self, text: str) -> str:
        logger.info("Applying WhitespacePreprocessor")
        # Thay thế nhiều khoảng trắng thành 1 khoảng trắng, xóa khoảng trắng ở đầu cuôi
        text = re.sub(r'\s+', ' ', text).strip()
        return super().handle(text)

class LowercasePreprocessor(Preprocessor):
    def handle(self, text: str) -> str:
        logger.info("Applying LowercasePreprocessor")
        text = text.lower()
        return super().handle(text)

class SpecialCharPreprocessor(Preprocessor):
    def handle(self, text: str) -> str:
        logger.info("Applying SpecialCharPreprocessor")
        # Remove non-alphanumeric except spaces, basic punctuation
        text = re.sub(r'[^\w\s\.,!\?-]', '', text)
        return super().handle(text)

class WordSegmentPreprocessor(Preprocessor):
    def handle(self, text: str) -> str:
        logger.info("Applying WordSegmentPreprocessor")
        try:
            from pyvi import ViTokenizer
            text = ViTokenizer.tokenize(text)
        except ImportError:
            logger.warning("pyvi không được cài đặt. Bỏ qua word segmentation.")
        return super().handle(text)

class PhobertPreprocessingChain:
    def __init__(self):
        self.chain = WhitespacePreprocessor(
            SpecialCharPreprocessor(
                LowercasePreprocessor(
                    WordSegmentPreprocessor()
                )
            )
        )

    def process(self, text: str) -> str:
        return self.chain.handle(text)
