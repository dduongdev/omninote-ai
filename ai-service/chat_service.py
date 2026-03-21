import os
import re
import asyncio
import requests
import numpy as np
import torch
from typing import List, Optional, Dict, Any, Tuple
from contextlib import asynccontextmanager

# Local AI models
from sentence_transformers import CrossEncoder
from FlagEmbedding import BGEM3FlagModel

# FastAPI & Tools
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from pymilvus import Collection, connections, AnnSearchRequest, RRFRanker

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

def local_embed(texts: List[str]) -> Tuple[List[List[float]], List[Dict[int, float]]]:
    """Dùng BGEM3FlagModel local - tạo cả dense và sparse vectors"""
    embedder = AI_MODELS.get("embedder")
    if not embedder:
        raise RuntimeError("Embedding model chưa sẵn sàng")
    
    outputs = embedder.encode(texts, return_dense=True, return_sparse=True, return_colbert_vecs=False)
    dense_vecs = outputs['dense_vecs'].tolist()
    
    # sparse vector dict: string -> float (token id -> weight). Milvus needs int -> float.
    sparse_vecs = [{int(k): float(v) for k, v in lw.items()} for lw in outputs['lexical_weights']]
    
    return dense_vecs, sparse_vecs

def local_rerank(query: str, candidates: List[str]) -> List[float]:
    """Reranking bằng CrossEncoder"""
    reranker = AI_MODELS.get("reranker")
    if not reranker:
        raise RuntimeError("Reranker model chưa sẵn sàng")
    
    pairs = [[query, cand] for cand in candidates]
    scores = reranker.predict(pairs)
    
    if isinstance(scores, np.ndarray):
        return scores.flatten().tolist()
    elif isinstance(scores, list):
        return [float(s) for s in scores]
    return [float(scores)]

# --- 4. POST-PROCESSING: Đánh số lại citation tuần tự, không lặp ---

def renumber_citations(text: str, top_chunks: List[Dict]) -> Tuple[str, List]:
    """
    LLM output dùng trực tiếp số hiệu [1], [2], [3] của context.
    Ta parse các số này và tạo mảng citation.
    Các câu trích dẫn cùng một [N] sẽ được map về cùng một index mới [K], 
    giúp đánh số liên tục từ 1 và không bị nhảy số.
    """
    num_chunks = len(top_chunks)
    citation_list = []
    seen_map = {}

    def replace_each(match: re.Match) -> str:
        original_idx = int(match.group(1))
        
        if original_idx not in seen_map:
            # Context 1-based → 0-based index vào top_chunks
            chunk_idx = min(max(original_idx - 1, 0), num_chunks - 1)
            chunk = top_chunks[chunk_idx]
            
            seen_map[original_idx] = len(citation_list) + 1
            citation_list.append(Citation(
                documentId=int(chunk["doc_id"]),
                startIndex=int(chunk["start_idx"]),
                endIndex=int(chunk["end_idx"]),
                fileName=None
            ))
            
        return f"[{seen_map[original_idx]}]"

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

        # B. Load Embedding & Reranker models local
        print("[BG] Đang load embedding model BAAI/bge-m3 ...")
        device = "cuda" if torch.cuda.is_available() else "cpu"
        use_fp16 = torch.cuda.is_available()
        
        AI_MODELS["embedder"] = BGEM3FlagModel('BAAI/bge-m3', use_fp16=use_fp16, device=device)
        print("[BG] Đang load reranker model BAAI/bge-reranker-v2-m3 ...")
        AI_MODELS["reranker"] = CrossEncoder('BAAI/bge-reranker-v2-m3', device=device)
        print(f"[BG] AI models sẵn sàng (Device: {device}).")

        # C. Kiểm tra kết nối Gemma LLM qua HF Inference API
        print(f"[BG] Gemma LLM: {GEMMA_MODEL} via {HF_CHAT_URL}")
        AI_MODELS["llm_ready"] = True  # Gemma gọi qua requests, không cần load
        print("[BG] Gemma endpoint sẵn sàng.")

        # D. Preprocessor (nhẹ, không cần GPU)
        AI_MODELS["preprocessor"] = PhobertPreprocessingChain()
        print("[BG] Preprocessor sẵn sàng.")

        IS_READY = True
        print("--- [BG] AI Service sẵn sàng! (Embedding local + Gemma HF API) ---")
        
        # Mở thread chạy consumer (dùng chung các models NLP để tiết kiệm RAM)
        try:
            from consumer import main as start_consumer
            import threading
            print("[BG] Đang start RabbitMQ Consumer Thread...")
            threading.Thread(target=start_consumer, daemon=True).start()
        except Exception as consumer_err:
            print(f"[BG] Error starting consumer thread: {consumer_err}")

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

SYSTEM_PROMPT = """
Bạn là một chuyên gia phân tích tài liệu. Nhiệm vụ của bạn là cung cấp câu trả lời chính xác, khách quan dựa trên NGỮ CẢNH (CONTEXT) được cung cấp.

======================
1. NGUYÊN TẮC CỐT LÕI
======================
- TRUNG THÀNH VỚI NGỮ CẢNH: Chỉ trả lời dựa trên thông tin có trong NGỮ CẢNH. Tuyệt đối không dùng kiến thức bên ngoài hoặc tự suy luận.
- XỬ LÝ KHI THIẾU TIN: Nếu không tìm thấy thông tin liên quan trong tài liệu, trả lời chính xác: "Tôi không tìm thấy thông tin này trong tài liệu." và không giải thích gì thêm.
- LÀM SẠCH DỮ LIỆU: Loại bỏ các ký tự đặc biệt như: \n, \t, \r. Viết lại câu trả lời rõ ràng, mạch lạc và tự nhiên.

======================
2. QUY TẮC TRÍCH DẪN (CITATION)
======================
- GIỮ NGUYÊN SỐ THỨ TỰ TÀI LIỆU: Chỉ sử dụng đúng số hiệu Tài liệu [i] từ NGỮ CẢNH.
- Nếu lấy ý từ "Tài liệu [1]", hãy viết [1]. Có thể lặp lại [1] nhiều lần nếu nhiều đoạn/câu cùng dùng dữ liệu đó.
- Nếu một ý kết hợp từ nhiều tài liệu, viết sát nhau: [1][2].
- KHÔNG tự ý chế ra hoặc đảo lộn các số không có trong ngữ cảnh.

======================
3. VÍ DỤ MINH HỌA
======================
- ĐÚNG: "Dự án bao gồm ba giai đoạn chính [1] với tổng ngân sách dự kiến là 5 tỷ đồng [2]. Theo thông tin ở tài liệu [1], quy trình này sẽ diễn ra song song [1][3]."
- SAI (Định dạng): "Thông tin [1, 2]." -> Phải là [1][2].
- SAI (Chế số): "Ý hai [4]." (khi ngữ cảnh chỉ cung cấp đến [3]).

======================
4. ĐỊNH DẠNG ĐẦU RA (FORMAT OUTPUT)
======================
- Trả lời dưới dạng đoạn văn rõ ràng.
- Không giải thích về các quy tắc này trong câu trả lời.
- Không thêm danh sách "Nguồn" hoặc "Tài liệu tham khảo" ở cuối bài.
- Citation phải nằm ngay trong dòng văn bản, tại cuối ý được trích dẫn.
- Nếu không có thông tin liên quan, chỉ trả lời: "Tôi không tìm thấy thông tin này trong tài liệu." và không thêm gì khác.
"""

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

        # Bước 2: Embedding câu hỏi (Hybrid: Dense + Sparse)
        dense_vecs, sparse_vecs = await asyncio.to_thread(local_embed, [processed_query])
        query_dense = dense_vecs[0]
        query_sparse = sparse_vecs[0]

        # Bước 3: Truy vấn Milvus (Top 10 Small Chunks bằng Hybrid Search)
        collection = Collection(COLLECTION_NAME)
        collection.load() # Yêu cầu load trước khi hybrid search
        
        enabled_docs_str = [str(d) for d in request.documentIds]
        filter_expr = build_filter_expr(enabled_docs_str)
        partition_name = f"part_{request.conversationId}"

        dense_req = AnnSearchRequest(
            data=[query_dense],
            anns_field="vector",
            param={"metric_type": "COSINE", "params": {"ef": 64}},
            limit=10,
            expr=filter_expr
        )
        
        sparse_req = AnnSearchRequest(
            data=[query_sparse],
            anns_field="sparse_vector",
            param={"metric_type": "IP", "params": {"drop_ratio_search": 0.2}},
            limit=10,
            expr=filter_expr
        )

        search_results = collection.hybrid_search(
            reqs=[dense_req, sparse_req],
            rerank=RRFRanker(k=60),
            limit=10,
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

        # Bước 4: Context Expansion (Mở rộng ngữ cảnh)
        # Truy vấn lấy tất cả các chunks của các doc_id trong hits để tìm chunk trước + sau
        hit_doc_ids = list(set([h.entity.get("doc_id") for h in hits]))
        doc_ids_list = ",".join([f"'{d}'" for d in hit_doc_ids])
        
        all_doc_chunks = {}
        if hit_doc_ids:
            # Query tất cả chunks của các docs này trong Milvus
            all_chunks_query = collection.query(
                expr=f"doc_id in [{doc_ids_list}]",
                output_fields=["doc_id", "content", "start_idx", "end_idx"],
                partition_names=[partition_name],
                limit=10000
            )
            # Gom nhóm theo doc_id và sắp xếp theo start_idx
            for c in all_chunks_query:
                doc_id = c["doc_id"]
                if doc_id not in all_doc_chunks:
                    all_doc_chunks[doc_id] = []
                all_doc_chunks[doc_id].append(c)
                
            for doc_id in all_doc_chunks:
                all_doc_chunks[doc_id].sort(key=lambda x: x["start_idx"])

        candidates = []
        for h in hits:
            doc_id = h.entity.get("doc_id")
            start_idx = h.entity.get("start_idx")
            end_idx = h.entity.get("end_idx")
            content = h.entity.get("content")

            c_list = all_doc_chunks.get(doc_id, [])
            
            # Tìm vị trí chunk hiện tại trong danh sách đã sắp xếp
            curr_idx = -1
            for idx, c in enumerate(c_list):
                if c["start_idx"] == start_idx and c["end_idx"] == end_idx:
                    curr_idx = idx
                    break
                    
            big_content = content
            big_start = start_idx
            big_end = end_idx
            
            if curr_idx != -1:
                parts = []
                if curr_idx > 0:
                    prev_c = c_list[curr_idx - 1]
                    parts.append(prev_c["content"])
                    big_start = min(big_start, prev_c["start_idx"])
                
                parts.append(content)
                
                if curr_idx < len(c_list) - 1:
                    next_c = c_list[curr_idx + 1]
                    parts.append(next_c["content"])
                    big_end = max(big_end, next_c["end_idx"])
                    
                big_content = "\n".join(parts)
                
            candidates.append({
                "doc_id": doc_id,
                "content": big_content,
                "start_idx": big_start,
                "end_idx": big_end
            })

        # Loại bỏ các Big Chunks trùng lặp
        unique_candidates = []
        seen = set()
        for cand in candidates:
            k = f"{cand['doc_id']}_{cand['start_idx']}_{cand['end_idx']}"
            if k not in seen:
                seen.add(k)
                unique_candidates.append(cand)

        # Bước 5: Reranking (BGE-Reranker)
        candidate_texts = [c["content"] for c in unique_candidates]
        scores = await asyncio.to_thread(local_rerank, request.contentQuery, candidate_texts)
        for i, s in enumerate(scores):
            unique_candidates[i]["rerank_score"] = float(s)

        sorted_candidates = sorted(unique_candidates, key=lambda x: x["rerank_score"], reverse=True)

        print(f"\n[RERANK] Kết quả sau reranking (top {len(sorted_candidates)}):")
        for i, c in enumerate(sorted_candidates):
            marker = "✓ TOP" if i < 3 else "   "
            print(f"  {marker}[{i}] doc_id={c['doc_id']} | "
                  f"rerank_score={c['rerank_score']:.4f} | "
                  f"idx=[{c['start_idx']},{c['end_idx']}] | "
                  f"content='{str(c['content'])[:80]}...'")

        top_chunks = sorted_candidates[:3]
        print(f"\n[CONTEXT] Sử dụng {len(top_chunks)} chunks cho LLM (nhãn 1-based):")
        for i, chunk in enumerate(top_chunks):
            print(f"  [{i + 1}] doc_id={chunk['doc_id']} | score={chunk['rerank_score']:.4f} | "
                  f"content='{str(chunk['content'])[:100]}...'")
        print(f"{'='*60}\n")

        # Bước 6: Chuẩn bị context (1-based, khớp với system prompt [1],[2],[3])
        context_str = ""
        for i, chunk in enumerate(top_chunks):
            text_limited = chunk['content'][:2500] # Giới hạn 2500 ký tự mỗi chunk để tránh tràn context
            context_str += f" Tài liệu [{i + 1}]: {text_limited}\n\n"

        print(f"[LLM] Đang gọi Hugging Face API với context dài {len(context_str)} ký tự...")
        # Bước 7: Gọi Gemma qua HF Router (OpenAI-compatible)
        user_msg = build_user_message(context_str, request.contentQuery)
        try:
            raw_answer = await asyncio.to_thread(hf_generate, SYSTEM_PROMPT, user_msg)
        except Exception as api_err:
            print(f"[LLM] LỖI GỌI HUGGINGFACE API: {api_err}")
            raise api_err

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
