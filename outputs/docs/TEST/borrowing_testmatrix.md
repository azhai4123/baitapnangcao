# Test Matrix — Mượn/Trả sách

## 1. Căn cứ và quy ước

- Căn cứ: `outputs/docs/BA/UC_borrowing_spec.md`, `outputs/docs/SA/architecture_design.md` và `prompts/tester_agent.md`.
- Module summary `.ai/03_module_summaries/borrowing.md` hiện rỗng; các test dưới đây chỉ dựa trên BA/SA.
- Trạng thái bị khóa trong BA/SA là `LOCKED`; prompt Tester gọi trạng thái này là `BLOCKED`. Test dùng `LOCKED` (tương ứng `BLOCKED`).
- Một `borrowing_details` đại diện một cuốn. Dữ liệu fixture dùng ngày cụ thể để kết quả kỳ vọng có thể kiểm tra.
- Giả định kiểm thử ngày: hạn trả 2026-10-10, trả ngày 2026-10-10 là đúng hạn và 2026-10-11 là trễ một ngày. BA yêu cầu xác nhận quy ước ngày lịch/ngày trả đúng hạn; các test tính phạt đánh dấu cần chốt quy ước.
- `Automation Candidate = Yes` khi có thể ổn định hóa bằng unit, integration, concurrency hoặc UI automation test. Kiểm thử race condition yêu cầu MySQL/InnoDB thật hoặc môi trường tương đương, không chỉ mock repository.

## 2. Dữ liệu kiểm thử nền

| Fixture | Dữ liệu |
|---|---|
| Thành viên M-A | `ACTIVE`, email duy nhất |
| Thành viên M-L | `LOCKED`, email duy nhất |
| Sách B-0 | `availableQuantity=0`, `totalQuantity=1` |
| Sách B-1 | `availableQuantity=1`, `totalQuantity=1` |
| Sách B-3 | `availableQuantity=3`, `totalQuantity=3` |
| Sách B-4 | `availableQuantity=4`, `totalQuantity=4` |
| Phiếu L-DUE | Một detail chưa trả, hạn 2026-10-10 |
| Phiếu L-MIX | Hai detail chưa trả: một hạn 2026-10-10 và một hạn 2026-10-12 |
| Phiếu L-RETURNED | Detail đã trả, `returned_at` khác null |

Mỗi test phải ghi nhận snapshot trước/sau của `borrowings`, `borrowing_details`, `books.available_quantity` và `version` khi có liên quan. Dọn fixture hoặc dùng transaction/test database để tránh ảnh hưởng giữa các case.

## 3. Test Matrix

| Test Case ID | Feature | Scenario | Preconditions | Test Data | Steps | Expected Result | Priority | Type (Positive/Negative/Boundary) | Automation Candidate (Yes/No) |
|---|---|---|---|---|---|---|---|---|---|
| BOR-P-001 | Mượn — happy path | Mượn một cuốn khi thành viên ACTIVE và sách còn hàng | M-A tồn tại; B-1 tồn tại; form hợp lệ | M-A, B-1 × 1, hạn hợp lệ | 1. Mở form mượn.<br>2. Chọn M-A và B-1 × 1.<br>3. Nhập hạn trả.<br>4. Xác nhận. | Tạo một borrowing và một detail; `availableQuantity` B-1 giảm 1→0; chuyển tới chi tiết/hiển thị thành công. | P0 | Positive | Yes |
| BOR-P-002 | Mượn — nhiều dòng | Mượn nhiều đầu sách trong cùng phiếu | M-A ACTIVE; B-3 và sách khác còn đủ tồn | B-3 × 2, B-4 × 1 | 1. Chọn M-A.<br>2. Thêm hai sách với số lượng.<br>3. Xác nhận. | Một phiếu chứa đúng số detail/cuốn theo thiết kế; tồn từng sách giảm đúng số lượng, không trộn book_id. | P0 | Positive | Yes |
| BOR-B-001 | Mượn — boundary tồn kho | Mượn số lượng đúng bằng tồn kho khả dụng | M-A ACTIVE; B-3 còn 3 | B-3 × 3 | 1. Chọn M-A, B-3 × 3.<br>2. Xác nhận. | Thành công; tồn kho về đúng 0, không âm; phiếu/detail khớp 3 cuốn. | P0 | Boundary | Yes |
| BOR-N-001 | Mượn — thành viên khóa | Thành viên `LOCKED` không được mượn (`BLOCKED` theo prompt Tester) | M-L tồn tại; B-1 còn hàng | M-L, B-1 × 1 | 1. Chọn M-L.<br>2. Thử xác nhận mượn. | UI báo thành viên bị khóa và không cho xác nhận; backend từ chối nếu gửi request trực tiếp; không có borrowing/detail mới, tồn kho không đổi. | P0 | Negative | Yes |
| BOR-N-002 | Mượn — sách hết hàng | Từ chối mượn sách có tồn khả dụng 0 | M-A ACTIVE; B-0 tồn tại | M-A, B-0 × 1 | 1. Chọn M-A và tìm B-0.<br>2. Thử thêm/xác nhận. | Sách không thể được thêm hoặc submit bị từ chối với tồn mới nhất; không tạo dữ liệu và không làm tồn kho âm. | P0 | Negative | Yes |
| BOR-N-003 | Mượn — vượt tồn | Số lượng yêu cầu lớn hơn khả dụng | M-A ACTIVE; B-1 còn 1 | B-1 × 2 | 1. Nhập/chọn B-1 × 2.<br>2. Xác nhận. | Báo không đủ tồn; không ghi nhận một phần, không tạo phiếu/detail, tồn vẫn 1. | P0 | Negative | Yes |
| BOR-B-002 | Mượn — boundary số lượng | Số lượng bằng 1, giá trị nhỏ nhất hợp lệ | M-A ACTIVE; B-1 còn hàng | B-1 × 1 | 1. Nhập quantity 1.<br>2. Xác nhận. | Mượn thành công và tồn giảm đúng 1. | P1 | Boundary | Yes |
| BOR-N-004 | Mượn UI — quantity bằng 0 | Từ chối số lượng không dương ở form | Form mượn đang mở | quantity = 0 | 1. Chọn thành viên và sách.<br>2. Nhập 0.<br>3. Submit. | Validation báo số lượng phải là số nguyên dương; không gọi ghi nhận thành công, dữ liệu/tồn kho không đổi. | P0 | Negative | Yes |
| BOR-N-005 | Mượn UI — quantity âm | Từ chối số lượng âm | Form mượn đang mở | quantity = -1 | 1. Nhập -1.<br>2. Submit. | Hiển thị lỗi tại dòng sách; không tạo phiếu và không tăng tồn kho do giá trị âm. | P0 | Negative | Yes |
| BOR-N-006 | Mượn UI — quantity không nguyên | Từ chối chuỗi/số thập phân không phải số nguyên dương | Form mượn đang mở | `abc`, `1.5` | 1. Nhập từng giá trị.<br>2. Submit form. | Form binding/validation từ chối; hiển thị lỗi có thể hiểu được; không có thay đổi DB. | P1 | Negative | Yes |
| BOR-N-007 | Mượn UI — thiếu thành viên | Không submit khi chưa chọn một thành viên hợp lệ | Form mượn đang mở | `memberId` null/không chọn kết quả tra cứu | 1. Chọn sách hợp lệ nhưng bỏ trống thành viên.<br>2. Submit. | Lỗi bắt buộc tại trường thành viên; không tạo phiếu/detail; tồn kho không đổi. | P1 | Negative | Yes |
| BOR-N-008 | Mượn UI — phiếu rỗng | Không tạo borrowing không có sách | M-A ACTIVE; không có item trong form | items rỗng | 1. Chọn M-A.<br>2. Không thêm sách.<br>3. Submit. | UI/backend từ chối phiếu rỗng; không có borrowing được lưu. | P1 | Negative | Yes |
| BOR-N-009 | Mượn — thành viên không tồn tại | Gửi memberId không tồn tại | Có thể POST trực tiếp endpoint | memberId không tồn tại | 1. Gửi POST `/borrowings` với id không tồn tại và item hợp lệ. | Báo không tìm thấy/tham chiếu thành viên không hợp lệ; không tạo borrowing/detail; tồn kho không đổi. | P1 | Negative | Yes |
| BOR-N-010 | Mượn — sách không tồn tại | Gửi bookId không tồn tại | M-A ACTIVE | bookId không tồn tại, quantity 1 | 1. Gửi POST `/borrowings` với item giả mạo. | Từ chối item không hợp lệ; transaction không ghi borrowing/detail hoặc thay đổi tồn kho của sách khác. | P1 | Negative | Yes |
| BOR-N-011 | Mượn UI — hạn trả thiếu/không hợp lệ | Từ chối ngày hạn trả không hợp lệ | Form mượn đang mở | dueDate thiếu hoặc chuỗi sai định dạng | 1. Nhập member/item hợp lệ.<br>2. Bỏ trống hoặc nhập sai định dạng dueDate.<br>3. Submit. | Binding/validation báo lỗi; không ghi borrowing hoặc cập nhật tồn kho. | P1 | Negative | Yes |
| BOR-B-003 | Mượn — biên ngày hạn | Hạn trả bằng ngày mượn hoặc trước ngày mượn | Chính sách ngày/hạn đã được PO xác nhận | borrowedAt = dueDate; sau đó dueDate < ngày mượn | 1. Submit mỗi bộ dữ liệu. | Kỳ vọng phải được chốt: hệ thống nên từ chối dueDate trước/ngày mượn nếu nghiệp vụ yêu cầu hạn tương lai; không áp dụng tiêu chí cứng trước khi PO xác nhận. Ghi nhận quyết định trong test result. | P2 | Boundary | No |
| BOR-N-012 | Mượn UI — ISBN không hợp lệ | Kiểm tra ISBN sai định dạng khi tra cứu/chọn sách | Quy tắc ISBN/chuẩn ISBN-10/13 chưa được BA chốt | ISBN sai định dạng theo chuẩn được chọn | 1. Nhập ISBN không hợp lệ vào trường tra cứu hoặc form sách.<br>2. Submit/tra cứu. | **Blocked pending requirement:** BA/SA chưa chốt định dạng ISBN hoặc yêu cầu checksum; sau khi chốt, kiểm tra lỗi tại form và không lưu sách sai. Không mặc định mọi ISBN phải ISBN-10/13 khi chưa xác nhận. | P2 | Negative | No |
| BOR-N-013 | Mượn — duplicate book item | Cùng book_id xuất hiện ở nhiều item | M-A ACTIVE; B-4 còn 4 | B-4 × 2 ở dòng 1, B-4 × 3 ở dòng 2 | 1. Submit payload có dòng trùng vượt tổng tồn. | Tổng nhu cầu theo book_id được kiểm tra; không vượt tồn; toàn phiếu bị từ chối và rollback. Nếu tổng ≤ tồn thì hành vi gộp/chặn cần thống nhất UI. | P1 | Negative | Yes |
| BOR-N-014 | Mượn — lỗi persistence/transaction | Phát sinh lỗi sau khi đã insert borrowing/detail nhưng trước commit tồn kho | Có thể inject exception tại bước cập nhật Book trong test tích hợp | M-A ACTIVE, B-1 × 1; repository Book ném exception | 1. Submit phiếu hợp lệ.<br>2. Inject exception khi update tồn.<br>3. Đọc lại DB sau rollback. | Không còn borrowing/detail dở dang; tồn kho/version không đổi; lỗi được surface, không phản hồi thành công. | P0 | Negative | Yes |
| BOR-C-001 | Mượn — Optimistic Locking | Dữ liệu Book bị cập nhật sau khi form đọc tồn nhưng trước khi commit | M-A ACTIVE; B-1 ban đầu 1; hai transaction/test barrier | Hai request cùng đọc `version=v`, mỗi request mượn 1 | 1. Cho cả hai transaction đọc B-1 và cùng version.<br>2. Đồng bộ barrier.<br>3. Cùng thực hiện update version. | Một request commit; request stale nhận optimistic conflict; không ghi phiếu/detail dở dang; tồn chính xác 0 và version tăng đúng một lần. | P0 | Negative | Yes |
| BOR-C-002 | Mượn — hai nhân viên tranh cuốn cuối | Hai request đồng thời mượn cùng cuốn cuối | M-A/M-B ACTIVE; B-1 còn đúng 1; MySQL/InnoDB | Request A và B cùng mượn B-1 × 1 | 1. Khởi chạy hai request đồng thời với barrier.<br>2. Chờ cả hai hoàn tất.<br>3. Đối chiếu DB. | Chính xác một request thành công và một request bị từ chối do hết tồn/optimistic conflict; chỉ một borrowing/detail hợp lệ; `available_quantity=0`, không âm; thất bại không để bản ghi dở dang. | P0 | Negative | Yes |
| BOR-C-003 | Mượn — retry sau xung đột | Nhân viên tải lại dữ liệu sau optimistic conflict | BOR-C-001 đã làm một request thất bại; tồn hiện tại 0 | Submit lại quantity 1 từ dữ liệu mới | 1. Refresh form/chi tiết.<br>2. Submit lại sách đã hết. | Request mới bị từ chối do hết hàng; không tạo giao dịch thứ hai hoặc giảm tồn lần nữa. | P1 | Negative | Yes |
| BOR-P-003 | Trả — đúng hạn | Trả sách vào ngày đến hạn | L-DUE chưa trả; B-1 available 0 | returnDate = dueDate = 2026-10-10 | 1. Mở phiếu.<br>2. Chọn detail chưa trả.<br>3. Preview rồi xác nhận trả.<br>4. Kiểm tra detail/book. | `returned_at` được ghi; overdueDays=0, fineAmount=0 theo quy ước ngày đã chốt; available tăng 0→1 đúng một lần. | P0 | Boundary | Yes |
| BOR-B-004 | Trả — quá hạn một ngày | Trả chính xác một ngày sau hạn | L-DUE chưa trả; fine rate 5,000 VND/day/copy | dueDate 2026-10-10; returnDate 2026-10-11 | 1. Preview khoản phạt.<br>2. Xác nhận trả.<br>3. Kiểm tra snapshot phạt và tồn. | Nếu quy ước ngày lịch được chốt như giả định: overdueDays=1, fineAmount=5.000 VNĐ/cuốn; detail đã trả và tồn tăng đúng một. | P0 | Boundary | Yes |
| BOR-P-004 | Trả — quá hạn nhiều ngày | Tính phạt theo số ngày trễ và từng cuốn | Hai detail chưa trả, cùng hạn; số lượng copy độc lập | Trả trễ 3 ngày, 2 cuốn | 1. Preview.<br>2. Xác nhận.<br>3. So tiền từng detail và tổng. | Mỗi cuốn 15.000 VNĐ; tổng 30.000 VNĐ; không dùng floating point; tồn tăng đúng số cuốn trả. | P1 | Positive | Yes |
| BOR-P-005 | Trả — nhiều detail với hạn khác nhau | Tính riêng từng cuốn có hạn trả khác nhau | L-MIX chưa trả, detail A hạn 10/10 và B hạn 10/12 | Return 10/11 | 1. Chọn cả hai detail.<br>2. Preview và xác nhận trả. | Detail A tính trễ 1 ngày/5.000; detail B đúng hạn/0; tổng 5.000; cả hai detail đánh dấu trả và tồn tương ứng tăng. | P1 | Positive | Yes |
| BOR-N-015 | Trả — phiếu không tồn tại | Tra cứu/xác nhận với borrowing id không tồn tại | Không có phiếu tương ứng | borrowingId không tồn tại | 1. GET detail hoặc POST return cho id không tồn tại. | Hiển thị not found/không có kết quả; không thay đổi bất cứ tồn kho hay dữ liệu giao dịch nào. | P1 | Negative | Yes |
| BOR-N-016 | Trả — detail đã trả | Ngăn trả lặp một cuốn đã hoàn tất | L-RETURNED đã có returned_at | Gửi lại detail id đã trả | 1. Submit return lần nữa cho detail đã trả. | Từ chối do trạng thái đã trả; fine/returned_at không bị ghi đè; tồn kho không tăng lần hai. | P0 | Negative | Yes |
| BOR-N-017 | Trả — detail không thuộc phiếu | Chặn ID detail của phiếu khác bị gửi cùng borrowing id | Có hai phiếu L1, L2 và detail thuộc L2 chưa trả | URL borrowingId=L1, detailId từ L2 | 1. POST return với tổ hợp ID giả mạo. | Từ chối; không trả detail ngoài phiếu; không cập nhật tồn kho hoặc phí cho bất kỳ detail nào. | P0 | Negative | Yes |
| BOR-N-018 | Trả UI — không chọn sách | Không xác nhận trả với danh sách detail rỗng | Phiếu có ít nhất một detail đang mượn | `borrowingDetailIds=[]` | 1. Mở form trả.<br>2. Không chọn cuốn nào.<br>3. Submit preview/confirm. | Validation yêu cầu chọn ít nhất một cuốn; không cập nhật detail hoặc tồn. | P1 | Negative | Yes |
| BOR-N-019 | Trả — ID detail trùng lặp | Chặn cùng một detail lặp trong payload | Detail chưa trả | `[detailId, detailId]` | 1. POST payload có ID lặp. | Không được tăng tồn hoặc tính phạt hai lần; payload bị từ chối hoặc chuẩn hóa an toàn theo quyết định triển khai; lưu đúng một lần tối đa. | P1 | Negative | Yes |
| BOR-B-005 | Trả — trả một phần theo cuốn | Trả một detail trong phiếu có nhiều cuốn | Borrowing có hai detail chưa trả cùng book_id | Trả detail A, giữ detail B | 1. Chọn detail A.<br>2. Xác nhận.<br>3. Mở lại chi tiết phiếu. | A đã trả và tồn tăng 1; B vẫn chưa trả và không tăng tồn; phiếu vẫn có số cuốn còn mượn. Cần xác nhận UX trả một phần với PO. | P1 | Boundary | Yes |
| BOR-N-020 | Trả UI — request sửa tiền/ngày phía client | Không tin cậy fine, overdueDays hoặc returnDate do client gửi | Detail chưa trả; endpoint xác nhận nhận payload | Thêm `fineAmount=0`, `overdueDays=0`, ngày trả giả | 1. Gửi POST xác nhận với các trường giả mạo. | Server tính lại từ dữ liệu lưu và ngày trả hệ thống; bỏ qua/từ chối trường không được bind; khoản lưu không thể bị giảm bằng payload. | P0 | Negative | Yes |
| BOR-N-021 | Trả — số lượng vượt số còn mượn | Chặn yêu cầu trả lớn hơn lượng chưa trả | Phiếu có một detail/copy chưa trả | Gửi quantity > outstanding hoặc lặp detail để biểu diễn vượt | 1. Submit return vượt số lượng có thể trả. | Từ chối; không tăng tồn/phí; dữ liệu giao dịch giữ nguyên. Với model mỗi detail là một cuốn, quantity không thuộc ReturnForm nên ID hợp lệ phải được kiểm tra theo phiếu và trạng thái. | P0 | Negative | Yes |
| BOR-N-022 | Trả — lỗi persistence/transaction | Exception khi cập nhật tồn sau khi ghi trạng thái returned/fine | Có thể inject exception sau update detail | Detail chưa trả; Book update ném exception | 1. Submit return hợp lệ.<br>2. Inject lỗi ở cập nhật tồn.<br>3. Đọc lại DB. | Transaction rollback; detail vẫn chưa trả, fine snapshot không lưu, tồn không đổi; không có thông báo thành công. | P0 | Negative | Yes |
| BOR-C-004 | Trả — hai request trả cùng detail | Hai request đồng thời xử lý một cuốn chưa trả | Detail chưa trả; book tồn 0 | Hai request cùng detailId | 1. Barrier cho cả hai request đọc trạng thái chưa trả.<br>2. Cùng commit return.<br>3. Kiểm tra DB. | Chỉ một request thành công; request kia xung đột/đã xử lý; tồn tăng đúng 1, không double return/fine. | P0 | Negative | Yes |
| BOR-B-006 | Trả — tồn sau mượn/trả | Kiểm tra invariant sau nhiều vòng giao dịch | Book totalQuantity cố định, ban đầu available=total | Mượn k cuốn rồi trả đủ k cuốn | 1. Mượn quantity k.<br>2. Xác nhận tồn giảm k.<br>3. Trả đủ k detail.<br>4. Đọc DB. | Sau mượn available=total-k; sau trả available trở về total; trong mọi thời điểm `0 <= available <= total`. | P1 | Boundary | Yes |
| BOR-N-023 | UI — lỗi nghiệp vụ giữ ngữ cảnh form | Form mượn lỗi tồn kho vẫn hiển thị lỗi hữu ích | Form đã nhập thành viên/item; tồn vừa bị request khác lấy | B-1 đã đổi available 1→0 trước submit | 1. Submit stale form.<br>2. Quan sát trang lỗi. | Hiển thị lỗi cạnh sách/thông báo cần tải lại; dữ liệu khác trong form được giữ nếu an toàn; không báo thành công/không tạo partial record. | P1 | Negative | Yes |
| BOR-P-006 | Báo cáo — sách quá hạn | Hiển thị các detail chưa trả đã quá hạn | Có detail quá hạn chưa trả, detail đúng hạn và detail đã trả | dueDate < today cho một detail | 1. Mở `/reports/overdue`.<br>2. Đối chiếu danh sách với DB. | Chỉ các chi tiết còn chưa trả và quá hạn được đưa vào danh sách; chi tiết đã trả không xuất hiện; ngày/quy tắc được áp dụng nhất quán. | P1 | Positive | Yes |

## 4. Quy tắc kiểm tra transaction và concurrency

1. Với mọi case từ chối trước commit, so sánh snapshot để xác nhận không có borrowing/detail dở dang và `available_quantity` không đổi.
2. Với lỗi phát sinh giữa transaction, kiểm tra rollback tất cả bảng liên quan, bao gồm `version`; không chỉ kiểm tra HTTP response.
3. Test tranh chấp chạy bằng hai transaction/connection độc lập trên MySQL/InnoDB, dùng barrier để đảm bảo cả hai request cùng đọc trạng thái ban đầu trước khi cập nhật. Không dựa vào test tuần tự để kết luận xử lý đồng thời đúng.
4. Race test kỳ vọng một kết quả thắng, không bắt buộc loại exception cụ thể nếu lớp service chuyển optimistic-lock exception thành lỗi nghiệp vụ; hợp đồng bắt buộc là không bán/mượn vượt tồn và không có partial write.
5. Sau khi optimistic conflict, lần submit lại phải tải dữ liệu tồn kho mới; không tự retry nguyên transaction stale.

## 5. Open Questions / tiêu chí cần chốt trước khi test sign-off

- Quy ước tính ngày trễ, timezone, ngày trả đúng hạn, ngày lịch hay ngày làm việc và công thức chuẩn khi trả sau hạn một phần ngày.
- Hạn trả phải sau ngày mượn hay cho phép cùng ngày; có được lùi ngày mượn/ngày trả không.
- Có hỗ trợ trả từng cuốn trong một dòng quantity nhiều hơn 1 hay mỗi bản luôn có detail riêng; migration SA chọn một detail mỗi cuốn.
- Chuẩn/format ISBN và việc validate checksum chưa được BA xác nhận; BOR-N-012 là blocked, không dùng để đánh giá release đến khi có tiêu chí.
- Duplicate book item được tự gộp hay bị chặn khi tổng quantity hợp lệ.
- Cách hiển thị lỗi xung đột và giới hạn retry.
- Thu/miễn/điều chỉnh phạt không thuộc phạm vi đã chốt; test không được kỳ vọng ghi nhận thanh toán.

## 6. Phạm vi và rủi ro kiểm thử

- **Files affected:** `outputs/docs/TEST/borrowing_testmatrix.md`.
- **Reason:** Lập test matrix cho luồng mượn/trả theo BA/SA và prompt Tester Agent.
- **Risk:** Một số tiêu chí biên phụ thuộc chính sách nghiệp vụ chưa chốt; các case được đánh dấu giả định/blocked cần Product Owner quyết định trước khi dùng làm acceptance criteria.
- **Tests required:** Đây là tài liệu test design, chưa có code test runnable. Các case P0 transaction/concurrency cần integration test với DB; các case Form Binding cần MVC/UI test khi Controller và DTO được triển khai.
