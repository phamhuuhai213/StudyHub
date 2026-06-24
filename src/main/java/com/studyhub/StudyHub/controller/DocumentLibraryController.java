package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.Category;
import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.repository.CategoryRepository;
import com.studyhub.StudyHub.repository.DocumentRepository;
import com.studyhub.StudyHub.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
public class DocumentLibraryController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/documents")
    public String showDocumentLibrary(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            Model model) {

        // 1. Lấy danh sách Categories
        List<Category> categories = categoryRepository.findAll();


        // Làm sạch keyword trước khi xử lý
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }


        // 2. Tìm kiếm documents (Lúc này keyword đã được xử lý chuẩn)
        List<Document> documents = documentService.searchDocuments(keyword, categoryId);

        // 3. Đẩy dữ liệu ra View
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword); // Nếu null, Thymeleaf sẽ tạo link sạch (/documents) thay vì (/documents?keyword=)
        model.addAttribute("currentCategoryId", categoryId);
        model.addAttribute("pageTitle", "Kho tài liệu");

        return "documents";
    }

    @PostMapping("/api/documents/{id}/view")
    @ResponseBody
    public ResponseEntity<?> incrementView(@PathVariable Long id) {
        Document doc = documentService.getDocumentById(id);
        if (doc != null) {
            doc.setViews(doc.getViews() + 1);
            documentRepository.save(doc);

            // Broadcast qua WebSocket để tất cả Client đang xem cập nhật realtime số lượt xem/tải
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "DOCUMENT_STATS");
                payload.put("documentId", id);
                payload.put("views", doc.getViews());
                payload.put("downloads", doc.getDownloads());
                messagingTemplate.convertAndSend("/topic/documents/stats", payload);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return ResponseEntity.ok(Map.of("success", true, "views", doc.getViews()));
        }
        return ResponseEntity.notFound().build();
    }
}