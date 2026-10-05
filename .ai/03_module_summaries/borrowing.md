# Module Summary — Borrowing

## Mục tiêu

Quản lý lập phiếu mượn, trả sách, tính phạt quá hạn và báo cáo các phiếu còn sách quá hạn trong ứng dụng thư viện.

## CodeGraph 5 bước

CodeGraph index không có sẵn (`.codegraph` vắng mặt; lệnh `codegraph explore` xác nhận không thể dùng). Vì vậy không tạo/rebuild index; phạm vi được xác định bằng đọc trực tiếp prompt, SA, migration và các file Java/test liên quan.

1. **Search:** Kiểm tra `.codegraph`, tìm codegraph CLI, xem package Java hiện có, test context hiện hữu và dependencies trong `pom.xml`.
2. **Explore:** Đối chiếu `architecture_design.md`, `V1__init_schema.sql`, `AGENTS.md` và application entry point. Module implementation mới; schema gồm categories, books, members, borrowings, borrowing_details.
3. **Impact:** Package yêu cầu `com.library` nằm ngoài root package `org.example.library`; đã mở component scan, entity scan và JPA repository scan. Baseline thiếu JPA/validation/Flyway và datasource config; đã bổ sung dependencies/env config, context test dùng H2.
4. **Edit Scope:** `src/main/java/com/library/{entity,repository,dto,mapper,service,controller,config}`, Thymeleaf templates, application entry/config, Maven dependencies, test config và unit tests.
5. **Affected Test:** `src/test/java/com/library/service/BorrowingServiceTest.java` bao phủ mượn thành công, member khóa, thiếu tồn và trả trễ một ngày; `LibraryApplicationTests` là context smoke test hiện có.

## Package và thành phần

- Base package: `com.library`.
- `entity`: `Category`, `Book`, `Member`, `Borrowing`, `BorrowingDetail`, `FinePolicy`, `FinePayment`, `FineWaiver` và enum trạng thái; book/detail dùng `@Version`.
- `repository`: truy cập sách/thành viên/phiếu/chi tiết/phí; khóa member khi lập phiếu, khóa borrowing khi thanh toán và policy khi đổi policy.
- `dto`: DTO form và response cho mượn, trả một phần, thanh toán, miễn giảm, policy và danh sách sách; form tách entity, dùng Jakarta Validation.
- `mapper`: `BorrowingMapper` chuyển entity sang response DTO.
- `service`: `BorrowingService`, `FineCalculationService`, `FineManagementService` xử lý giao dịch mượn/trả/hủy, tính phí, thanh toán và miễn giảm.
- `controller`: MVC controllers cung cấp `/books`, `/borrowings`, `/borrowings/{id}/returns/*`, `/borrowings/{id}/payments/*`, `/borrowings/{id}/waivers/*`, `/fine-policies` và `/reports/overdue`.
- `config`: `Clock` bean để tính ngày nhất quán và có thể thay bằng clock cố định trong test.

## Luồng nghiệp vụ hiện thực

- Chỉ member `ACTIVE` được mượn; member `LOCKED` bị từ chối.
- Mỗi detail lưu `quantity` và `returnedQuantity`; từ chối `bookId` trùng, phiếu rỗng, tồn không đủ, giữ quá 5 bản, độc giả quá hạn hoặc còn nợ.
- Lập/trả/hủy thay đổi tồn kho trong transaction. Hủy chỉ được phép khi chưa trả bản nào và chưa phát sinh phạt.
- Trả một phần cập nhật tồn và tính phí trên số lượng của đợt trả; preview không ghi dữ liệu, xác nhận tính lại phía server. Fine cap áp dụng xuyên nhiều đợt trả cho cùng một detail.
- `FineCalculationService` dùng policy active (daily amount, grace days, max amount); trạng thái phiếu và trạng thái phí được tách riêng. Thanh toán/miễn giảm khóa phiếu, cấm trả vượt dư nợ.
- Member write lock tuần tự hóa việc kiểm tra hạn mức 5 cuốn; optimistic version trên Book/detail và borrowing ngăn cập nhật đồng thời ghi đè.
- Form cho phép nhập hạn trả, mặc định 14 ngày sau ngày hiện tại; ngày overdue được suy ra khi đọc. Mốc 14 ngày và timezone theo `Clock.systemDefaultZone` vẫn là quy tắc cấu hình cố định.
- Tính ngày dùng ngày lịch theo `Clock.systemDefaultZone`; trả đúng hạn không phạt, trễ một ngày phạt 5.000 VNĐ. Cần chốt timezone/ngày lịch với BA.

## Endpoint/UI

- `GET /borrowings`, `GET /borrowings/new`, `POST /borrowings`
- `GET /borrowings/{id}`, `GET /borrowings/{id}/returns/new`
- `POST /borrowings/{id}/returns/preview`, `POST /borrowings/{id}/returns`
- `POST /borrowings/{id}/cancel`, `/borrowings/{id}/payments`, `/borrowings/{id}/waivers`
- `GET/POST /fine-policies`, `GET /books`, `GET /reports/overdue`.
- Templates Thymeleaf có form validation cho mượn, trả từng phần, thanh toán, miễn giảm, policy; trang sách hỗ trợ tìm kiếm/phân trang, danh sách phiếu hỗ trợ lọc.

## Cấu hình và kiểm thử

- Spring Boot 3.5.6, Java 21, Spring Data JPA, Validation, Flyway MySQL; cấu hình MySQL lấy từ `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Migration V1 tạo schema cơ bản; V2 thêm trạng thái, trả từng phần, policy, payment và waiver, đồng thời backfill dữ liệu cũ.
- Test profile dùng H2 in-memory, tắt Flyway và tạo schema từ JPA metadata; production dùng Flyway V1 và `ddl-auto=validate`.
- Unit test: `src/test/java/com/library/service/BorrowingServiceTest.java`; MVC/form tests: `src/test/java/com/library/controller/BorrowingControllerTest.java`; context test: `src/test/java/org/example/library/LibraryApplicationTests.java`.
- Project dùng Spring Boot `3.5.6`; môi trường làm việc hiện có JDK 17 nên test được chạy với override `-Djava.version=17`; cần JDK 21 cho build chuẩn.
- Module summaries không có file mẫu/template sẵn trong `.ai`; cấu trúc summary này được lập theo các mục tiêu, scope, flow, risk và tests.

## Files affected / Reason / Risk / Tests required

- **Files affected:** `pom.xml`, `HELP.md`, `src/main/java/com/library/**`, `src/main/resources/db/migration/V2__fine_and_partial_returns.sql`, Thymeleaf templates, service/controller tests, this summary.
- **Reason:** Hoàn thiện nghiệp vụ mượn/trả, phí, thanh toán và giao diện MVC theo đề bài MySQL.
- **Risk:** V2 chưa được chạy trên MySQL do yêu cầu human review và MySQL CLI/JDK 21 không có trong môi trường này; cần xác nhận migration trên MySQL 8 trước khi áp dụng DB thật.
- **Tests required:** Unit/service và MVC H2 đã chạy; còn cần chạy lại `mvn test` bằng JDK 21 và integration/concurrency test trên MySQL 8.