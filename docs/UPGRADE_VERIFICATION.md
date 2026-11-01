# Kiểm chứng bản nâng cấp — 12/10/2026

## Nâng cấp cảnh biển trong trận (màn hình bố trí tàu và chiến đấu)

- Thêm `OceanScene.java` (cảnh biển + hiệu ứng vào trận), `OceanPanel.java` (đồng hồ biển chung, bật/tắt sóng), `SeaDecor.java` (la bàn, sonar, thước độ sâu, dải chân trời) và `WaveProgress.java` (ống nước cho thanh thời gian). Bàn cờ vẽ lại thành mặt nước trong suốt có caustics, sonar và vệt nước bắn. Tất cả bằng Java2D, không thêm thư viện hay tài nguyên ngoài.
- Hiệu ứng vào trận: sóng quét qua màn hình, tấm biển RA KHƠI/VÀO TRẬN, la bàn, tàu khu trục chạy ngang, vòng sonar. Không chặn thao tác: bàn cờ vẫn bấm và nút Khai hỏa vẫn gửi được trong lúc hiệu ứng đang chạy.
- Hiệu ứng bắn: cột nước bắn và vòng bọt khi trượt, cầu lửa + mũ bọt + khói khi trúng, sủi bọt khi chìm, biển ĐÁNH CHÌM có đường sóng.
- Âm thanh: thêm `SPLASH`, `WAVE`, `PING` và nền trận đấu là tiếng sóng biển 4 giây lặp liền mạch; `VisualFeatureTests` vẫn xác nhận 12 âm có dữ liệu PCM khác nhau.
- Tối ưu: khung hình trung bình 63 ms → 15 ms (cache lớp tĩnh, ảnh viên gạch cho ô nước, bỏ `setCursor` và lớp UI của từng ô, sprite cho caustics/tia nắng, hoãn dựng cache khi cửa sổ đang đổi kích thước) và đồng hồ biển tự giảm tốc khi máy bận hoặc cửa sổ không được chọn.
- `run_tests.ps1`: PASS toàn bộ protocol/TCP, race, storage fault, migration, penalty, bot và ChatTests.
- `run_tests.ps1 -Gui -RealTimer`: PASS `SwingSmoke`, `VisualFeatureTests`, `ChatSwingTests` và `OceanVisualTests` (hiệu ứng tự kết thúc, không chặn bắn, bật/tắt sóng dừng–chạy lại đồng hồ, đồng hồ dừng khi rời màn hình, ảnh + GIF render lại). Ảnh ở `docs/ui/sea-intro*.png`, `docs/ui/battle-splash.png`; âm thanh mới ở `docs/audio/`.
- Lưu ý kiểm thử: chế độ `-RealTimer` (timeout lượt 15 giây) là chế độ đã kiểm chứng. Chế độ test nhanh `-Dbattleship.turn_ms=700` trên máy này bị quá hạn lượt ngay sau khi vào trận (thời gian chụp/ghi ảnh PNG chiếm phần lớn hạn lượt) — lỗi này cũng xảy ra với mã trước khi nâng cấp.

## Bổ sung chat riêng

- Chat ở sảnh và trong trận, tự chọn đối thủ, thu gọn/mở lại, báo tin chưa đọc, giữ bản nháp, Enter/Shift+Enter và tải lịch sử theo trang. Overlay đã kiểm tra ở cửa sổ tiêu chuẩn và tối thiểu; không đổi kích thước bản đồ.
- `chat_messages` (thời điểm đó còn dùng SQLite): người gửi/nhận, nội dung Unicode, thời gian, khóa chống gửi trùng. Gửi offline và lịch sử qua restart; chỉ hai tài khoản liên quan đọc được. Xóa tài khoản ẩn tên trong lịch sử, tài khoản đăng ký lại không được thừa hưởng tin cũ.
- `run_tests.ps1`: PASS toàn bộ kiểm thử cũ và `ChatTests` (xác thực, giả danh, riêng tư, lỗi ghi SQL không phát tin, retry/idempotency, phân trang/giới hạn frame, offline/restart, xóa tài khoản, chat trong trận, hạn chế gửi dồn).
- `run_tests.ps1 -Gui -RealTimer`: PASS toàn bộ Swing/game/bot và `ChatSwingTests` (hai client thật, gửi/nhận bằng Enter, báo chưa đọc, giữ bản nháp khi lỗi SQL rồi gửi lại, thu gọn/đổi màn hình, resize, lịch sử và đổi tài khoản). Ảnh ở `docs/ui/chat-*.png`.
- Build `compile.ps1` PASS với Java 8 API. Đã khởi động JAR server mới trên cổng 8888 và client mới. Database thật `integrity_check=ok`, giữ nguyên toàn bộ 6 tài khoản và 1 kết quả trận (so sánh SHA-256 dữ liệu trước/sau); bảng chat sẵn sàng, chưa có tin người dùng. Backup trước nâng cấp ở thư mục TEMP: `battleship-before-chat-20261012-110909.db`.
- Kiểm thử hai client trên một máy qua TCP; chưa kiểm thử bản chat trên hai máy vật lý hoặc macOS. Số chưa đọc là trạng thái phiên, lịch sử nội dung lưu lâu dài trong SQL trên server.

## Đã triển khai

- Clone tham khảo giao diện StarNet: `references/starnet-reference`, commit `54899e5ff3f2b002b457757916b3977818db6fc1`; dùng hướng bảng điều khiển, triển khai riêng bằng Java2D.
- Màu xanh biển/tím/ngọc lam/cam, lược bỏ chữ trang trí nhỏ ở login/placement/battle.
- Bản đồ chính lớn, chuyển sang bản đồ đang bị bắn theo lượt server; tùy chọn hiện bản đồ nhỏ được lưu.
- Bom bay vòng cung, vệt lửa, hiệu ứng đánh chìm; ô kết quả được hiển thị lúc bom chạm mục tiêu. Timer hiển thị không chặn mạng/EDT.
- Âm nền và chín âm thao tác/sự kiện được tổng hợp; bật/tắt và lưu lựa chọn.
- accounts/matches/metadata (thời điểm đó còn dùng SQLite), nhập dữ liệu JSON cũ, mật khẩu băm có salt, xóa tài khoản có xác nhận mật khẩu và ẩn danh lịch sử.
- Máy dễ/vừa/khó dùng thuật toán riêng, không nhìn tàu bí mật; luyện tập không tính điểm/phạt.
- Giữ phạt online −3 điểm (sàn 0), khóa 60 giây.

## Kết quả

`run_tests.ps1`: PASS toàn bộ protocol/TCP, race, storage fault, migration, penalty và FeatureTests. Máy được chạy trên 200 bộ tàu ngẫu nhiên có seed mỗi mức: số phát trung bình Dễ 95,73; Vừa 54,29; Khó 44,24. Mọi mức kết thúc trong 100 phát, không bắn lặp.

`run_tests.ps1 -Gui -RealTimer`: PASS hai client Swing/TCP, đặt tàu/Ready, bắn/chuyển lượt, chơi lại, mất server/kết nối lại, phạt điểm/thời gian, chọn độ khó/chơi máy/thoát luyện tập, xóa tài khoản từ UI, chuyển bản đồ, bật/tắt bản đồ nhỏ và dọn timer. `-Visual -RealTimer` kiểm tra riêng nhóm giao diện mới. Audio được kiểm tra dữ liệu PCM/WAV và các âm có nội dung khác nhau; không kiểm định chất lượng loa trên từng thiết bị.

Build PowerShell và Git Bash: PASS, target API Java 8 trên JDK 25. Bản JAR server đã chạy thành công với thư viện ở `dist/lib/`. Chưa chạy trực tiếp trên macOS hoặc hai máy LAN.

Dữ liệu thật (bản ghi cũ theo SQLite; lưu trữ nay đã chuyển sang MySQL): SQLite integrity_check=ok; giữ đủ 6 tài khoản và 1 bản ghi trận từ snapshot, đối chiếu ID, username, hash, điểm, số trận/thắng và thời gian phạt. Không xóa tài khoản thật khi kiểm thử.

## Ảnh và âm mẫu

- [Sảnh](ui/lobby.png), [chọn độ khó](ui/bot-levels.png), [phòng thủ](ui/defense.png), [ẩn bản đồ nhỏ](ui/no-minimap.png).
- [Bom bay](ui/bomb-flight.png), [đánh chìm](ui/ship-sunk.png), [animation GIF](ui/bomb-animation.gif).
- [Xóa tài khoản](ui/delete-account.png), [âm phóng bom](audio/launch.wav), [âm tàu chìm](audio/sunk.wav).

Lần clone đầy đủ đầu tiên đã dừng vì lượng tài nguyên lớn; bản clone chọn phần giao diện đã hoàn tất. Bộ duyệt tự động từ chối xóa file tải dở tại `references/starnet/.git/objects/pack/tmp_pack_DNXZya` với lý do `blocked by policy`; file khoảng 1,19 GB còn lại ngoài bản clone đang dùng. Cả thư mục references được gitignore và không đi vào bản chạy.
