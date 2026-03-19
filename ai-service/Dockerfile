# syntax=docker/dockerfile:1.6
FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TOKENIZERS_PARALLELISM=false

# ===== Layer 1: OS deps (ít thay đổi → cache lâu) =====
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 libgomp1 build-essential curl \
    && rm -rf /var/lib/apt/lists/*

# ===== Layer 2: Upgrade pip =====
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip

# ===== Layer 3: PyTorch CPU (~800MB, rất ít thay đổi → cache riêng) =====
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install torch==2.3.1 \
        --index-url https://download.pytorch.org/whl/cpu

# ===== Layer 4: PaddlePaddle (~200MB, ít thay đổi → cache riêng) =====
# ===== Layer 4: PaddlePaddle =====
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install paddlepaddle==2.6.2 \
        -f https://www.paddlepaddle.org.cn/whl/linux/cpu-mkl-avx/stable.html

# ===== Layer 5: Còn lại (thay đổi khi sửa requirements.txt) =====
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --prefer-binary -r requirements.txt

# ===== Layer 6: Source code (thay đổi thường xuyên nhất → cuối cùng) =====
COPY . .

EXPOSE 8080

CMD ["sh", "-c", "python consumer.py & uvicorn chat_service:app --host 0.0.0.0 --port 8080 --no-access-log"]