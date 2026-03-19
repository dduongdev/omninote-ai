import os
import re
import asyncio
import requests
import numpy as np
from typing import List, Optional, Dict, Any, Tuple
from contextlib import asynccontextmanager

# Local AI models
from sentence_transformers import SentenceTransformer

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

# --- 3. HELPER: Embedding & Reranking LOCAL (cùng model với document_service) ---

GEMMA_MODEL = "aisingapore/Gemma-SEA-LION-v3-9B-IT:featherless-ai"
HF_CHAT_URL = "https://router.huggingface.co/v1/chat/completions"

def hf_generate(system_prompt: str, user_message: str) -> str:
    """Gọi Gemma LLM qua HF Router (OpenAI-compatible format)"""
    headers = {
        "Authorization": f"Bearer {HF_TOKEN}",
    }
    # Gemma không hỗ trợ system role → gộp vào user message
    combined = f"{system_prompt}\n\n{user_message}"
    payload = {
        "model": GEMMA_MODEL,
        "messages": [
            {"role": "user", "content": combined},
        ],
        "max_tokens": 1024,
        "temperature": 0.1,
    }
    resp = requests.post(HF_CHAT_URL, headers=headers, json=payload, timeout=120)
    if resp.status_code != 200:
        print(f"[Gemma API] Status: {resp.status_code}, Body: {resp.text[:500]}")
    resp.raise_for_status()
    result = resp.json()
    # OpenAI format: {"choices": [{"message": {"content": "..."}}]}
    return result["choices"][0]["message"]["content"].strip()

def local_embed(texts: List[str]) -> List[List[float]]:
    """Dùng SentenceTransformer local - cùng model với document_service.py"""
    embedder = AI_MODELS.get("embedder")
    if not embedder:
        raise RuntimeError("Embedding model chưa sẵn sàng")
    embeddings = embedder.encode(texts, normalize_embeddings=True)
    return embeddings.tolist()

def local_rerank(query: str, candidates: List[str]) -> List[float]:
    """Reranking bằng cosine similarity với embedding model local"""
    embedder = AI_MODELS.get("embedder")
    if not embedder:
        raise RuntimeError("Embedding model chưa sẵn sàng")
    # Encode query và candidates
    query_vec = embedder.encode([query], normalize_embeddings=True)
    cand_vecs = embedder.encode(candidates, normalize_embeddings=True)
    # Cosine similarity (đã normalize nên chỉ cần dot product)
    scores = np.dot(cand_vecs, query_vec.T).flatten().tolist()
    return scores

# --- 4. POST-PROCESSING: Đánh số lại citation tuần tự, không lặp ---

def renumber_citations(text: str, top_chunks: List[Dict]) -> Tuple[str, List]:
    """
    Thay thế MỌI [N] trong text bằng số tăng dần [1],[2],[3],...
    - Không phụ thuộc vào model có đánh số đúng không
    - Mỗi lần xuất hiện [N] đều được gán số mới, kể cả cùng N
    - Trả về (text_mới, danh_sách_citations_tương_ứng)
    """
    num_chunks = len(top_chunks)
    counter = [1]
    citation_list = []

    def replace_each(match: re.Match) -> str:
        original_idx = int(match.group(1))
        # Map về chunk hợp lệ (0-based từ model → clamp vào range)
        chunk_idx = min(max(original_idx, 0), num_chunks - 1)
        chunk = top_chunks[chunk_idx]
        citation_list.append(Citation(
            documentId=int(chunk["doc_id"]),
            startIndex=int(chunk["start_idx"]),
            endIndex=int(chunk["end_idx"]),
            fileName=(chunk["content"] or "")[:150] + "..."
        ))
        new_num = counter[0]
        counter[0] += 1
        return f"[{new_num}]"

    new_text = re.sub(r'\[(\d+)\]', replace_each, text)
    return new_text, citation_list

# --- 5. KHỞI TẠO SERVICE TRONG BACKGROUND ---

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

        # B. Load Embedding model local (cùng model với document_service.py)
        print("[BG] Đang load embedding model keepitreal/vietnamese-sbert ...")
        AI_MODELS["embedder"] = SentenceTransformer('keepitreal/vietnamese-sbert')
        print("[BG] Embedding model sẵn sàng.")

        # C. Kiểm tra kết nối Gemma LLM qua HF Inference API
        print(f"[BG] Gemma LLM: {GEMMA_MODEL} via {HF_CHAT_URL}")
        AI_MODELS["llm_ready"] = True  # Gemma gọi qua requests, không cần load
        print("[BG] Gemma endpoint sẵn sàng.")

        # D. Preprocessor (nhẹ, không cần GPU)
        AI_MODELS["preprocessor"] = PhobertPreprocessingChain()
        print("[BG] Preprocessor sẵn sàng.")

        IS_READY = True
        print("--- [BG] AI Service sẵn sàng! (Embedding local + Gemma HF API) ---")

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

SYSTEM_PROMPT = """Bạn là một trợ lý AI chuyên phân tích tài liệu dựa trên ngữ cảnh được cung cấp.

======================
 NHIỆM VỤ
======================
- Trả lời câu hỏi của người dùng CHỈ dựa trên NGỮ CẢNH.
- Không sử dụng bất kỳ kiến thức bên ngoài nào.

======================
QUY TẮC BẮT BUỘC
======================

1. GIỚI HẠN NGUỒN:
- Chỉ sử dụng thông tin có trong NGỮ CẢNH.
- Nếu không tìm thấy thông tin liên quan, trả lời chính xác:
  "Tôi không tìm thấy thông tin này trong tài liệu."

2. TRÍCH DẪN (CỰC KỲ QUAN TRỌNG):
Citation là SỐ THỨ TỰ XUẤT HIỆN, KHÔNG phải ID tài liệu.

Đánh số PHẢI:
- Bắt đầu từ [1]
- Tăng dần liên tục: [1], [2], [3], [4], ...
- KHÔNG được bỏ số (không nhảy từ [1] → [3])
- KHÔNG được lặp lại số (mỗi số chỉ dùng 1 lần duy nhất)

Nếu một câu dùng nhiều nguồn:
- PHẢI viết: [1][2]
- KHÔNG được viết: [1, 2] hoặc [1 2]

TUYỆT ĐỐI CẤM:
- Dùng lại số cũ → sai
- Gộp dạng [1, 2] → sai
- Bỏ qua số → sai

======================
VÍ DỤ ĐÚNG
======================

Câu 1 → [1]  
Câu 2 → [2]  
Câu 3 (2 nguồn) → [3][4]  
Câu 4 → [5]

======================
VÍ DỤ SAI (KHÔNG ĐƯỢC LÀM)
======================

 Sai: [1, 2]  
 Sai: [1] ... [1]  
 Sai: [1] → [3] (thiếu [2])  

3. KHÔNG SUY DIỄN:
- Không tự suy luận vượt quá dữ liệu.
- Không thêm thông tin không có trong context.

4. LÀM SẠCH DỮ LIỆU:
- Loại bỏ các ký tự đặc biệt như: \\n, \\t, \\r
- Viết lại câu trả lời rõ ràng, mạch lạc.

5. ƯU TIÊN ĐA NGUỒN:
- Nếu nhiều nguồn cùng chứa thông tin → cố gắng sử dụng nhiều nguồn khác nhau.
- Tránh chỉ dùng 1 nguồn nếu có thể dùng 2+ nguồn.
- Nếu 1 câu dùng nhiều nguồn → citation vẫn đánh số tiếp theo 

6. TRÁNH LẶP:
- Không lặp lại cùng một ý từ nhiều nguồn nếu không cần thiết.

======================
 FORMAT OUTPUT
======================

- Trả lời dạng đoạn văn rõ ràng.
- Không giải thích về quy tắc.
- Không thêm phần "nguồn" riêng.
- Citation phải nằm ngay trong câu."""

def build_user_message(context: str, question: str) -> str:
    """Tạo user message chứa context + câu hỏi"""
    return f"""======================
 INPUT
======================
NGỮ CẢNH:
{context}

CÂU HỎI:
{question}

======================
 OUTPUT
======================
Câu trả lời:"""

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

        # Bước 2: Embedding câu hỏi (local - cùng model với document_service)
        query_vector = await asyncio.to_thread(local_embed, [processed_query])
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
            print(f"[SEARCH] Không tìm thấy chunk nào cho query: '{request.contentQuery[:80]}...'")
            return MessageResponse(
                contentAnswer="Không tìm thấy dữ liệu liên quan trong tài liệu của bạn.",
                citationsResponses=[]
            )

        print(f"\n{'='*60}")
        print(f"[SEARCH] Query: '{request.contentQuery[:100]}'")
        print(f"[SEARCH] Tìm thấy {len(hits)} chunks từ Milvus:")
        for i, h in enumerate(hits):
            content_preview = str(h.entity.get('content') or '')[:80]
            print(f"  [{i}] doc_id={h.entity.get('doc_id')} | "
                  f"score={h.score:.4f} | "
                  f"idx=[{h.entity.get('start_idx')},{h.entity.get('end_idx')}] | "
                  f"content='{content_preview}...'") 

        # Bước 4: Reranking (local cosine similarity)
        candidates = [{
            "doc_id": h.entity.get("doc_id"),
            "content": h.entity.get("content"),
            "start_idx": h.entity.get("start_idx"),
            "end_idx": h.entity.get("end_idx")
        } for h in hits]

        candidate_texts = [c["content"] for c in candidates]
        scores = await asyncio.to_thread(local_rerank, request.contentQuery, candidate_texts)
        for i, s in enumerate(scores):
            candidates[i]["rerank_score"] = float(s)

        sorted_candidates = sorted(candidates, key=lambda x: x["rerank_score"], reverse=True)

        print(f"\n[RERANK] Kết quả sau reranking (top {len(sorted_candidates)}):")
        for i, c in enumerate(sorted_candidates):
            marker = "✓ TOP" if i < 3 else "   "
            print(f"  {marker}[{i}] doc_id={c['doc_id']} | "
                  f"rerank_score={c['rerank_score']:.4f} | "
                  f"idx=[{c['start_idx']},{c['end_idx']}] | "
                  f"content='{str(c['content'])[:80]}...'")

        top_chunks = sorted_candidates[:3]
        print(f"\n[CONTEXT] Sử dụng {len(top_chunks)} chunks cho LLM:")
        for i, chunk in enumerate(top_chunks):
            print(f"  [{i}] doc_id={chunk['doc_id']} | score={chunk['rerank_score']:.4f} | "
                  f"content='{str(chunk['content'])[:100]}...'")
        print(f"{'='*60}\n")

        # Bước 5: Chuẩn bị context (0-based cho model)
        context_str = ""
        for i, chunk in enumerate(top_chunks):
            context_str += f" Tài liệu [{i}]: {chunk['content']}\n\n"

        # Bước 6: Gọi Gemma qua HF Router (OpenAI-compatible)
        user_msg = build_user_message(context_str, request.contentQuery)
        raw_answer = await asyncio.to_thread(hf_generate, SYSTEM_PROMPT, user_msg)

        # Bước 7: Post-process — đánh số citation tăng dần, KHÔNG lặp
        final_answer, final_citations = renumber_citations(raw_answer, top_chunks)
        print(f"[CITATION] raw  : {raw_answer[:200]}...")
        print(f"[CITATION] fixed: {final_answer[:200]}...")

        return MessageResponse(contentAnswer=final_answer, citationsResponses=final_citations)

    except Exception as e:
        print(f"LỖI HỆ THỐNG AI: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi xử lý: {str(e)}")


# --- 8. RUN ---

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080, access_log=False)
