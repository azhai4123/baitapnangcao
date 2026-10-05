# Mô tả ngắn gọn của dự án 
# Project Brief - Library Management System

- **Mục tiêu:** Xây dựng ứng dụng Web quản lý thư viện nội bộ (Sách, Thành viên, Mượn/Trả sách, Báo cáo quá hạn).
- **Tech Stack:** Java 17, Spring Boot 3.x, Spring Data JPA, MySQL, Flyway, Thymeleaf, Bootstrap 5.
- **Yêu cầu cốt lõi:**
    1. Xử lý tồn kho chính xác (`availableQuantity`).
    2. Phạt 5.000 VNĐ/ngày cho mỗi cuốn sách trả quá hạn.
    3. Validate dữ liệu chặt chẽ (Email không trùng, Thành viên bị khóa không được mượn).
    4. Đảm bảo Transaction và tính nhất quán dữ liệu khi mượn/trả.