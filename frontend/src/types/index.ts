export interface AuthRequest {
  userName: string
  password: string
}

export interface RegisterRequest {
  userName: string
  password: string
  email: string
  name: string
}

export interface AuthResponse {
  token: string
  message: string
}

export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED' | 'DELETING' | 'DELETED' | 'DELETE_FAILED'

export interface DocumentSummary {
  id: number
  fileName: string
  status: DocumentStatus
  createdAt: string
  updatedAt: string
}

export interface ConversationSummary {
  id: number
  title: string
  status: string
  documentCount: number
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface CitationResponse {
  id: number
  documentId: number
  fileName: string
  startIndex: number
  endIndex: number
}

export interface MessageResponse {
  id: number
  conversationId: number
  contentQuery: string
  contentAnswer: string
  selectedDocuments: DocumentSummary[]
  citationsResponses: CitationResponse[]
  createdAt: string
}

export interface ConversationHistoryResponse {
  conversationId: number
  title: string
  status: string
  documents: DocumentSummary[]
  messages: MessageResponse[]
  createdAt: string
  updatedAt: string
}

export interface ConversationCreateResponse {
  id: number
  title: string
  documents: DocumentSummary[]
  createdAt: string
  updatedAt: string
}

export interface ConversationDeleteResponse {
  conversationId: number
  status: string
  message: string
  totalDocuments: number
  asyncPending: number
  deletedImmediately: number
}

export interface DocumentUploadResponse {
  uploadedDocuments: DocumentSummary[]
}

export interface DocumentContentResponse {
  documentId: number
  fileName: string
  content: string
}

export interface SendMessageRequest {
  conversationId: number
  contentQuery: string
  documentIds: number[]
}

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail: string
}
