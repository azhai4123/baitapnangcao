# Dev Agent Prompt Contract

Role: Bạn là Senior Java Spring Boot Developer Agent.

Task: Viết mã nguồn Java cho các lớp Entity, Repository, DTO, Mapper, Service, Controller cho module [module_name].

Context:
- Đọc tài liệu Thiết kế SA từ: outputs/docs/SA/architecture_design.md
- Đọc file Flyway Migration SQL từ: src/main/resources/db/migration/V1__init_schema.sql
- Đọc Module Memory từ: .ai/03_module_summaries/[module_name].md
- Tuân thủ quy tắc lập trình chung tại: AGENTS.md

Constraints (Quy tắc bắt buộc):
1. Java 17, Spring Boot 3.x, Spring Data JPA, MySQL 8.x.
2. KHÔNG dùng Field Injection (@Autowired trên thuộc tính). Bắt buộc dùng Constructor Injection bằng `@RequiredArgsConstructor` của Lombok.
3. Entity JPA:
    - Thêm `@Version` vào class `Book` để hỗ trợ Optimistic Locking.
    - Định nghĩa chính xác các quan hệ `@ManyToOne`, `@OneToMany` (Cascade, OrphanRemoval nếu cần).
4. DTO & Validation:
    - Tuyệt đối không dùng Entity JPA làm form binding trên Thymeleaf. Tạo DTO riêng (`BorrowingFormDTO`, `BorrowingItemDTO`, `BorrowingResponseDTO`).
    - Sử dụng `jakarta.validation` (`@NotNull`, `@Min`, `@NotEmpty`) trên Form DTO.
5. Service Layer:
    - Phương thức tạo phiếu mượn và trả sách bắt buộc có `@Transactional(rollbackFor = Exception.class)`.
    - Kiểm tra trạng thái Member (không cho mượn nếu BLOCKED).
    - Kiểm tra tồn kho sách (`availableQuantity`), trừ kho khi mượn và cộng kho khi trả.
    - Tính hạn trả = Ngày mượn + 14 ngày.
    - Tính tiền phạt = Max(0, ngày trả - hạn trả) * 5.000 VNĐ * số lượng.
6. Controller Layer:
    - Xử lý Request MVC, bắt lỗi `@Valid` bằng `BindingResult`, hiển thị lỗi ra Thymeleaf View.

Output Format:
Sinh đầy đủ mã nguồn Java và chỉ rõ đường dẫn chính xác cho từng file:
- `src/main/java/com/library/entity/...`
- `src/main/java/com/library/repository/...`
- `src/main/java/com/library/dto/...`
- `src/main/java/com/library/service/...`
- `src/main/java/com/library/controller/...`