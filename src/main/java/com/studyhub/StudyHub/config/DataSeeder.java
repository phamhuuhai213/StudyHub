package com.studyhub.StudyHub.config;

import com.studyhub.StudyHub.entity.*;
import com.studyhub.StudyHub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataSeeder implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        // 1. Tạo role Admin
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_ADMIN");
                    return roleRepository.save(role);
                });

        // Tạo role User
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_USER");
                    return roleRepository.save(role);
                });

        // 2. Tạo tài khoản Admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setName("Administrator");
            admin.setUsername("admin");
            admin.setEmail("admin@studyhub.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEnabled(true);
            admin.setUserType(UserType.OTHER);

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            roles.add(userRole);
            admin.setRoles(roles);

            userRepository.save(admin);
        }

        // 3. Tạo tài khoản phuuhai
        User phuuhai = userRepository.findByUsername("phuuhai")
                .orElseGet(() -> {
                    User u = new User();
                    u.setName("Phạm Hữu Hải");
                    u.setUsername("phuuhai");
                    u.setEmail("phamhuuhai213@gmail.com");
                    u.setPassword(passwordEncoder.encode("123"));
                    u.setEnabled(true);
                    u.setUserType(UserType.STUDENT);
                    u.setSchool("Đại học Công nghệ Thông tin và Truyền thông Việt - Hàn");
                    u.setMajor("Công nghệ thông tin");
                    u.setBio("Học hỏi là hành trình trọn đời.");
                    u.setRoles(Set.of(userRole));
                    return userRepository.save(u);
                });

        // 4. Tạo tài khoản huykhoa
        User huykhoa = userRepository.findByUsername("huykhoa")
                .orElseGet(() -> {
                    User u = new User();
                    u.setName("Nguyễn Huy Khoa");
                    u.setUsername("huykhoa");
                    u.setEmail("huykhoa@gmail.com");
                    u.setPassword(passwordEncoder.encode("123"));
                    u.setEnabled(true);
                    u.setUserType(UserType.STUDENT);
                    u.setSchool("Đại học Công nghệ Thông tin và Truyền thông Việt - Hàn");
                    u.setMajor("An toàn thông tin");
                    u.setBio("Đam mê lập trình và nghiên cứu AI.");
                    u.setRoles(Set.of(userRole));
                    return userRepository.save(u);
                });

        // 5. Kết bạn giữa phuuhai và huykhoa
        if (friendshipRepository.findRelationship(phuuhai, huykhoa).isEmpty()) {
            Friendship friendship = new Friendship();
            friendship.setRequester(phuuhai);
            friendship.setAddressee(huykhoa);
            friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
            friendship.setCreatedAt(LocalDateTime.now());
            friendshipRepository.save(friendship);
        }

        // 6. Tạo Categories
        Category toanHoc = categoryRepository.findByName("Toán học")
                .orElseGet(() -> {
                    Category cat = new Category();
                    cat.setName("Toán học");
                    cat.setDescription("Giải tích, Đại số, Xác suất thống kê...");
                    cat.setColor("#4F46E5");
                    cat.setIcon("fa-calculator");
                    return categoryRepository.save(cat);
                });

        Category tinHoc = categoryRepository.findByName("Tin học")
                .orElseGet(() -> {
                    Category cat = new Category();
                    cat.setName("Tin học");
                    cat.setDescription("Cấu trúc dữ liệu, Thuật toán, Java...");
                    cat.setColor("#0EA5E9");
                    cat.setIcon("fa-code");
                    return categoryRepository.save(cat);
                });

        Category ngoaiNgu = categoryRepository.findByName("Ngoại ngữ")
                .orElseGet(() -> {
                    Category cat = new Category();
                    cat.setName("Ngoại ngữ");
                    cat.setDescription("Tiếng Anh, Tiếng Nhật, Tiếng Trung...");
                    cat.setColor("#10B981");
                    cat.setIcon("fa-language");
                    return categoryRepository.save(cat);
                });

        Category kyNangMem = categoryRepository.findByName("Kỹ năng mềm")
                .orElseGet(() -> {
                    Category cat = new Category();
                    cat.setName("Kỹ năng mềm");
                    cat.setDescription("Kỹ năng giao tiếp, Thuyết trình...");
                    cat.setColor("#F59E0B");
                    cat.setIcon("fa-lightbulb");
                    return categoryRepository.save(cat);
                });

        // 7. Tạo bài viết mẫu (nếu chưa có bài viết nào)
        if (postRepository.count() == 0) {
            // Post 1 của phuuhai kèm tài liệu Xác suất thống kê
            Post post1 = new Post();
            post1.setUser(phuuhai);
            post1.setContent("Chào mọi người, mình chia sẻ tài liệu ôn tập Xác suất Thống kê cho các bạn ôn thi VKU nhé. Đầy đủ các chương lý thuyết ước lượng và kiểm định.");
            post1.setPublic(true);
            post1 = postRepository.save(post1);

            Document doc1 = new Document();
            doc1.setPost(post1);
            doc1.setUser(phuuhai);
            doc1.setCategory(toanHoc);
            doc1.setTitle("Tài liệu ôn thi Xác suất thống kê");
            doc1.setDescription("Đề cương tóm tắt lý thuyết và các dạng bài tập Xác suất thống kê VKU.");
            doc1.setTags("xacsuat, thongke, vku");
            doc1.setFileName("DCCT. Xac xuat thong ke.pdf");
            doc1.setStoragePath("93852d3a-15ae-437d-b55b-a98f14754177-DCCT. Xac xuat thong ke.pdf");
            doc1.setFileType("application/pdf");
            doc1.setFileSize(497734L);
            doc1.setViews(32);
            doc1.setDownloads(12);
            doc1.setIsPublic(true);
            documentRepository.save(doc1);

            // Comment mẫu cho post 1
            Comment comment1 = new Comment();
            comment1.setPost(post1);
            comment1.setUser(huykhoa);
            comment1.setContent("Tài liệu xịn quá, cảm ơn Hải nhé!");
            comment1.setCreatedAt(LocalDateTime.now().minusHours(2));
            commentRepository.save(comment1);

            // Post 2 của huykhoa kèm tài liệu C3. Ly thuyet chon mau
            Post post2 = new Post();
            post2.setUser(huykhoa);
            post2.setContent("Tài liệu Lý thuyết chọn mẫu cực kỳ chi tiết cho các bạn ôn tập.");
            post2.setPublic(true);
            post2 = postRepository.save(post2);

            Document doc2 = new Document();
            doc2.setPost(post2);
            doc2.setUser(huykhoa);
            doc2.setCategory(toanHoc);
            doc2.setTitle("Lý thuyết chọn mẫu - Chương 3");
            doc2.setDescription("Tóm tắt công thức và bài tập chương 3 lý thuyết chọn mẫu.");
            doc2.setTags("chonmau, toan, thongke");
            doc2.setFileName("C3. Ly thuyet chon mau.pdf");
            doc2.setStoragePath("9e7e888a-d301-4be3-9648-43343037d482-C3. Ly thuyet chon mau.pdf");
            doc2.setFileType("application/pdf");
            doc2.setFileSize(867109L);
            doc2.setViews(25);
            doc2.setDownloads(8);
            doc2.setIsPublic(true);
            documentRepository.save(doc2);

            // Post 3 của phuuhai kèm file test.txt
            Post post3 = new Post();
            post3.setUser(phuuhai);
            post3.setContent("Đây là file text ngắn để kiểm tra tính năng tóm tắt tài liệu bằng AI.");
            post3.setPublic(true);
            post3 = postRepository.save(post3);

            Document doc3 = new Document();
            doc3.setPost(post3);
            doc3.setUser(phuuhai);
            doc3.setCategory(tinHoc);
            doc3.setTitle("File Text Kiểm thử Tóm tắt AI");
            doc3.setDescription("Văn bản ngắn kiểm tra trích xuất text.");
            doc3.setTags("test, ai, txt");
            doc3.setFileName("test.txt");
            doc3.setStoragePath("3444cc36-c346-4d30-8545-8ae9d102ae5e-test.txt");
            doc3.setFileType("text/plain");
            doc3.setFileSize(11L);
            doc3.setViews(12);
            doc3.setDownloads(4);
            doc3.setIsPublic(true);
            documentRepository.save(doc3);
        }
    }
}
