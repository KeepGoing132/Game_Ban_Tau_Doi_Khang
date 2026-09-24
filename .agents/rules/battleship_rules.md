# QUY TẮC PHÁT TRIỂN DỰ ÁN CHO AI (BATTLESHIP PROJECT RULES)

Tài liệu này là chỉ dẫn tối thượng cho mọi trợ lý AI khi tham gia đọc, sinh code, sửa lỗi hoặc tái cấu trúc trong dự án **Game Bắn Tàu Đối Kháng Online (Battleship Online)**. Mọi quyết định thiết kế và mã nguồn phải tuân thủ nghiêm ngặt các quy tắc dưới đây:

---

## 1. NGUYÊN TẮC HỆ THỐNG VÀ BẢO MẬT (SYSTEM INTEGRITY)

1. **Kiến trúc Server trọng tài (Authoritative Server):**
   - Client **TUYỆT ĐỐI KHÔNG** được tự tính toán kết quả phát bắn (Hit/Miss/Sunk) hay quyết định người thắng/kẻ thua.
   - Client chỉ gửi tọa độ `(x, y)` lên Server.
   - Server là nơi duy nhất lưu giữ ma trận bàn cờ bí mật của 2 bên, thẩm định phát bắn, kiểm tra ô đã bắn chưa, phát hiện tàu chìm và ra quyết định kết thúc trận.
   - Không bao giờ gửi vị trí tàu của đối thủ về Client khi trận đấu đang diễn ra để chống gian lận (anti-cheat).

2. **Giao thức mạng TCP & Đóng gói (Framing):**
   - Mọi gói tin giao tiếp qua Socket TCP phải là **Line-Delimited JSON (JSON kết thúc bằng ký tự `\n`)** mã hóa UTF-8.
   - Khi nhận dữ liệu từ Socket, luôn đọc theo từng dòng (`readLine()`) để tránh triệt để lỗi dính gói hoặc xé gói tin TCP.
   - Mọi Message phải có trường `type` thuộc danh mục chuẩn đã định nghĩa trong `NETWORK_PROTOCOL.md`.

---

## 2. LUẬT CHƠI BẤT BIẾN (INVIOLABLE GAME RULES)

1. **Bàn cờ và Tàu:**
   - Kích thước: Đúng **10x10** ô (chỉ số 0 đến 9).
   - Bộ tàu: Đúng **5 tàu, tổng cộng 17 ô**:
     - 1 tàu 5 ô (Carrier)
     - 1 tàu 4 ô (Battleship)
     - 2 tàu 3 ô (Cruiser 1, Cruiser 2)
     - 1 tàu 2 ô (Destroyer)
   - Tàu chỉ được đặt theo chiều Ngang hoặc Dọc, không được đè lên nhau và không được thò ra ngoài viền 10x10.
   - Chỉ được kích hoạt Ready khi đã đặt đủ 5 tàu hợp lệ.

2. **Luật bắn & Lượt đi:**
   - Người đi trước được Server chọn ngẫu nhiên (50/50).
   - Mỗi lượt có **15 giây** để bắn (`turn_time_limit_sec = 15`).
   - Nếu quá 15s không bắn, Server phải kích hoạt xử lý timeout (tự động bắn ngẫu nhiên hoặc mất lượt).
   - Không được phép bắn vào ô đã từng bắn trước đó.
   - **QUY TẮC ĐẶC BIỆT KHI BẮN TRÚNG:**
     - Nếu bắn trúng (`HIT`) hoặc đánh chìm tàu (`SUNK`): **Người chơi đó ĐƯỢC TIẾP TỤC BẮN** (không đổi lượt), bộ đếm 15s được reset lại.
     - Nếu bắn trượt (`MISS`): Quyền bắn mới chuyển sang cho đối phương.

3. **Điều kiện Thắng - Thua, Điểm số & Thoát trận:**
   - Trận đấu kết thúc khi toàn bộ 17 ô tàu của 1 người chơi bị bắn chìm.
   - Điểm số: Thắng được **+3 điểm**, Thua được **0 điểm**.
   - Nếu người chơi chủ động bấm nút "Thoát" hoặc ngắt kết nối đột ngột giữa trận: **Bị xử thua ngay lập tức** (0đ), đối thủ được xử thắng (+3đ).
   - Sau trận: Cho phép chọn "Chơi lại" (Rematch) hoặc "Thoát" về sảnh. Cả 2 cùng đồng ý chơi lại thì mới tạo trận mới.

4. **Bảng xếp hạng (Leaderboard):**
   - Thứ tự sắp xếp bắt buộc theo 3 tiêu chí:
     1. Tổng điểm (`total_points`) giảm dần.
     2. Tổng số trận thắng (`total_wins`) giảm dần.
     3. Tỷ lệ thắng (`win_rate = total_wins / total_games`) giảm dần.

---

## 3. QUY ƯỚC MÃ NGUỒN VÀ TÍCH HỢP

- Khi sinh code phía Server, tổ chức phân tách rõ các module:
  - `network`: Socket listener, ClientHandler, Framing reader/writer.
  - `model`: User, Ship, Board, Room, Match.
  - `service`: AuthService, LobbyService, GameService, LeaderboardService.
- Khi sinh code phía Client, tách biệt luồng mạng (Network Thread) và luồng giao diện (UI Thread) để tránh hiện tượng treo (freeze) giao diện khi chờ I/O mạng.
- Tuân thủ chặt chẽ định dạng JSON và tên trường đã quy định trong `NETWORK_PROTOCOL.md` để đảm bảo code của 4 thành viên (Dương, Bảo, Hải, Khoa) cắm vào là chạy ngay.
