# Nguồn tham khảo và thư viện

## StarNet

Repository: https://github.com/androoAGI/starnet

Đã clone nông, chọn phần giao diện tại `references/starnet-reference`, commit `54899e5ff3f2b002b457757916b3977818db6fc1`. Đã đọc CSS, README, LICENSE, NOTICE và ảnh `docs/industrial-textures/live-command-deck.jpg`. Phong cách bảng điều khiển được tham khảo; giao diện, hình tàu, animation và âm thanh Battleship được viết bằng Java/Java2D. Không đóng gói code/runtime, font hoặc sprite StarNet vào JAR của game. Repo tham khảo được gitignore.

StarNet có MIT License, copyright (c) 2026 Andrew Sims. Các tài nguyên bên thứ ba trong repo có điều khoản riêng tại NOTICE.md của nguồn. Bản LICENSE gốc được giữ trong clone.

## SQLite JDBC

Nguồn: https://github.com/xerial/sqlite-jdbc

Artifact: `org.xerial:sqlite-jdbc:3.53.4.0`, tải từ Maven Central. Thư viện chứa native SQLite cho Windows/macOS/Linux; không cần cài server database.

SHA-256 của `lib/sqlite-jdbc-3.53.4.0.jar`, đã đối chiếu checksum Maven Central:

```text
bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb
```

License Apache 2.0 và BSD của mã gốc được trích nguyên văn từ JAR vào `lib/META-INF-maven-org.xerial-sqlite-jdbc-LICENSE` và `lib/META-INF-maven-org.xerial-sqlite-jdbc-LICENSE.zentus`. Giữ JAR và các license khi phân phối. Âm thanh của game được tổng hợp bằng mã nguồn tại máy, không dùng bản thu có bản quyền bên ngoài.
