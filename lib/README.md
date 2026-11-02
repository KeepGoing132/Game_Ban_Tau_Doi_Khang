# SQLite JDBC

`sqlite-jdbc-3.53.4.0.jar` được đóng gói sẵn để build/run offline. Bản này hỗ trợ API Java 8 và chứa native library cho Windows, macOS Intel/Apple Silicon và Linux. Đã kiểm thử trên Windows/JDK 25; chưa chạy trực tiếp trên macOS.

Nguồn, checksum và license: [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Khi gửi bản chạy, gửi cả `dist/lib/` bên cạnh `dist/battleship-server.jar`. Manifest server tự tìm thư viện; client JAR không cần JDBC.
