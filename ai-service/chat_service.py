import os
import asyncio
import requests
from typing import List, Optional, Dict, Any
from contextlib import asynccontextmanager

# Hugging Face
from langchain_huggingface import HuggingFaceEndpoint
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

# FastAPI & Tools
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from pymilvus import Collection, connections

# Các module nội bộ
from config import MILVUS_HOST, MILVUS_PORT, COLLECTION_NAME, HF_TOKEN
from preprocessor import PhobertPreprocessingChain
from consul_registration import register_with_consul, deregister_from_consul, AI_SERVICE_ID, AI_SERVICE_PORT

# --- 1. ĐỊNH NGHĨA DỮ LIỆU (SCHEMAS) ---

class MessageCreateRequest(BaseModel):
    conversationId: int          # khớp với Java
    contentQuery: str
    documentIds: List[int]       # khớp với Java (List<Long>)

class Citation(BaseModel):
    documentId: int              # khớp với Java CitationResponse (Long)
    startIndex: int
    endIndex: int
    fileName: Optional[str] = None

class MessageResponse(BaseModel):
    contentAnswer: str           # khớp với Java MessageResponse
    citationsResponses: List[Citation]

# --- 2. TRẠNG THÁI SERVICE ---

AI_MODELS: Dict[str, Any] = {}
IS_READY = False   # True khi service đã sẵn sàng

# --- 3. HELPER: Gọi HF Inference API (không download model) ---

HF_API_BASE = "https://router.huggingface.co"  # URL mới (api-inference.huggingface.co đã deprecated)

def hf_embed(texts: List[str]) -> List[List[float]]:
    """Gọi HF Inference API để lấy embeddings - không download model local"""
    headers = {"Authorization": f"Bearer {HF_TOKEN}"}
    url = f"{HF_API_BASE}/models/keepitreal/vietnamese-sbert"
    payload = {"inputs": texts}
    response = requests.post(url, headers=headers, json=payload, timeout=60)
    response.raise_for_status()
    result = response.json()
    # result có thể là list of list hoặc list of float (nếu 1 text)
    if result and isinstance(result[0], float):
        return [result]
    return [list(r) for r in result]

def hf_rerank(query: str, candidates: List[str]) -> List[float]:
    """Gọi HF Inference API để tính điểm similarity cho reranking"""
    headers = {"Authorization": f"Bearer {HF_TOKEN}"}
    payload = {
        "inputs": {
            "source_sentence": query,
            "sentences": candidates
        }
    }
    url = f"{HF_API_BASE}/models/BAAI/bge-reranker-v2-m3"
    response = requests.post(url, headers=headers, json=payload, timeout=60)
    response.raise_for_status()
    scores = response.json()
    # Trả về list[float]
    if isinstance(scores, list):
        return [float(s) for s in scores]
    return [0.0] * len(candidates)

# --- 4. KHỞI TẠO SERVICE TRONG BACKGROUND ---

async def load_models_background():
    global IS_READY
    try:
        print("--- [BG] Khởi tạo OmniNote AI Service (HF Inference API) ---")

        if not HF_TOKEN:
            raise ValueError("Không có HF_TOKEN! Không thể dùng HF Inference API.")

        # A. Kết nối Milvus
        try:
            connections.connect("default", host=MILVUS_HOST, port=int(MILVUS_PORT))
            print(f"[BG] Đã kết nối Milvus tại {MILVUS_HOST}:{MILVUS_PORT}")
        except Exception as e:
            print(f"[BG] Lỗi kết nối Milvus: {e}")

        # B. Khởi tạo Gemma LLM qua HF Inference API (không download)
        print("[BG] Kết nối Gemma LLM qua HuggingFace Inference API...")
        AI_MODELS["llm"] = HuggingFaceEndpoint(
            repo_id="google/gemma-2-9b-it",
            huggingfacehub_api_token=HF_TOKEN,
            endpoint_url=f"{HF_API_BASE}/models/google/gemma-2-9b-it",
            temperature=0.1,
            max_new_tokens=1024,
        )
        print("[BG] Gemma sẵn sàng.")

        # C. Preprocessor (nhẹ, không cần GPU)
        AI_MODELS["preprocessor"] = PhobertPreprocessingChain()
        print("[BG] Preprocessor sẵn sàng.")

        IS_READY = True
        print("--- [BG] AI Service sẵn sàng! (Embedding & Reranker dùng HF API) ---")

    except Exception as e:
        print(f"[BG] LỖI khởi tạo: {e}")
        import traceback
        traceback.print_exc()

# --- 5. LIFESPAN: Register Consul TRƯỚC, khởi tạo SAU ---

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("--- Khởi động OmniNote AI Service ---")

    # 1. Đăng ký Consul NGAY (trước khi load models)
    try:
        register_with_consul()
    except Exception as e:
        print(f"Consul registration failed (non-fatal): {e}")

    # 2. Khởi tạo trong background (không block server)
    asyncio.create_task(load_models_background())

    print("--- Server đã sẵn sàng nhận requests (đang khởi tạo background) ---")
    yield

    # Shutdown
    print("--- Đang tắt service ---")
    AI_MODELS.clear()
    try:
        connections.disconnect("default")
    except Exception:
        pass
    try:
        deregister_from_consul()
    except Exception as e:
        print(f"Consul deregistration failed: {e}")

app = FastAPI(title="OmniNote AI Service", lifespan=lifespan)

INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY")

# --- 6. CÁC HÀM BỔ TRỢ ---

def build_filter_expr(enabled_docs: List[str]) -> Optional[str]:
    if not enabled_docs:
        return None
    docs_list = ",".join([f"'{d}'" for d in enabled_docs])
    return f"doc_id in [{docs_list}]"

def get_gemma_rag_prompt():
    template = """
    <start_of_turn>user
    Bạn là một trợ lý ảo phân tích tài liệu chuyên nghiệp. Hãy trả lời câu hỏi dựa trên NGỮ CẢNH dưới đây.

    NGỮ CẢNH:
    {context}

    QUY TẮC:
    1. Chỉ dùng thông tin trong NGỮ CẢNH. Không dùng kiến thức bên ngoài.
    2. Nếu không thấy câu trả lời, hãy nói: "Tôi không tìm thấy thông tin này trong tài liệu."
    3. Trích dẫn: Sau mỗi ý trả lời, BẮT BUỘC thêm [0], [1] hoặc [2] tương ứng với tài liệu nguồn.
    <end_of_turn>
    <start_of_turn>user
    CÂU HỎI: {question}
    <end_of_turn>
    <start_of_turn>model
    """
    return ChatPromptTemplate.from_template(template)

# --- 7. API ENDPOINTS ---

@app.get("/health")
async def health():
    """Health check - luôn trả 200 để Consul không deregister"""
    return {
        "status": "ok" if IS_READY else "loading",
        "models_ready": IS_READY,
        "service_id": AI_SERVICE_ID,
        "port": AI_SERVICE_PORT
    }

@app.post("/api/v1/ai/chat", response_model=MessageResponse)
async def generate_ai_response(
    request: MessageCreateRequest,
    x_api_key: Optional[str] = Header(None, alias="X-Internal-API-Key")
):
    # Kiểm tra API key
    if x_api_key != INTERNAL_API_KEY:
        raise HTTPException(status_code=403, detail="Invalid API Key")

    # Kiểm tra service đã sẵn sàng chưa
    if not IS_READY:
        raise HTTPException(
            status_code=503,
            detail="AI service đang khởi tạo, vui lòng thử lại sau vài giây."
        )

    try:
        # Bước 1: Tiền xử lý tiếng Việt
        processed_query = AI_MODELS["preprocessor"].process(request.contentQuery)

        # Bước 2: Embedding câu hỏi qua HF Inference API
        query_vector = await asyncio.to_thread(hf_embed, [processed_query])
        query_vector = query_vector[0]

        # Bước 3: Truy vấn Milvus
        collection = Collection(COLLECTION_NAME)
        enabled_docs_str = [str(d) for d in request.documentIds]
        filter_expr = build_filter_expr(enabled_docs_str)
        partition_name = f"part_{request.conversationId}"

        search_results = collection.search(
            data=[query_vector],
            anns_field="vector",
            param={"metric_type": "COSINE", "params": {"ef": 64}},
            limit=15,
            expr=filter_expr,
            partition_names=[partition_name],
            output_fields=["doc_id", "content", "start_idx", "end_idx"]
        )

        hits = search_results[0] if search_results else []
        if not hits:
            return MessageResponse(
                contentAnswer="Không tìm thấy dữ liệu liên quan trong tài liệu của bạn.",
                citationsResponses=[]
            )

        # Bước 4: Reranking qua HF Inference API
        candidates = [{
            "doc_id": h.entity.get("doc_id"),
            "content": h.entity.get("content"),
            "start_idx": h.entity.get("start_idx"),
            "end_idx": h.entity.get("end_idx")
        } for h in hits]

        candidate_texts = [c["content"] for c in candidates]
        scores = await asyncio.to_thread(hf_rerank, request.contentQuery, candidate_texts)
        for i, s in enumerate(scores):
            candidates[i]["rerank_score"] = float(s)

        top_chunks = sorted(candidates, key=lambda x: x["rerank_score"], reverse=True)[:3]

        # Bước 5: Chuẩn bị context
        context_str = ""
        final_citations = []
        for i, chunk in enumerate(top_chunks):
            context_str += f" Tài liệu [{i}]: {chunk['content']}\n\n"
            final_citations.append(Citation(
                documentId=int(chunk["doc_id"]),
                startIndex=int(chunk["start_idx"]),
                endIndex=int(chunk["end_idx"]),
                fileName=chunk["content"][:150] + "..."
            ))

        # Bước 6: Chạy LangChain + Gemma qua HF Inference API
        prompt = get_gemma_rag_prompt()
        chain = prompt | AI_MODELS["llm"] | StrOutputParser()
        final_answer = await chain.ainvoke({
            "context": context_str,
            "question": request.contentQuery
        })

        return MessageResponse(contentAnswer=final_answer, citationsResponses=final_citations)

    except Exception as e:
        print(f"LỖI HỆ THỐNG AI: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi xử lý: {str(e)}")


# --- 8. RUN ---

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
