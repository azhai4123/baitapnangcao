# Thiết kế kiến trúc — Quản lý thư viện

## 1. Phạm vi, căn cứ và giả định

Tài liệu thiết kế dựa trên `outputs/docs/BA/UC_borrowing_spec.md`, `.ai/01_repo_inventory.md` và project brief.

- Các yêu cầu đã xác nhận: MySQL 8.x, Spring Boot/Java 17, Thymeleaf, quản lý sách/thành viên/mượn-trả, tồn kho khả dụng chính xác, phạt 5.000 VNĐ/ngày/cuốn, thành viên bị khóa không được mượn, email không trùng và giao dịch mượn/trả nguyên tử.
- Repo hiện có base package `org.example.library`, lớp khởi động Spring Boot và cấu hình WAR.
- Module summary mượn/trả rỗng; repo chưa có module domain hay migration. `pom.xml` hiện chưa khai báo Spring Data JPA hoặc Flyway, mặc dù project brief xác định hai công nghệ này.
- `pom.xml` hiện dùng Spring Boot `4.1.1`, trong khi project brief đặt mục tiêu Spring Boot 3.x; cần xác nhận/đồng bộ phiên bản trước khi triển khai dependency JPA/Flyway và kiểm thử tương thích.
- Các trường metadata (ISBN, tác giả, nhà xuất bản, năm xuất bản, điện thoại/địa chỉ, ghi chú) và trạng thái `ACTIVE`/`LOCKED` là thiết kế dữ liệu đề xuất, cần đối chiếu với chủ dự án trước khi xem là hợp đồng nghiệp vụ.
- Mỗi dòng `borrowing_details` đại diện **một cuốn** được mượn; một đầu sách có nhiều bản sẽ có nhiều dòng chi tiết. Mô hình này hỗ trợ trả và tính phạt riêng theo từng cuốn. Việc quản lý mã vạch/serial cho từng bản vật lý chưa nằm trong phạm vi đã xác nhận.

## 2. Tổng quan kiến trúc

Ứng dụng theo mô hình Spring MVC phân tầng, triển khai WAR:

| Lớp | Package đề xuất | Trách nhiệm |
|---|---|---|
| View | `src/main/resources/templates/` | Thymeleaf + Bootstrap; render trang, gửi form, hiển thị lỗi validation và kết quả nghiệp vụ. |
| Web | `org.example.library.web.controller` | Nhận HTTP request, bind Form DTO, chạy validation, gọi service, chọn view/redirect. Không chứa quy tắc nghiệp vụ hoặc truy vấn DB. |
| DTO | `org.example.library.web.dto.form`, `org.example.library.web.dto.view` | Form DTO cho request binding; View/Response DTO riêng cho dữ liệu trả về. Không bind/serialize Entity trực tiếp. |
| Application | `org.example.library.application.service` | Quy tắc mượn/trả, kiểm tra trạng thái thành viên và tồn kho, tính phạt; dùng constructor injection. Service giao dịch dùng `@Transactional(rollbackFor = Exception.class)`. |
| Domain/Persistence | `org.example.library.domain.entity`, `org.example.library.infrastructure.persistence.repository` | JPA Entity, quan hệ/column mapping và Spring Data repository. `@Version` dùng cho optimistic locking. |
| Database | MySQL 8.x | Dữ liệu bền vững, FK, unique/check constraints và indexes; Flyway quản lý schema. |

### Cấu trúc package

```text
org.example.library
├── web
│   ├── controller
│   └── dto
│       ├── form
│       └── view
├── application
│   └── service
├── domain
│   ├── entity
│   └── exception
└── infrastructure
    └── persistence
        └── repository
```

Sơ đồ phụ thuộc kiến trúc đích được cập nhật trong `.ai/01_repo_inventory.md`. `.codegraph` chưa tồn tại; vì vậy sơ đồ trong inventory là dependency map được đề xuất/duy trì thủ công, không tuyên bố là đồ thị sinh ra từ CodeGraph.

## 3. Thiết kế CSDL MySQL 8.x

### Quan hệ và quyết định mô hình

- `categories` 1—N `books`; `books.category_id` bắt buộc và không cho xóa danh mục còn được tham chiếu.
- `members` 1—N `borrowings`; thành viên lưu trạng thái `ACTIVE`/`LOCKED` và email duy nhất.
- `borrowings` 1—N `borrowing_details`; mỗi chi tiết là một cuốn, trỏ về đầu sách trong `books`.
- `borrowing_details.returned_at IS NULL` nghĩa là chưa trả. Hạn trả lấy từ phiếu mượn. `overdue_days` và `fine_amount` lưu kết quả tính tại thời điểm trả để giữ dấu vết khoản phạt.
- Không lưu trạng thái `OVERDUE` riêng: quá hạn là trạng thái suy ra từ chi tiết chưa trả và `due_date < ngày hiện tại`, tránh trạng thái lưu bị lỗi thời.
- `books.available_quantity` không được âm hoặc lớn hơn `total_quantity`; CHECK constraint bảo vệ điều kiện biên ở DB. Service phải cập nhật tồn kho trong cùng transaction với phiếu mượn/trả.
- `version` trên các bản ghi mutable hỗ trợ JPA optimistic locking; bắt buộc có ở `books` để chống ghi đè tồn kho đồng thời. Xung đột phải rollback và báo nhân viên tải lại dữ liệu, không retry mù.
- Indexes phục vụ tìm kiếm theo tiêu đề/tác giả/email, lọc tồn kho, lịch sử thành viên, danh sách đến hạn và truy vấn các chi tiết chưa trả.

### Flyway migration

Migration khởi tạo được lưu tại [`src/main/resources/db/migration/V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql). Đây là nguồn SQL chuẩn duy nhất, tạo năm bảng `categories`, `books`, `members`, `borrowings`, `borrowing_details`, với khóa chính `BIGINT AUTO_INCREMENT`, InnoDB, `utf8mb4`, foreign keys, unique/check constraints, chỉ mục truy vấn và cột `version`.

| Bảng | Các trường nghiệp vụ chính | Ràng buộc/chỉ mục trọng yếu |
|---|---|---|
| `categories` | `name`, `description` | Tên duy nhất |
| `books` | `category_id`, `isbn`, `title`, `author`, `publisher`, `publication_year`, `total_quantity`, `available_quantity`, `version` | ISBN duy nhất (nếu có), FK danh mục, khả dụng ≤ tổng, index tên/tác giả/danh mục/tồn kho |
| `members` | `full_name`, `email`, `phone`, `address`, `status`, `version` | Email duy nhất, status chỉ `ACTIVE`/`LOCKED`, index tên/trạng thái |
| `borrowings` | `member_id`, `borrowed_at`, `due_date`, `notes`, `version` | FK thành viên, index thành viên + ngày mượn và hạn trả |
| `borrowing_details` | `borrowing_id`, `book_id`, `returned_at`, `overdue_days`, `fine_amount`, `version` | FK phiếu/sách, index phiếu + ngày trả, sách và ngày trả |

**Lưu ý triển khai:** Trước khi ứng dụng khởi động được với migration, cần bổ sung dependency Spring Data JPA và Flyway tương thích Spring Boot đang dùng, cùng datasource MySQL. Tài liệu này không sửa `pom.xml` hay cấu hình kết nối vì yêu cầu hiện tại giới hạn việc tạo migration và tài liệu SA.

## 4. Entities, DTO và lớp truy cập dữ liệu

### Entity và repository đề xuất

| Entity | Quan hệ / lưu ý | Repository đề xuất |
|---|---|---|
| `Category` | `@OneToMany` phía inverse với `Book`; không cascade xóa bản ghi đang được dùng. | `CategoryRepository` |
| `Book` | `@ManyToOne Category`; `@Version int version`; quantity cập nhật qua service trong transaction. | `BookRepository` |
| `Member` | Email unique; status enum được lưu dạng chuỗi; `@Version int version`. | `MemberRepository` |
| `Borrowing` | `@ManyToOne Member`, `@OneToMany` details; có hạn trả. | `BorrowingRepository` |
| `BorrowingDetail` | Một bản sách trên một phiếu; `returnedAt` nullable; lưu snapshot ngày quá hạn/tiền phạt; `@Version int version`. | `BorrowingDetailRepository` |

Các entity được đánh dấu `@Entity`, dùng tên bảng/cột tường minh để khớp migration. Dùng `EnumType.STRING`; tránh xóa cascade có thể làm mất lịch sử mượn. Không dùng Lombok `@Data` trên entity có quan hệ hai chiều để tránh recursion/side effect.

### Form Binding DTO

- `BookForm`: `categoryId`, `isbn`, `title`, `author`, `publisher`, `publicationYear`, `totalQuantity`, `availableQuantity`. Validation: `@NotNull` category, `@NotBlank` title/author, `@PositiveOrZero` tổng/khả dụng, khả dụng ≤ tổng ở service/bean validation cross-field; ISBN format nếu chốt chuẩn.
- `MemberForm`: `fullName`, `email`, `phone`, `address`, `status`. Validation: tên/email bắt buộc, `@Email`, email unique kiểm tra ở service/DB; status thuộc enum cho phép.
- `BorrowingForm`: `memberId`, `dueDate`, `notes`, `List<BorrowingItemForm> items`. Item: `bookId`, `quantity`; service mở rộng quantity thành một detail row cho mỗi cuốn. Validation: member/date hợp lệ, ít nhất một item, quantity là số nguyên dương; member khóa và tồn kho phải được kiểm tra lại ở service khi commit.
- `ReturnForm`: `List<Long> borrowingDetailIds` (các cuốn chưa trả được chọn). Ngày trả do server quyết định; số ngày và tiền phạt do service tính, không nhận từ form để tránh client tự sửa.

Form DTO không chứa entity; các đối tượng lồng nhau được validate bằng `@Valid`. Hạn mượn tối đa, ngày trả lùi ngày, và hỗ trợ trả một phần theo quy trình UI cần chốt với Product Owner.

### Response / View DTO

- `BookView`: id, ISBN, title, author, category name, total/available quantity.
- `MemberView`: id, full name, email, phone, status.
- `BorrowingListItemView`: id, member display name, borrowedAt, dueDate, tổng cuốn còn mượn, cờ overdue suy ra.
- `BorrowingDetailView`: id, borrowing id, book id/title, dueDate, returnedAt, overdueDays, fineAmount.
- `BorrowingReceiptView`: id, member summary, timestamp, dueDate, chi tiết từng cuốn.
- `ReturnPreviewView`: danh sách cuốn chọn, ngày quá hạn và tiền phạt từng cuốn/tổng; giá trị tính lại phía server khi ghi nhận.

Controller trả View DTO qua Thymeleaf Model hoặc redirect/flash message. Không trả entity trực tiếp.

## 5. Controller endpoints (Spring MVC / Thymeleaf)

Các endpoint dưới đây là thiết kế đề xuất, dùng form POST và redirect sau khi ghi thành công (Post/Redirect/Get). `id` là `BIGINT`; màn hình lỗi validation render lại form với Form DTO và lỗi trường tương ứng.

| Method | Endpoint | Controller | Mục đích |
|---|---|---|---|
| GET | `/books` | `BookController` | Danh sách/tìm kiếm sách, hiển thị tồn kho |
| GET | `/books/new` | `BookController` | Mở form thêm sách |
| POST | `/books` | `BookController` | Validate và tạo sách |
| GET | `/books/{id}` | `BookController` | Chi tiết sách |
| GET | `/books/{id}/edit` | `BookController` | Mở form sửa |
| POST | `/books/{id}` | `BookController` | Validate và cập nhật sách |
| GET | `/members` | `MemberController` | Danh sách/tìm kiếm thành viên |
| GET | `/members/new` | `MemberController` | Mở form thành viên |
| POST | `/members` | `MemberController` | Tạo member; xử lý email trùng |
| GET | `/members/{id}` | `MemberController` | Chi tiết thành viên |
| GET | `/members/{id}/edit` | `MemberController` | Mở form sửa |
| POST | `/members/{id}` | `MemberController` | Cập nhật hồ sơ |
| POST | `/members/{id}/status` | `MemberController` | Khóa/mở khóa theo status hợp lệ |
| GET | `/categories` | `CategoryController` | Danh sách danh mục |
| GET | `/categories/new` | `CategoryController` | Mở form danh mục |
| POST | `/categories` | `CategoryController` | Tạo danh mục |
| GET | `/categories/{id}/edit` | `CategoryController` | Mở form sửa |
| POST | `/categories/{id}` | `CategoryController` | Cập nhật danh mục |
| GET | `/borrowings` | `BorrowingController` | Danh sách phiếu, lọc sách còn mượn/quá hạn |
| GET | `/borrowings/new` | `BorrowingController` | Mở form mượn |
| POST | `/borrowings` | `BorrowingController` | Xác thực và ghi nhận phiếu mượn nguyên tử |
| GET | `/borrowings/{id}` | `BorrowingController` | Chi tiết phiếu và tình trạng từng cuốn |
| GET | `/borrowings/{id}/returns/new` | `BorrowingController` | Mở form chọn các cuốn chưa trả |
| POST | `/borrowings/{id}/returns/preview` | `BorrowingController` | Tính và hiển thị preview tiền phạt; chưa ghi dữ liệu |
| POST | `/borrowings/{id}/returns` | `BorrowingController` | Xác nhận trả; tính lại phía server và ghi nhận nguyên tử |
| GET | `/reports/overdue` | `ReportController` | Báo cáo chi tiết sách quá hạn |

Endpoint preview không ghi dữ liệu và không được xem số tiền do client gửi là đáng tin cậy. Endpoint xác nhận phải kiểm tra lại chi tiết thuộc đúng phiếu, chưa trả và chưa bị cập nhật đồng thời; tính lại khoản phạt từ dữ liệu lưu và ngày trả hệ thống rồi commit. Response thành công chuyển hướng tới trang chi tiết phiếu. Route danh mục, dashboard, đăng nhập và quyền truy cập là các màn hình chưa được BA chốt; cần chốt phạm vi trước khi xây dựng.

Tất cả controller/service nhận dependency qua constructor (hoặc Lombok `@RequiredArgsConstructor` với trường `final`), tuyệt đối không Field Injection.

## 6. Sequence Diagram — ghi nhận mượn

```mermaid
sequenceDiagram
    actor Staff as Thủ thư
    participant View as Thymeleaf Form
    participant Controller as BorrowingController
    participant Service as BorrowingService
    participant MemberRepo as MemberRepository
    participant BookRepo as BookRepository
    participant BorrowingRepo as BorrowingRepository
    participant DetailRepo as BorrowingDetailRepository
    participant DB as MySQL 8 / InnoDB

    Staff->>View: Chọn thành viên, sách, hạn trả
    View->>Controller: POST /borrowings (BorrowingForm)
    Controller->>Controller: Validate Form DTO
    Controller->>Service: createBorrowing(form)
    Note over Service,DB: @Transactional(rollbackFor = Exception.class)
    Service->>MemberRepo: Tải thành viên và xác nhận ACTIVE
    MemberRepo->>DB: SELECT thành viên
    DB-->>MemberRepo: Member
    Service->>BookRepo: Tải sách được chọn
    BookRepo->>DB: SELECT Book (có version)
    DB-->>BookRepo: Books + availableQuantity
    Service->>Service: Kiểm tra hạn trả, item và tồn kho
    Service->>BorrowingRepo: Lưu phiếu mượn
    BorrowingRepo->>DB: INSERT borrowings
    Service->>DetailRepo: Lưu từng cuốn mượn
    DetailRepo->>DB: INSERT borrowing_details
    Service->>BookRepo: Giảm availableQuantity
    BookRepo->>DB: UPDATE books WHERE id=? AND version=?
    alt Tồn kho/version hợp lệ
        DB-->>BookRepo: UPDATE thành công, tăng version
        Service-->>Controller: Receipt DTO
        Controller-->>View: Redirect chi tiết + flash success
        View-->>Staff: Hiển thị phiếu mượn
    else Bị khóa, thiếu tồn kho hoặc optimistic conflict
        DB-->>Service: Lỗi kiểm tra / optimistic lock
        Service-->>Controller: Exception nghiệp vụ; transaction rollback
        Controller-->>View: Form và thông báo lỗi
        View-->>Staff: Yêu cầu làm mới/điều chỉnh
    end
```

### Giao dịch trả

`POST /borrowings/{id}/returns/preview` chỉ hiển thị kết quả tạm tính; `POST /borrowings/{id}/returns` dùng transaction để nạp và khóa lạc quan từng `BorrowingDetail`, xác nhận chưa trả, tính lại `max(0, daysBetween(dueDate, returnDate)) × 5.000 VNĐ` mỗi cuốn theo quy ước ngày đã chốt, cập nhật `returned_at`/snapshot phạt và tăng tồn kho sách tương ứng. Bất kỳ lỗi nào cũng rollback cả trạng thái trả lẫn tồn kho. Công thức ngày chỉ là khung; ngày lịch/ngày làm việc và việc tính ngày trả đúng hạn cần được xác nhận.

## 7. NFR Checklist

| Hạng mục | Thiết kế / tiêu chí |
|---|---|
| Atomicity mượn | `BorrowingService.createBorrowing` dùng `@Transactional(rollbackFor = Exception.class)`; phiếu, chi tiết và giảm tồn kho commit hoặc rollback đồng loạt. |
| Atomicity trả | Cập nhật chi tiết trả, số ngày/phạt và tăng tồn kho trong cùng transaction; không xử lý một phần khi có lỗi. |
| Optimistic Locking | JPA `@Version` ánh xạ `version`; book inventory update phải điều kiện theo version. Xử lý `OptimisticLockException` thành thông báo xung đột và yêu cầu tải lại. |
| Thành viên khóa | Đọc trạng thái mới nhất trong transaction; cân nhắc pessimistic lock ngắn trên member khi tạo phiếu để tránh status bị khóa đồng thời với giao dịch mượn. |
| Tính lặp lại an toàn khi trả | Cập nhật chỉ khi chi tiết còn `returned_at IS NULL`; version/điều kiện update bảo vệ trả trùng do request đồng thời. |
| Referential integrity | FK InnoDB, delete restrict để bảo toàn lịch sử; không xóa sách/thành viên/danh mục đang có giao dịch liên quan. |
| Validation | Bean Validation cho hình thức; service kiểm tra trạng thái/quantity/date; DB unique/check/FK là lớp bảo vệ cuối. |
| MySQL Indexing | Unique email/ISBN/category name; index tìm sách/member; composite member+borrowed date và borrowing+returned date; due date phục vụ báo cáo quá hạn. Xác nhận query plan khi có dữ liệu thật. |
| Consistency | `available_quantity` luôn trong khoảng `0..total_quantity`; thao tác tồn kho qua service, không ghi trực tiếp từ controller. |
| Encoding/currency | `utf8mb4`; tiền VND dùng `DECIMAL(12,0)`, không dùng floating point. |
| Migration | Flyway quản lý schema theo version; không chạy migration production trước khi được human review theo quy tắc repo. V1 là migration khởi tạo, không sửa sau khi đã áp dụng môi trường dùng chung. |

## 8. Open Questions và quyết định cần xác nhận

1. Thời hạn mượn mặc định và quy tắc gia hạn/số lượng tối đa là gì?
2. Phạt tính theo ngày lịch hay ngày làm việc? Trả vào đúng hạn có tiền phạt không, và ngày bắt đầu tính là ngày nào?
3. Có hỗ trợ trả nhiều đợt/một phần không? Thiết kế một detail cho mỗi cuốn hỗ trợ trả theo cuốn nhưng chưa lưu lịch sử nhiều lần trả một phần trên cùng một cuốn.
4. Thư viện quản lý số lượng tổng hay mã vạch riêng từng bản? Nếu cần mã vạch, nên bổ sung bảng `book_copies` và detail tham chiếu copy thay vì chỉ `books`.
5. Cần trạng thái thành viên nào khác `ACTIVE`/`LOCKED`, và ai có quyền khóa/mở?
6. Danh mục có bắt buộc với mọi sách không? ISBN có bắt buộc và một sách có nhiều ISBN/ấn bản không?
7. Có thu tiền phạt/miễn giảm hay chỉ ghi nhận khoản phạt? Nếu có, cần thiết kế bảng thanh toán/audit riêng; không thêm nghiệp vụ thu tiền vào transaction trả hiện tại.
8. Xác nhận bổ sung dependency JPA/Flyway và cấu hình datasource trong `pom.xml`/application config ở một thay đổi triển khai riêng.

## 9. Files affected / Reason / Risk / Tests required

- **Files affected:** `src/main/resources/db/migration/V1__init_schema.sql`, `.ai/01_repo_inventory.md`, `outputs/docs/SA/architecture_design.md`.
- **Reason:** Tạo schema Flyway MySQL 8.x, ghi dependency map/ package layout mục tiêu và lưu thiết kế kiến trúc dựa trên BA/inventory.
- **Risk:** Metadata sách/thành viên và cách lưu từng cuốn là lựa chọn thiết kế khi BA chưa chốt; thay đổi sau migration đã chạy cần migration mới. Repo chưa có dependency JPA/Flyway nên script chưa được ứng dụng tự chạy cho đến khi bổ sung dependency/config. Migration cần human review trước khi chạy.
- **Tests required:** Kiểm tra cú pháp migration bằng MySQL 8.x/Flyway sau khi dependencies và DB test được cấu hình; kiểm tra constraints/unique/FK, update tồn kho có `@Version`, và rollback toàn transaction trong integration tests khi triển khai các service. Không áp dụng test mã Java vì task hiện chỉ thêm schema và tài liệu.
