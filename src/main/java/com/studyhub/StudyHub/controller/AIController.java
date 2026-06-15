package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class AIController {

    @Autowired
    private AIService aiService;

    /**
     * API gọi tóm tắt tài liệu bằng AI
     * @param id ID của tài liệu cần tóm tắt
     * @return JSON chứa nội dung tóm tắt
     */
    @GetMapping("/{id}/summarize")
    public ResponseEntity<?> getAISummary(@PathVariable Long id) {
        try {
            String summary = aiService.summarizeDocument(id);
            return ResponseEntity.ok(Map.of("success", true, "summary", summary));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
