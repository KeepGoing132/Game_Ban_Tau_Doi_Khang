# Nguồn tham khảo và thư viện

## StarNet

Repository: https://github.com/androoAGI/starnet

Đã clone nông, chọn phần giao diện tại `references/starnet-reference`, commit `54899e5ff3f2b002b457757916b3977818db6fc1`. Đã đọc CSS, README, LICENSE, NOTICE và ảnh `docs/industrial-textures/live-command-deck.jpg`. Phong cách bảng điều khiển được tham khảo; giao diện, hình tàu, animation và âm thanh Battleship được viết bằng Java/Java2D. Không đóng gói code/runtime, font hoặc sprite StarNet vào JAR của game. Repo tham khảo được gitignore.

StarNet có MIT License, copyright (c) 2026 Andrew Sims. Các tài nguyên bên thứ ba trong repo có điều khoản riêng tại NOTICE.md của nguồn. Bản LICENSE gốc được giữ trong clone.

## MySQL Connector/J

Nguồn: https://dev.mysql.com/doc/connector-j/en/ và https://github.com/mysql/mysql-connector-j

Artifact: `com.mysql:mysql-connector-j:8.4.0`, tải từ Maven Central. Đây là driver JDBC dùng để nói chuyện với MySQL 8; server MySQL không được đóng gói kèm project, người chạy phải tự cài (xem mục **Chuẩn bị MySQL** trong README).

SHA-256 của `lib/mysql-connector-j-8.4.0.jar`, đã đối chiếu checksum Maven Central:

```text
d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5
```

License: **GNU General Public License v2 kèm Universal FOSS Exception v1.0** (POM của artifact ghi rõ tên này). Bản GPLv2 được kèm ở `lib/mysql-connector-j-LICENSE.txt`; giữ JAR và tệp license khi phân phối. Lưu ý giấy phép này khác SQLite (public domain) mà bản trước đây dùng, vì vậy nếu project được phát hành lại như sản phẩm nguồn mở cần đọc kỹ GPLv2.

Âm thanh của game được tổng hợp bằng mã nguồn tại máy, không dùng bản thu có bản quyền bên ngoài.
