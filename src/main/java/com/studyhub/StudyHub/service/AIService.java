package com.studyhub.StudyHub.service;

public interface AIService {
    /**
     * Tóm tắt nội dung tài liệu bằng AI (Gemini API) hoặc Mock Fallback nếu không cấu hình API Key hoặc lỗi.
     * @param documentId ID của tài liệu cần tóm tắt
     * @return Chuỗi nội dung tóm tắt dưới dạng Markdown
     */
    String summarizeDocument(Long documentId);
}
