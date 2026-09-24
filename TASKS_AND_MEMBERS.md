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
    - Nếu hết 15s: Server tự động bắn ngẫu nhiên hoặc chuyển lượt cho đối thủ.
  - **Xác định kết thúc trận & Tính điểm:**
    - Khi 1 bên bị phá hủy toàn bộ 17 ô tàu -> Kết thúc trận.
    - Xử lý khi 1 bên bấm nút Thoát / ngắt kết nối giữa trận: Xử bên thoát thua cuộc ngay lập tức.
    - Cộng điểm: Người thắng +3 điểm, người thua +0 điểm.
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
| **TC-07** | Xử lý hết giờ (Timeout 15s) | 1. Đến lượt `userA` nhưng không thực hiện bắn.<br>2. Chờ hết 15 giây. | Server kích hoạt timeout, tự động bắn ngẫu nhiên hoặc chuyển lượt cho `userB`. |
| **TC-08** | Thoát giữa trận (Surrender) | 1. Đang trong trận, `userA` bấm nút "Thoát" (hoặc tắt ứng dụng). | `userA` bị xử thua (0đ). `userB` được thông báo chiến thắng (+3đ). Trạng thái giải phóng về sảnh. |
| **TC-09** | Kết thúc trận & Chơi lại | 1. `userA` bắn chìm toàn bộ 5 tàu của `userB`.<br>2. Cả 2 hiện popup thông báo kết quả.<br>3. Cả 2 cùng bấm "Chơi lại". | `userA` +3 điểm. Cả 2 được đưa về màn hình đặt tàu của trận mới. |
| **TC-10** | Kiểm tra Bảng xếp hạng | 1. Mở màn hình Bảng xếp hạng từ Client. | Danh sách người chơi hiển thị theo đúng thứ tự: Tổng điểm giảm dần -> Số trận thắng giảm dần -> Win Rate giảm dần. |
