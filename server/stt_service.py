import os
import logging
from http import HTTPStatus

import dashscope
from dashscope.audio.asr import Recognition

logger = logging.getLogger(__name__)


class STTService:
    """
    使用阿里云 DashScope Paraformer 云端 API 进行语音识别。
    替代本地 Whisper 模型，减轻 CPU 负担，同时利用免费额度。
    """

    def __init__(self, api_key: str, model: str = "paraformer-realtime-v2", language: str = "zh"):
        logger.info(f"Initializing STT Service (DashScope Paraformer) with model='{model}'...")

        # NOTE: DashScope SDK 使用全局 api_key 或环境变量 DASHSCOPE_API_KEY
        dashscope.api_key = api_key
        self.model = model
        self.language = language
        self._available = True

        logger.info("STT Service (DashScope Paraformer) initialized successfully.")

    def transcribe(self, file_path: str) -> str:
        """
        将本地音频文件转写为文字。
        使用 Paraformer 非流式调用，直接传入本地文件路径，同步返回结果。
        """
        if not self._available:
            raise RuntimeError("STT Service not available.")

        if not os.path.exists(file_path):
            raise FileNotFoundError(f"Audio file not found: {file_path}")

        try:
            logger.info(f"Transcribing file via DashScope Paraformer: {file_path}")

            # 非流式调用：直接传入文件路径，同步阻塞返回识别结果
            recognition = Recognition(
                model=self.model,
                format=self._detect_format(file_path),
                sample_rate=16000,
                language_hints=[self.language, 'en'],
                callback=None,
            )

            result = recognition.call(file_path)

            if result.status_code == HTTPStatus.OK:
                sentences = result.get_sentence()
                # sentences 是一个列表，每个元素包含 'text' 字段
                full_text = ""
                if sentences:
                    for sentence in sentences:
                        if isinstance(sentence, dict) and 'text' in sentence:
                            full_text += sentence['text']
                        elif isinstance(sentence, str):
                            full_text += sentence

                logger.info(f"Transcription result: {full_text}")
                return full_text
            else:
                error_msg = f"DashScope STT error: status={result.status_code}, message={result.message}"
                logger.error(error_msg)
                raise RuntimeError(error_msg)

        except Exception as e:
            logger.error(f"Transcription error: {e}")
            raise e

    @staticmethod
    def _detect_format(file_path: str) -> str:
        """根据文件扩展名推断音频格式"""
        ext = os.path.splitext(file_path)[1].lower()
        format_map = {
            '.wav': 'wav',
            '.mp3': 'mp3',
            '.pcm': 'pcm',
            '.ogg': 'ogg',
            '.flac': 'flac',
            '.aac': 'aac',
            '.m4a': 'mp4',
            '.opus': 'opus',
        }
        return format_map.get(ext, 'wav')
