Quy Tắc Hoạt Động của AGENTS



## 1. Context Rules (Quy tắc ngữ cảnh)
1. Không tự ý quét (read/cat/grep) toàn bộ repository khi chưa có chỉ định rõ ràng.
2. Luôn đọc file tóm tắt trong `.ai/03_module_summaries/` trước khi xem mã nguồn Java.
3. Chỉ đọc và chỉnh sửa các file liên quan trực tiếp đến task và file test tương ứng.
4. Mọi đề xuất thay đổi code phải nêu rõ: Files affected, Reason, Risk, Tests required.

## 2. Coding Rules (Quy tắc viết code)
1. Tuân thủ Spring Boot 3.x, Java 17+.
2. KHÔNG dùng Field Injection (`@Autowired` trên field). Bắt buộc dùng Constructor Injection hoặc `@RequiredArgsConstructor` của Lombok.
3. Không dùng Entity JPA trực tiếp làm form binding trên Thymeleaf View. Bắt buộc dùng DTO riêng.
4. Mọi thao tác mượn/trả sách bắt buộc chạy trong `@Transactional`.

## 3. Safety Rules (Quy tắc an toàn)
1. Tuyệt đối không xóa file, không commit secret hoặc file `.env`.
2. Không tự ý deploy hoặc push thẳng vào nhánh `main`/`master`.
3. Mọi script Migration DB (Flyway) phải được con người (Human) review trước khi chạy.