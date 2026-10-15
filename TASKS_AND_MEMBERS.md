# PHÂN CÔNG NHIỆM VỤ & KỊCH BẢN KIỂM THỬ (TASKS & INTEGRATION TEST CASES)

Tài liệu này chi tiết hóa trách nhiệm của từng thành viên trong nhóm, định nghĩa rõ **Giao diện kết nối (Contract/Interface)** giữa các phần và cung cấp bộ **Kịch bản kiểm thử tích hợp (Test Cases)** để nghiệm thu đồ án.

---

## 1. PHÂN CÔNG NHIỆM VỤ THEO THÀNH VIÊN

### 1.1. Trần Tuấn Dương - Module Kết nối, Xác thực & Trạng thái Online
- **Phạm vi trách nhiệm:**
  - **Hạ tầng TCP Socket:** Xây dựng `ServerSocket` đa luồng phía Server (Worker per Client) và kết nối TCP Socket bền vững phía Client.
  - **Quản lý phiên kết nối:** Quản lý danh sách kết nối đang hoạt động (`activeSessions`). Xử lý sự kiện ngắt kết nối đột ngột hoặc đóng ứng dụng.
  - **Xác thực người dùng:** Đăng nhập tài khoản (kèm mật khẩu hoặc tự động tạo nếu chưa có), ngăn chặn 1 tài khoản đăng nhập 2 nơi đồng thời.
  - **Đồng bộ danh sách Online & Trạng thái:**
    - Cung cấp danh sách người chơi đang online kèm: Tên, Tổng điểm, Số trận thắng, Trạng thái (`IDLE` - Rảnh, `BUSY` - Đang đấu/trong phòng).
    - Phát thông báo Broadcast thời gian thực (`USER_STATUS_BROADCAST`) khi bất kỳ người chơi nào chuyển trạng thái.
- **Các Message phụ trách:**
  - `LOGIN_REQ`, `LOGIN_RESP`, `LOGOUT_REQ`
  - `GET_ONLINE_USERS_REQ`, `ONLINE_USERS_RESP`, `USER_STATUS_BROADCAST`
- **Tiêu chí nghiệm thu:**
  - Chạy đồng thời >= 3 Client đăng nhập khác nhau, hiển thị danh sách online chính xác.
  - Khi 1 Client vào trận hoặc thoát game, danh sách trên các Client khác tự động cập nhật ngay lập tức.

---

### 1.2. Nguyễn Ngọc Bảo - Module Sảnh (Lobby), Ghép trận & Phòng chơi
- **Phạm vi trách nhiệm:**
  - **Hàng chờ ghép trận tự động (Quick Match):** Đưa người chơi vào hàng đợi `Queue`. Khi có đủ 2 người, tự động ghép đôi và tạo trận.
  - **Tạo và Vào phòng theo mã (Room Code):** 
    - Sinh mã phòng ngẫu nhiên (6 ký tự).
    - Xử lý người chơi khác nhập mã phòng: kiểm tra phòng tồn tại, phòng đã đủ 2 người hay chưa.
  - **Thách đấu trực tiếp (Direct Invite):**
    - Cho phép click chọn 1 người đang `IDLE` trong danh sách để mời.
    - Chuyển tiếp lời mời đến người nhận và hứng phản hồi (`ACCEPT` hoặc `REJECT`).
    - Nếu `ACCEPT`: chuyển cả 2 vào phòng chơi và đổi trạng thái sang `BUSY`.
- **Các Message phụ trách:**
  - `INVITE_REQ`, `INVITE_NOTIFY`, `INVITE_RESPOND_REQ`, `INVITE_RESULT`
  - `CREATE_ROOM_REQ`, `CREATE_ROOM_RESP`, `JOIN_ROOM_REQ`, `JOIN_ROOM_RESP`, `LEAVE_ROOM_REQ`
  - `QUEUE_JOIN_REQ`, `QUEUE_LEAVE_REQ`, `MATCH_FOUND`
- **Tiêu chí nghiệm thu:**
  - Ghép trận tự động hoạt động trơn tru khi 2 client cùng bấm tìm trận.
  - Tạo phòng và vào đúng phòng bằng mã phòng.
  - Lời mời thách đấu hiển thị popup ở client đối thủ, nhận/từ chối phản hồi chính xác.

---

### 1.3. Ngô Thanh Hải - Module Bàn cờ, Bố trí tàu & Trạng thái Sẵn sàng
- **Phạm vi trách nhiệm:**
  - **Giao diện đặt tàu phía Client:**
    - Lưới 10x10 ô vuông trực quan.
    - Cung cấp đủ 5 tàu: 1 tàu 5 ô, 1 tàu 4 ô, 2 tàu 3 ô, 1 tàu 2 ô (tổng 17 ô).
    - Hỗ trợ xoay tàu Ngang / Dọc.
    - Thuật toán kiểm tra tính hợp lệ tại Client: Không nằm ra ngoài biên (0..9), không đè chồng lên nhau.
    - Tùy chọn đặt tàu ngẫu nhiên nhanh (Random Placement) để người chơi test nhanh.
  - **Đồng bộ và Kiểm tra tại Server (Authoritative Validation):**
    - Nhận danh sách tọa độ tàu gửi lên qua `PLACE_SHIPS_REQ`.
    - Server thẩm định lại tính hợp lệ của toàn bộ 5 con tàu (ngăn chặn Client sửa đổi tọa độ bất hợp pháp).
  - **Quản lý trạng thái Sẵn sàng (Ready):**
    - Chỉ cho bấm Ready khi đã đặt đủ 5 tàu hợp lệ.
    - Gửi thông báo đối thủ đã Ready.
    - Khi cả 2 người chơi cùng Ready, kích hoạt chuyển sang giai đoạn bắn.
- **Các Message phụ trách:**
  - `PLACE_SHIPS_REQ`, `PLAYER_READY_REQ`, `OPPONENT_READY_NOTIFY`, `MATCH_START`
- **Tiêu chí nghiệm thu:**
  - Không thể bấm Ready nếu chưa đặt đủ 5 tàu hoặc đặt sai quy tắc.
  - Đặt tàu thành công, cả 2 bên Ready thì trận đấu tự động bắt đầu.

---

### 1.4. Dương Đăng Khoa - Module Chiến đấu, Lượt chơi, Timer & Bảng xếp hạng
- **Phạm vi trách nhiệm:**
  - **Cơ chế chiến đấu (Combat Engine):**
    - Nhận lệnh bắn `(x, y)` từ Client đang có lượt.
    - Kiểm tra ô đã bắn chưa (ngăn bắn trùng).
    - So khớp với bàn cờ bí mật của đối phương: Trả về `MISS`, `HIT` hoặc `SUNK`.
    - Quy tắc đổi lượt / giữ lượt: Nếu `MISS` -> đổi lượt; Nếu `HIT` hoặc `SUNK` -> **người chơi tiếp tục được bắn**.
  - **Bộ đếm thời gian lượt bắn (Timer 15s):**
    - Đếm ngược 15 giây cho mỗi lượt.
    - Nếu hết 15s: Server chuyển lượt cho đối thủ, không bắn hộ hoặc tăng shot count.
  - **Xác định kết thúc trận & Tính điểm:**
    - Khi 1 bên bị phá hủy toàn bộ 17 ô tàu -> Kết thúc trận.
    - Xử lý khi 1 bên bấm nút Thoát / ngắt kết nối giữa trận: Xử bên thoát thua cuộc ngay lập tức.
    - Điểm: Người thắng +3 điểm, thua bình thường +0. Rời/ngắt kết nối khi đang đấu bị trừ 3 điểm (không dưới 0) và cấm ghép trận 60 giây; hạn phạt đã lưu không mất khi đăng nhập hoặc khởi động lại server.
    - Xử lý chơi lại (Rematch) hoặc trở về sảnh.
  - **Bảng xếp hạng (Leaderboard):**
    - Truy vấn danh sách toàn hệ thống và sắp xếp theo 3 tiêu chí:
      1. Tổng điểm giảm dần.
      2. Tổng số trận thắng giảm dần.
      3. Tỷ lệ thắng (Win Rate) giảm dần.
- **Các Message phụ trách:**
  - `FIRE_REQ`, `FIRE_RESULT`, `TURN_CHANGE`, `TIMEOUT_NOTIFY`
  - `SURRENDER_REQ`, `MATCH_END`, `REMATCH_REQ`, `REMATCH_CONFIRM_REQ`, `REMATCH_START`
  - `GET_LEADERBOARD_REQ`, `LEADERBOARD_RESP`
- **Tiêu chí nghiệm thu:**
  - Phát bắn chuẩn xác (Miss/Hit/Sunk); bắn trúng được bắn tiếp; bắn trượt đổi lượt.
  - Đồng hồ 15s đếm ngược đồng bộ và xử lý đúng khi timeout.
  - Người chơi bấm Thoát thì đối thủ thắng ngay và nhận +3 điểm.
  - Bảng xếp hạng hiển thị và sắp xếp chính xác.

---

## 2. KỊCH BẢN KIỂM THỬ TÍCH HỢP (INTEGRATION TEST SCENARIOS)

| Test ID | Kịch bản kiểm thử | Các bước thực hiện | Kết quả mong đợi |
| :---: | :--- | :--- | :--- |
| **TC-01** | Đăng nhập đa client & Kiểm tra Online List | 1. Chạy Server.<br>2. Mở Client 1 đăng nhập `userA`.<br>3. Mở Client 2 đăng nhập `userB`. | Cả 2 client đều thấy nhau trong danh sách Online ở trạng thái `Rảnh (IDLE)`. |
| **TC-02** | Thách đấu trực tiếp | 1. `userA` click mời `userB`.<br>2. `userB` thấy popup mời.<br>3. `userB` bấm Chấp nhận (OK). | Server tạo phòng, cả 2 chuyển sang màn hình đặt tàu. Trạng thái cả 2 trên hệ thống đổi thành `Bận (BUSY)`. |
| **TC-03** | Tạo phòng & Vào phòng bằng mã | 1. `userA` bấm Tạo phòng -> Nhận mã `CODE12`.<br>2. `userB` chọn Vào phòng và nhập `CODE12`. | `userB` vào đúng phòng của `userA`, chuyển sang màn hình đặt tàu. |
| **TC-04** | Ghép trận ngẫu nhiên (Matchmaking) | 1. `userA` bấm Tìm trận nhanh.<br>2. `userB` bấm Tìm trận nhanh. | Server tự động ghép cặp 2 người vào một trận đấu mới. |
| **TC-05** | Đặt tàu hợp lệ & Sẵn sàng | 1. Cả 2 đặt đủ 5 tàu (17 ô) không bị đè và không ra ngoài bản đồ.<br>2. Cả 2 bấm Ready. | Server thông báo bắt đầu trận đấu, ngẫu nhiên chọn 1 người đi trước và bắt đầu đếm 15s. |
| **TC-06** | Luật bắn Hit được bắn tiếp, Miss đổi lượt | 1. `userA` có lượt, bắn trúng ô tàu của `userB`.<br>2. Server trả về `HIT`.<br>3. `userA` tiếp tục bắn trượt.<br>4. Server trả về `MISS`. | Sau khi `HIT`, lượt vẫn thuộc `userA`. Sau khi `MISS`, quyền bắn chuyển sang `userB`. |
| **TC-07** | Xử lý hết giờ (Timeout 15s) | 1. Đến lượt `userA` nhưng không thực hiện bắn.<br>2. Chờ hết 15 giây. | Server phát TIMEOUT_NOTIFY và chuyển lượt cho `userB`, không bắn hộ. |
| **TC-08** | Thoát giữa trận (Surrender) | 1. Đang trong trận, `userA` bấm nút "Thoát" (hoặc tắt ứng dụng). | `userA` bị xử thua (0đ). `userB` được thông báo chiến thắng (+3đ). Trạng thái giải phóng về sảnh. |
| **TC-09** | Kết thúc trận & Chơi lại | 1. `userA` bắn chìm toàn bộ 5 tàu của `userB`.<br>2. Cả 2 hiện popup thông báo kết quả.<br>3. Cả 2 cùng bấm "Chơi lại". | `userA` +3 điểm. Cả 2 được đưa về màn hình đặt tàu của trận mới. |
| **TC-10** | Kiểm tra Bảng xếp hạng | 1. Mở màn hình Bảng xếp hạng từ Client. | Danh sách người chơi hiển thị theo đúng thứ tự: Tổng điểm giảm dần -> Số trận thắng giảm dần -> Win Rate giảm dần. |

## Kết quả triển khai và kiểm thử ngày 04/10/2026

Baseline đã chạy: JDK/javac 25.0.1, build thường thành công; `--release 8` thất bại tại hai FileInputStream.readAllBytes trong StorageManager. Bản cuối thay storage bằng snapshot dùng Files.readAllBytes; build/test đều biên dịch API Java 8. Runtime Java 8 **NOT-RUN** vì môi trường chỉ có JDK 25. LAN hai máy **NOT-RUN**; không suy ra từ localhost.

### Nguồn Word và trách nhiệm

| Nguồn | Module và test đối chiếu |
| --- | --- |
| BANTAUDOIKHANG.docx | Luật toàn hệ thống, bốn lối vào trận, lưu kết quả, bảng xếp hạng; TC-01..10, INT-01..07 |
| Bao_cao_Lap_trinh_mang_Tran_Tuan_Duong.docx | Dương: GameServer/ClientHandler/UserManager/PasswordHasher/SocketClient/LoginFrame; DUONG-01..06 |
| Bao_cao_Lap_trinh_mang_Nguyen_Ngoc_Bao.docx | Bảo: LobbyManager/Room/LobbyFrame; BAO-01..06 |
| Bao_cao_Lap_trinh_mang_Ngo_Thanh_Hai.docx | Hải: Board/Ship/PlacementPanel/BoardGridPanel và placement trong MatchSession; HAI-01..06 |
| Bao_cao_Lap_trinh_mang_Duong_Dang_Khoa.docx | Khoa: MatchManager/MatchSession/BattlePanel/MatchRecord/LeaderboardDialog; KHOA-01..07 |

Đã đối chiếu văn bản đề bài và các hàng test trong năm Word, giữ nguyên file gốc. Tên tin Word được ánh xạ trong NETWORK_PROTOCOL.md; JSON theo dòng/data/x/y giữ theo repository; timeout mất lượt theo KHOA-05. Protocol, storage và kiểm thử tích hợp là phần chung.

### Checklist P0 đến P8

- [x] P0: đọc kế hoạch/quy tắc/tài liệu, baseline, thống nhất đặc tả, runner Java có assertion/timeout/exit code; giữ dữ liệu thật và Word.
- [x] P1: envelope và schema strict, bounded UTF-8 framing, writer queue/watchdog, heartbeat, generation connection, shutdown.
- [x] P2: auth gate, username/password, PBKDF2/migration, duplicate login giữ chủ cũ, logout ownership, presence snapshot và cleanup.
- [x] P3: một thao tác pair, room/queue/invite loại trừ, invitation ID/expiry, Accept xác thực, hủy queue/rời phòng.
- [x] P4: board tạm, fleet validation, Ready ack/cancel/lock, single start, giữ vị trí cũ khi sửa sai, close PLACING.
- [x] P5: match/turn/deadline guard, Miss/Hit/Sunk, timeout mất lượt, timer cũ inert, kết thúc một lần.
- [x] P6: atomic users+matches snapshot, import/backup, pending retry, full history/timestamp, stable ranking, rematch phiên mới và expiry/reject cleanup.
- [x] P7: UI chờ ack và chống click lặp, deadline clock, bỏ event/callback cũ, dọn timer/listener/dialog, kiểm tra Swing thật.
- [x] P8: TCP bốn client/race/domain/storage/GUI, hướng dẫn build/demo, scripts chạy từ thư mục khác, rà diff; giới hạn LAN/runtime Java 8 ghi riêng.

### Lệnh và bằng chứng

| Kiểm tra | Kết quả |
| --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\compile.ps1` | PASS, Java 8 API compatibility trên javac 25.0.1 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1` | PASS, protocol + TCP integration + fake clock/storage + race, ALL TESTS PASSED |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -RealTimer` | PASS, cùng bộ test với lượt thật 15000 ms |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Gui -RealTimer` | PASS, Swing thật trên EDT và TCP, gồm failed connect/reconnect |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -SelfTestFailure` | PASS kiểm tra runner: AssertionError cố ý, exit code **1** |

Test data/build ở `%TEMP%/battleship-*`, server port 0 cấp cổng riêng. Fault test cố ý in `[Storage] Pending result clock-test: injected disk failure`; sau retry snapshot chỉ có một result và +3 đúng một lần. Các log đường dẫn tạm và ảnh GUI được runner in để tái kiểm tra. Ảnh lobby/placement/battle/leaderboard đã được xem; đã sửa glyph thiếu, chữ bị cắt và màu bị look-and-feel Windows ghi đè.

### Trạng thái từng ca tài liệu gốc

| Mã | Trạng thái | Assertion/bằng chứng |
| --- | --- | --- |
| DUONG-01 | PASS | SocketPeer.login và SwingSmoke login bằng nút thật |
| DUONG-02 | PASS | Sai/rỗng password trả AUTH_FAILED, không lấy session |
| DUONG-03 | PASS | Duplicate login sequential và barrier concurrent: một chủ, phiên cũ còn dùng được |
| DUONG-04 | PASS | Bốn socket, mỗi client lấy snapshot đủ bốn người |
| DUONG-05 | PASS | Silent authenticated client hết heartbeat -> OFFLINE; socket thật |
| DUONG-06 | PASS | Presence phase QUEUED/PLACING/READY_WAITING/IN_BATTLE; nút Swing thay theo server |
| BAO-01 | PASS | Invitation ID Accept -> một session, hai người cùng match ID |
| BAO-02 | PASS | Reject, không pair; tiếp tục mời/tạo trận được |
| BAO-03 | PASS | ROOM_NOT_FOUND, không mất context |
| BAO-04 | PASS | Barrier hai người join cùng room: một success, một lỗi |
| BAO-05 | PASS | Barrier bốn queue request, đúng hai nhóm mỗi nhóm hai người |
| BAO-06 | PASS | Queue leave và GUI hủy trước ghép, người đã hủy không nhận trận |
| HAI-01 | PASS | Bộ tàu [5,4,3,3,2] được server nhận; Ready mở đúng |
| HAI-02 | PASS | Fleet tọa độ ngoài biên -> OUT_OF_BOUNDS |
| HAI-03 | PASS | Fleet chồng ô -> OVERLAP, thông báo nêu ID tàu/ô |
| HAI-04 | PASS | Thiếu tàu, sai hình học -> INVALID_FLEET, board cũ còn Ready được |
| HAI-05 | PASS | Một Ready còn PLACING; cancel ack và khóa click được kiểm tra Swing |
| HAI-06 | PASS | Barrier hai Ready, một MATCH_START và first turn giống nhau |
| KHOA-01 | PASS | MISS -> đổi lượt, turn ID mới |
| KHOA-02 | PASS | HIT và SUNK -> giữ shooter, turn ID mới, cuối fleet kết thúc |
| KHOA-03 | PASS | ALREADY_SHOT; fake-clock assert deadline/shot count giữ nguyên |
| KHOA-04 | PASS | NOT_YOUR_TURN; không tạo FIRE_RESULT thành công |
| KHOA-05 | PASS | Timeout thật 15 giây, mất lượt, không auto_x/auto_y/shot |
| KHOA-06 | PASS | Surrender reason khác disconnect; history/điểm một lần |
| KHOA-07 | PASS | Fixture cùng điểm/khác wins/khác tỷ lệ; ổn định username và zero-game |
| INT-01 | PASS | Login/presence TCP + GUI |
| INT-02 | PASS | Room create/join TCP + GUI |
| INT-03 | PASS | Queue pair TCP + GUI và barrier bốn người |
| INT-04 | PASS | Fleet + Ready + single match start |
| INT-05 | PASS | Chuỗi MISS/HIT/SUNK và 17 ô kết thúc |
| INT-06 | PASS | Surrender/disconnect/storage pending retry, không cộng điểm lặp |
| INT-07 | PASS | Full result, restart stats/history, ranking fixture và GUI leaderboard |
| TC-01 | PASS | Bốn online snapshots |
| TC-02 | PASS | Invitation Accept bằng TCP và dialog Swing |
| TC-03 | PASS | Room code bằng TCP và nút Swing |
| TC-04 | PASS | Queue pair bằng TCP và nút Swing |
| TC-05 | PASS | Fleet/Ready/đi trước từ server |
| TC-06 | PASS | HIT giữ/MISS đổi |
| TC-07 | PASS | Timeout 15000 ms |
| TC-08 | PASS | Surrender, socket disconnect, WINDOW_CLOSING RUNNING |
| TC-09 | PASS | Sunk fleet + rematch ID mới + timer ván hai, TCP và GUI |
| TC-10 | PASS | Ranking fixture ba tiêu chí, dialog Swing |

### Hồi quy bổ sung

| Mã | Trạng thái | Test thực hiện |
| --- | --- | --- |
| REG-01 | PASS | Unicode/escape roundtrip, socket split từng byte và coalesce hai frame |
| REG-02 | PASS | JSON hỏng/tin lạ/data sai/số thập phân/trường thiếu, quá 64 KiB, UTF-8 lỗi, EOF giữa frame; peer khác còn phục vụ |
| REG-03 | PASS | Empty password, trước login/sau logout, login đổi user socket; replay sau logout bị UNAUTHENTICATED |
| REG-04 | PASS | Barrier duplicate login; close socket không sở hữu không xóa chủ cũ |
| REG-05 | PASS | Tự mời/join, Accept giả/lặp/hết hạn; crossing invites và queue-vs-Accept |
| REG-06 | PASS | Client chết trong queue được dọn; người còn sống tiếp tục ghép được |
| REG-07 | PASS | Board sai sau đúng vẫn Ready được; không board bị NOT_READY; single start |
| REG-08 | PASS | Board sau Ready/RUNNING và match ID cũ đều bị từ chối |
| REG-09 | PASS | Hit 1 ns trước deadline fake clock; expired FIRE chủ động xử timeout; callback turn cũ inert |
| REG-10 | PASS | Wrong turn/bounds/stale/already-shot; cùng request_id FIRE replay giữ kết quả; fake clock xác nhận không reset |
| REG-11 | PASS | Final fleet shot, surrender cạnh tranh disconnect qua barrier, duplicate match commit/retry: một record/điểm |
| REG-12 | PASS | Socket disconnect queue/phòng chờ/PLACING/RUNNING/POST_GAME; PLACING không record, FINISHED không chấm lại |
| REG-13 | PASS | Rematch ID mới/timer mới, reject, expiry và leave; user ghép lại được |
| REG-14 | PASS | Import giữ ID/stats/nguồn+backup; malformed JSON fail closed; fault write trước/sau restart; retry một lần |
| REG-15 | PASS | Hai RUNNING session song song; non-reading flood peer được OFFLINE, peer khác và timer tiếp tục, executors shutdown |
| REG-16 | PASS | Traffic trước khi SUNK không có sunk_ship_coords; start/ready chỉ public users, không board đối thủ; GUI board đối thủ ban đầu trống |
| REG-17 | PASS | Swing fast click, Ready lock/cancel, window close, server death, failed connect và logout/reconnect |

Bộ test có barrier/latch và timeout rõ, fake epoch/nano/Random cho deadline, socket thật cho lệnh ngoài UI. GUI actions gọi component thật trên EDT, không phải test mock controller. Đây là kết quả trong môi trường hiện tại, không phải bằng chứng soak test hoặc mọi cấu hình mạng.

### Giới hạn còn lại

- LAN hai máy và chạy bằng runtime Java 8: NOT-RUN; đã kiểm tra localhost và biên dịch API Java 8.
- Chưa có soak test dài ngày hoặc kiểm thử Internet/TLS.
- Kết quả chưa commit khi đĩa lỗi nằm trong RAM; crash lúc đó không bảo đảm lưu được pending, snapshot đã commit vẫn nguyên vẹn.
- Không sửa Word, không tự commit/push hoặc xóa dữ liệu thật.
