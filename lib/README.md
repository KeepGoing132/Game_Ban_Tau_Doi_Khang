# MySQL Connector/J

`mysql-connector-j-8.4.0.jar` được đóng gói sẵn để build/run offline. Đây là driver JDBC chính thức của MySQL, hỗ trợ API Java 8 (project biên dịch `--release 8`) và làm việc với MySQL 8.0/8.4 (đã kiểm thử với MySQL 8.0.44 Community).

Server cần một MySQL đang chạy; xem mục **Chuẩn bị MySQL** trong [README.md](../README.md) để khai báo `host/port/user/password` qua `db.properties` (mặc định `127.0.0.1:3306`, user `root`). Không cần chạy script tạo bảng: server tự tạo schema và bảng khi khởi động lần đầu.

Giấy phép: **GNU General Public License v2 kèm Universal FOSS Exception v1.0** (trước đây SQLite là public domain). Bản GPLv2 được kèm ở `mysql-connector-j-LICENSE.txt`; ngoại lệ FOSS nêu trong POM của artifact trên Maven Central. Giữ JAR và tệp giấy phép khi phân phối.

Nguồn, checksum và license: [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Khi gửi bản chạy, gửi cả `dist/lib/` bên cạnh `dist/battleship-server.jar`. Manifest server tự tìm thư viện; client JAR không cần JDBC.
