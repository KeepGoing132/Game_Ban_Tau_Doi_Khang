# QUY TẮC TRÒ CHƠI & NGHIỆP VỤ (GAME RULES SPECIFICATION)

Tài liệu này chuẩn hóa toàn bộ luật chơi, cơ chế tính điểm, xếp hạng và các trạng thái của hệ thống **Game Bắn Tàu Đối Kháng Online (Battleship Online)**.

---

## 1. THIẾT LẬP BÀN CỜ VÀ BỘ TÀU (FLEET & BOARD)

### 1.1. Kích thước bản đồ
- Kích thước: **10 x 10 ô vuông**.
- Trục tọa độ chuẩn:
  - Trục ngang (X): Chỉ số từ `0` đến `9` (hoặc cột `A` đến `J`).
  - Trục dọc (Y): Chỉ số từ `0` đến `9` (hoặc hàng `1` đến `10`).
- Khuyến nghị chuẩn hóa tọa độ truyền tải qua mạng: `(x, y)` với `0 <= x <= 9` và `0 <= y <= 9`.

### 1.2. Danh sách tàu chiến (Fleet Composition)
Mỗi người chơi sở hữu chính xác **5 tàu chiến**, tổng cộng chiếm **17 ô**:
| STT | Loại tàu | Chiều dài (Số ô) | Số lượng | Tổng số ô |
| :---: | :--- | :---: | :---: | :---: |
| 1 | Tàu sân bay (Carrier) | 5 ô | 1 | 5 |
| 2 | Tàu thiết giáp (Battleship) | 4 ô | 1 | 4 |
| 3 | Tàu tuần dương (Cruiser) | 3 ô | 2 | 6 |
| 4 | Tàu khu trục (Destroyer) | 2 ô | 1 | 2 |
| **Tổng** | **5 tàu** | - | **5 tàu** | **17 ô** |

### 1.3. Quy tắc đặt tàu (Ship Placement Rules)
- **Hướng đặt:** Chỉ được đặt theo **Hàng ngang (Horizontal)** hoặc **Hàng dọc (Vertical)**. Không được đặt chéo.
- **Biên hợp lệ:** Toàn bộ thân tàu phải nằm hoàn toàn trong phạm vi `0 <= x <= 9` và `0 <= y <= 9`. Không được có phần nào chìa ra ngoài biên.
- **Không chồng lấn (No Overlap):** Không ô nào của tàu này được trùng tọa độ với ô của tàu khác.
- **Khoảng cách giữa các tàu:** Theo đặc tả đề bài, chỉ cần không chồng lấn và không ra ngoài bản đồ (không bắt buộc cách 1 ô giãn cách, trừ khi có thông nhất thêm; mặc định hợp lệ là tàu được phép nằm sát cạnh nhau).
- **Trạng thái Sẵn sàng (Ready):** 
  - Chỉ khi người chơi đã đặt đủ 5 tàu hợp lệ mới được kích hoạt nút **Ready**.
  - Không được phép thay đổi vị trí tàu sau khi đã bấm **Ready** trừ khi hủy Ready (nếu đối thủ chưa Ready).
  - Khi cả 2 người chơi đều chuyển sang trạng thái **Ready**, Server chính thức khởi tạo trận đấu và ngẫu nhiên chọn người đi trước (người bắn phát đầu tiên).

---

## 2. LUẬT BẮN VÀ LƯỢT CHƠI (TURN & COMBAT MECHANICS)

### 2.1. Phân định lượt đi
- **Khởi đầu trận:** Server sử dụng hàm random 50/50 để quyết định Player nào bắn trước (`turn_player_id`).
- Server gửi thông báo đến cả 2 Client xác định rõ ai đang nắm quyền bắn.

### 2.2. Cơ chế hành động trong lượt
- Đến lượt của mình, người chơi click chọn 1 ô trên bản đồ đối phương và nhấn nút **Bắn**.
- **Bộ đếm thời gian (Turn Timer):** Mỗi lượt người chơi có tối đa **15 giây** để chọn ô và gửi lệnh bắn lên Server.
- **Xử lý Timeout (Hết 15 giây):**
  - Nếu hết 15 giây người chơi không bắn: Server tự động chọn ngẫu nhiên một ô chưa bắn trên bàn cờ đối phương để bắn hộ, HOẶC xử lý mất lượt (Server chuyển quyền bắn ngay cho đối phương). 
  - *Khuyến nghị chuẩn:* Server tự động bắn ngẫu nhiên 1 ô hợp lệ chưa bắn, sau đó nếu Miss thì chuyển lượt cho đối thủ.

### 2.3. Quy tắc chọn ô bắn
- Người chơi **không được bắn lại** vào ô đã từng bắn trước đó (dù ô đó trước đây là Hit, Sunk hay Miss). Client phải vô hiệu hóa (disable) các ô này trên giao diện và Server phải kiểm tra chặn gian lận (authoritative check).

### 2.4. Trọng tài Server và Phân loại kết quả bắn
Server nắm toàn bộ ma trận tàu ẩn của cả 2 bên và là trọng tài duy nhất trả kết quả cho mỗi phát bắn:
1. **Miss (Trượt):** Tọa độ bắn không trúng bất kỳ ô tàu nào.
   - Bản đồ ghi nhận ô đánh dấu Trượt.
   - **Quyền bắn chuyển ngay cho đối phương** (Đổi lượt).
2. **Hit (Trúng):** Tọa độ bắn trúng 1 ô của tàu nhưng tàu đó vẫn còn ô khác chưa bị bắn thủng.
   - Bản đồ hiển thị ô bị trúng đạn.
   - **Người chơi hiện tại ĐƯỢC TIẾP TỤC BẮN** (không đổi lượt), reset timer 15 giây cho phát bắn kế tiếp.
3. **Sunk (Đánh chìm):** Tọa độ bắn là ô cuối cùng của một con tàu khiến tàu đó chìm hoàn toàn.
   - Server gửi kèm danh sách tọa độ của toàn bộ con tàu vừa chìm để Client vẽ hiệu ứng chìm tàu.
   - **Người chơi hiện tại ĐƯỢC TIẾP TỤC BẮN** (nếu trận đấu chưa kết thúc), reset timer 15 giây.

---

## 3. ĐIỀU KIỆN THẮNG THUA VÀ KẾT THÚC TRẬN (GAME OVER & SCORING)

### 3.1. Điều kiện thắng bình thường
- Trận đấu kết thúc ngay lập tức khi toàn bộ 5 tàu (17 ô) của một người chơi bị bắn chìm.
- Người còn lại (vẫn còn ít nhất 1 ô tàu chưa chìm) là **Người chiến thắng**.

### 3.2. Điểm số (Scoring)
- **Người thắng (Winner):** Được cộng **+3 điểm**.
- **Người thua (Loser):** Được cộng **+0 điểm**.
- Tổng số trận thắng của Winner được cộng thêm 1.

### 3.3. Xử lý thoát giữa trận (Rage Quit / Disconnect / Surrender)
- Trong khi đang thi đấu, nếu người chơi bấm nút **Thoát** (hoặc chủ động đóng ứng dụng/mất kết nối đột ngột):
  - Người thoát được tính là **Thua cuộc ngay lập tức** (0 điểm, tính là 1 trận thua).
  - Server lập tức gửi thông báo xử thắng cho người chơi còn lại (+3 điểm, +1 trận thắng).
  - Server đóng phòng thi đấu và giải phóng tài nguyên.

### 3.4. Tùy chọn Chơi lại (Rematch) hoặc Thoát
Sau khi công bố kết quả trận đấu:
- Giao diện hai bên hiển thị 2 nút: **Chơi lại (Rematch)** và **Thoát (Exit)**.
- Nếu **cả hai bên cùng bấm Chơi lại**: Server reset trận đấu trong cùng phòng, đưa 2 người chơi quay về màn hình bố trí tàu mới.
- Nếu **một bên bấm Thoát** (hoặc hết thời gian chờ xác nhận): Phòng chơi bị hủy, đưa người chơi về màn hình Lobby / Danh sách online.

### 3.5. Lưu trữ kết quả trận đấu (Match Record)
Mỗi trận kết thúc phải lưu một bản ghi lịch sử gồm:
- Mã trận đấu (`match_id`)
- ID / Tên người chơi 1 và người chơi 2
- ID / Tên người thắng, người thua
- Thời gian bắt đầu và kết thúc (thời lượng thi đấu)
- Tổng số lượt bắn của mỗi người chơi
- Điểm số nhận được của mỗi người chơi

---

## 4. QUY TẮC BẢNG XẾP HẠNG (LEADERBOARD CRITERIA)

Mỗi người chơi có thể mở xem Bảng xếp hạng toàn hệ thống bất kỳ lúc nào từ màn hình sảnh (Lobby).
Thứ tự xếp hạng người chơi được sắp xếp tuần tự theo các tiêu chí ưu tiên:
1. **Tổng số điểm (Total Points)** giảm dần (Ưu tiên cao nhất).
2. Nếu bằng điểm: **Tổng số trận thắng (Total Wins)** giảm dần.
3. Nếu vẫn bằng: **Tỷ lệ thắng (Win Rate %)** giảm dần:
   $$\text{Win Rate} = \frac{\text{Total Wins}}{\text{Total Games Played}} \times 100\%$$
   *(Nếu số trận = 0 thì Win Rate = 0%).*
4. Nếu vẫn bằng: Xếp theo tên tài khoản (A-Z) hoặc thời gian đăng ký tài khoản trước.

---

## 5. CƠ CHẾ SẢNH (LOBBY) & TẠO TRẬN (MATCHMAKING)

Người chơi đăng nhập vào hệ thống sẽ ở trạng thái `ONLINE` (Rảnh). Có 4 hình thức bắt cặp vào trận:

1. **Thách đấu trực tiếp (Direct Invite):**
   - Click vào người chơi có trạng thái `RẢNH` trong danh sách Online -> Gửi lời mời.
   - Phía người nhận hiện hộp thoại: Chấp nhận (`Accept`) hoặc Từ chối (`Reject`).
   - Nếu `Accept`: Server tạo phòng riêng và chuyển cả 2 vào màn hình đặt tàu. Cả 2 chuyển sang trạng thái `BẬN`.
   - Nếu `Reject`: Server thông báo lại cho người gửi lời mời biết lý do bị từ chối.

2. **Tạo phòng riêng (Create Room):**
   - Người chơi chọn "Tạo phòng" -> Server cấp một **Mã phòng (Room Code)** duy nhất gồm 6 ký tự (ví dụ: `ROOM88`).
   - Chủ phòng chia sẻ mã này cho bạn bè.

3. **Vào phòng bằng mã (Join Room):**
   - Người chơi nhập Room Code -> Gửi lên Server.
   - Server kiểm tra: Nếu mã phòng tồn tại và phòng đang chờ người (1/2), cho phép vào phòng; nếu phòng đầy hoặc không tồn tại thì báo lỗi.

4. **Hàng chờ tự động (Auto Matchmaking):**
   - Người chơi bấm "Tìm trận nhanh" -> Server đưa vào Queue.
   - Khi có ít nhất 2 người trong Queue, Server tự động ghép cặp 2 người chơi đầu tiên, tạo phòng đấu và bắt đầu quá trình đặt tàu.
