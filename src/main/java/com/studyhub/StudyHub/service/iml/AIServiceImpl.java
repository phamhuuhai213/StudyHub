package com.studyhub.StudyHub.service.iml;

import com.studyhub.StudyHub.entity.Category;
import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.repository.DocumentRepository;
import com.studyhub.StudyHub.service.AIService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIServiceImpl implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${openrouter.api.key:}")
    private String openRouterKey;

    @Value("${openrouter.model:google/gemini-2.5-flash}")
    private String openRouterModel;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String summarizeDocument(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu với ID: " + documentId));

        // 1. Trích xuất nội dung text từ file
        String extractedText = extractTextFromFile(doc);

        // 2. Nếu không có API Key, sử dụng Mock AI ngay lập tức
        if (openRouterKey == null || openRouterKey.trim().isEmpty()) {
            log.info("OpenRouter API Key trống, sử dụng chế độ Mock AI.");
            return generateMockSummary(doc, extractedText);
        }

        // 3. Gọi OpenRouter API
        try {
            return callOpenRouterAPI(doc, extractedText);
        } catch (Exception e) {
            log.error("Lỗi khi gọi OpenRouter API, tự động fallback sang Mock AI. Lỗi: {}", e.getMessage());
            return generateMockSummary(doc, extractedText) + "\n\n*(Lưu ý: Đây là tóm tắt được tạo tự động ngoại tuyến do lỗi kết nối API)*";
        }
    }

    /**
     * Trích xuất văn bản từ file tải lên (hỗ trợ .txt, .pdf)
     */
    private String extractTextFromFile(Document doc) {
        String fileName = doc.getFileName();
        if (fileName == null) return "";

        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        Path filePath = Paths.get(uploadDir, doc.getStoragePath());
        File file = filePath.toFile();

        if (!file.exists()) {
            log.warn("Không tìm thấy file vật lý tại đường dẫn: {}", filePath);
            return "";
        }

        try {
            if ("txt".equals(ext) || "md".equals(ext) || "html".equals(ext)) {
                // Đọc file text thông thường (giới hạn 5000 ký tự)
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                return content.substring(0, Math.min(content.length(), 5000));
            } else if ("pdf".equals(ext)) {
                // Trích xuất text từ file PDF (giới hạn 5 trang đầu)
                try (PDDocument document = PDDocument.load(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(Math.min(5, document.getNumberOfPages()));
                    String pdfText = stripper.getText(document);
                    return pdfText.substring(0, Math.min(pdfText.length(), 5000));
                }
            }
        } catch (IOException e) {
            log.error("Lỗi khi đọc file tài liệu: {}", e.getMessage());
        }

        return "";
    }

    /**
     * Gọi OpenRouter API để tóm tắt tài liệu
     */
    private String callOpenRouterAPI(Document doc, String textContent) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        // Xây dựng prompt chất lượng
        String prompt = "Bạn là trợ lý AI học tập xuất sắc của StudyHub. Hãy đọc tài liệu học tập sau đây và viết tóm tắt chi tiết bằng tiếng Việt.\n" +
                "Yêu cầu định dạng đầu ra rõ ràng bằng Markdown:\n" +
                "1. **Tổng quan tài liệu**: (Mô tả ngắn gọn về tài liệu này là gì, chủ đề chính là gì)\n" +
                "2. **Các kiến thức cốt lõi/điểm chính**: (Liệt kê các mục lý thuyết, công thức hoặc ý chính quan trọng nhất dưới dạng danh sách gạch đầu dòng)\n" +
                "3. **Kết luận & Lời khuyên ôn tập**: (Khuyên học viên nên tập trung vào phần nào để làm bài tốt)\n\n" +
                "Thông tin bổ sung:\n" +
                "- Tiêu đề tài liệu: " + doc.getTitle() + "\n" +
                "- Danh mục: " + (doc.getCategory() != null ? doc.getCategory().getName() : "Chung") + "\n" +
                "- Từ khóa: " + (doc.getTags() != null ? doc.getTags() : "Không có") + "\n\n" +
                "Nội dung trích xuất từ tài liệu:\n\"\"\"\n" +
                (textContent.isEmpty() ? "[Không trích xuất được nội dung trực tiếp từ file, hãy dựa vào thông tin tiêu đề và mô tả để tóm tắt]" : textContent) +
                "\n\"\"\"";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openRouterKey);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "StudyHub");

        // Tạo request body OpenAI/OpenRouter chat completions
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openRouterModel != null && !openRouterModel.isEmpty() ? openRouterModel : "google/gemini-2.5-flash");
        requestBody.put("messages", List.of(message));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Gọi API
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        Map body = response.getBody();

        if (body == null || !body.containsKey("choices")) {
            throw new RuntimeException("Phản hồi rỗng hoặc sai định dạng từ OpenRouter API");
        }

        List choices = (List) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Không tìm thấy kết quả từ choices");
        }

        Map choice = (Map) choices.get(0);
        Map messageMap = (Map) choice.get("message");
        return (String) messageMap.get("content");
    }

    /**
     * Thuật toán Mock AI sinh tóm tắt thông minh offline
     */
    private String generateMockSummary(Document doc, String textContent) {
        String title = doc.getTitle();
        String tags = doc.getTags();
        String category = (doc.getCategory() != null) ? doc.getCategory().getName() : "Học tập";
        String desc = (doc.getDescription() != null && !doc.getDescription().trim().isEmpty()) 
                ? doc.getDescription() 
                : "Tài liệu học tập chất lượng cao chia sẻ trên StudyHub.";

        // Phân tích một số từ khóa trong văn bản trích xuất để sinh nội dung thực tế hơn
        StringBuilder autoKeywords = new StringBuilder();
        if (textContent != null && !textContent.isEmpty()) {
            String lower = textContent.toLowerCase();
            if (lower.contains("java") || lower.contains("spring") || lower.contains("oop")) {
                autoKeywords.append("- **Lập trình Java**: Kiến trúc hướng đối tượng, xử lý ngoại lệ và luồng dữ liệu.\n");
            }
            if (lower.contains("database") || lower.contains("sql") || lower.contains("mysql")) {
                autoKeywords.append("- **Cơ sở dữ liệu**: Thiết kế thực thể liên kết, truy vấn SQL tối ưu.\n");
            }
            if (lower.contains("html") || lower.contains("css") || lower.contains("javascript")) {
                autoKeywords.append("- **Phát triển Web**: Xây dựng giao diện Responsive, tương tác DOM và tối ưu UX.\n");
            }
            if (lower.contains("math") || lower.contains("tính") || lower.contains("phương trình")) {
                autoKeywords.append("- **Toán học & Giải thuật**: Thuật toán tối ưu, tính toán logic và phân tích thống kê.\n");
            }
        }

        if (autoKeywords.length() == 0) {
            autoKeywords.append("- **Kiến thức cốt lõi**: Khái niệm nền tảng, các định nghĩa cơ bản và sơ đồ phân tích trực quan.\n");
            autoKeywords.append("- **Phương pháp thực hành**: Các bước triển khai thực tế, bài tập tự luyện nâng cao nhận thức.\n");
        }

        return String.format(
                "### 🤖 Tóm tắt tài liệu học tập bằng AI (StudyHub Smart Engine)\n\n" +
                "#### 1. **Tổng quan tài liệu**\n" +
                "- **Tiêu đề**: %s\n" +
                "- **Chuyên mục**: Chuyên sâu về **%s**\n" +
                "- **Từ khóa nổi bật**: `%s`\n" +
                "- **Mô tả sơ bộ**: %s\n\n" +
                "#### 2. **Các kiến thức cốt lõi / Điểm chính**\n" +
                "%s" +
                "- **Phương pháp tiếp cận**: Trình bày logic từ lý thuyết căn bản đến ví dụ ứng dụng thực tế.\n\n" +
                "#### 3. **Khuyên dùng & Lời khuyên ôn tập**\n" +
                "- **Đối tượng phù hợp**: Dành cho học sinh, sinh viên tự học hoặc chuẩn bị ôn tập cho các kỳ kiểm tra học phần.\n" +
                "- **Chiến lược ôn thi**: Tập trung ôn luyện các phần bài tập thực hành ở cuối tài liệu và nắm vững các định nghĩa lý thuyết cốt lõi được nêu trong phần mở đầu.",
                title, category, (tags != null && !tags.isEmpty()) ? tags : "study, share", desc, autoKeywords.toString()
        );
    }
}
