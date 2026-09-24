# BTL LẬP TRÌNH MẠNG: GAME BẮN TÀU ĐỐI KHÁNG ONLINE (BATTLESHIP)

Hệ thống trò chơi **Bắn Tàu Đối Kháng Trực Tuyến (Battleship Online)** được xây dựng hoàn toàn bằng **Java thuần (Standard Java SE)**, sử dụng kiến trúc **Client - Server qua giao thức TCP Socket**, giao diện đồ họa **Java Swing hiện đại**, hỗ trợ nhiều người chơi đồng thời, sảnh chờ trực quan, tạo phòng đấu riêng, ghép trận tự động và bảng xếp hạng thời gian thực.

---

## 🚀 HƯỚNG DẪN KHỞI CHẠY NHANH (QUICK START)

Dự án không phụ thuộc vào bất kỳ thư viện bên ngoài nào (Zero Dependencies). Bạn chỉ cần cài JDK (Java 8 trở lên) là có thể chạy ngay lập tức.

### Cách 1: Click đúp các file kịch bản (.bat)
1. **Biên dịch toàn bộ dự án:** Nhấp đúp vào [compile.bat](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/compile.bat).
2. **Khởi chạy Server:** Nhấp đúp vào [run_server.bat](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/run_server.bat) (Server chạy trên cổng `8888`).
3. **Mở Client:** Nhấp đúp vào [run_client.bat](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/run_client.bat) (Có thể nhấp đúp 2 hoặc nhiều lần để mở 2 Client chơi đối kháng với nhau).

### Cách 2: Chạy từ Terminal / PowerShell
```powershell
# 1. Biên dịch
javac -encoding UTF-8 -d bin (Get-ChildItem -Recurse -Path src -Filter *.java).FullName

# 2. Khởi chạy Server
java -Dfile.encoding=UTF-8 -cp bin server.ServerMain 8888

# 3. Khởi chạy Client 1 (Mở terminal mới)
java -Dfile.encoding=UTF-8 -cp bin client.ClientMain

# 4. Khởi chạy Client 2 (Mở terminal mới)
java -Dfile.encoding=UTF-8 -cp bin client.ClientMain
```

### 👤 Tài khoản mẫu sẵn có (Mật khẩu: `123456`)
Tại màn hình đăng nhập có sẵn các nút bấm chọn nhanh tài khoản mẫu:
- `tuan_duong` (18 điểm, 6 trận thắng)
- `ngoc_bao` (21 điểm, 7 trận thắng - Top 1 BXH)
- `thanh_hai` (9 điểm, 3 trận thắng)
- `dang_khoa` (15 điểm, 5 trận thắng)
- `player1`
- `player2`
*(Bạn cũng có thể nhập tên tài khoản bất kỳ khác, hệ thống sẽ tự động tạo mới tài khoản).*

---

## 📁 CẤU TRÚC MÃ NGUỒN (PROJECT ARCHITECTURE)

```
btl/
├── src/
│   ├── common/                               # Dữ liệu & Giao thức dùng chung
│   │   ├── config/
│   │   │   └── GameConfig.java               # Hằng số: Bàn cờ 10x10, Bộ 5 tàu 17 ô, Timer 15s, Điểm +3/0
│   │   ├── model/
│   │   │   ├── Coordinate.java               # Tọa độ (x, y)
│   │   │   ├── Ship.java                     # Con tàu, kích thước, kiểm tra chìm
│   │   │   ├── Board.java                    # Bàn cờ 10x10, thẩm định hợp lệ, xử lý trúng/trượt
│   │   │   ├── ShotResult.java               # Enum: MISS, HIT, SUNK
│   │   │   ├── User.java                     # Thông tin tài khoản, điểm, tỷ lệ thắng
│   │   │   ├── UserStatus.java               # Enum: IDLE (Rảnh), BUSY (Đang đấu), OFFLINE
│   │   │   └── MatchRecord.java              # Lịch sử trận đấu
│   │   └── protocol/
│   │       ├── MessageType.java              # Toàn bộ mã tin nhắn giao thức
│   │       ├── Message.java                  # Lớp phong bì gói tin Base Message
│   │       └── JsonUtil.java                 # Bộ parse/serialize JSON thuần Java (Zero-dependency)
│   │
│   ├── server/                               # Phía Máy chủ (Server TCP)
│   │   ├── ServerMain.java                   # Điểm bắt đầu Server
│   │   ├── network/
│   │   │   ├── GameServer.java               # ServerSocket TCP đa luồng
│   │   │   └── ClientHandler.java            # Luồng xử lý cho mỗi Client kết nối
│   │   ├── model/
│   │   │   ├── Room.java                     # Phòng chơi, mã phòng (Room Code)
│   │   │   └── MatchSession.java             # Điều hành trận đấu, Timer 15s, luật bắn tiếp khi Hit
│   │   └── manager/
│   │       ├── StorageManager.java           # Lưu trữ JSON (users.json, matches.json)
│   │       ├── UserManager.java              # Xác thực, phiên kết nối, tính BXH theo 3 tiêu chí
│   │       ├── LobbyManager.java             # Quản lý sảnh, hàng chờ tự ghép, tạo/vào phòng, mời bạn
│   │       └── MatchManager.java             # Điều phối các trận đấu đang diễn ra
│   │
│   └── client/                               # Phía Người dùng (Client GUI)
│       ├── ClientMain.java                   # Điểm bắt đầu Client
│       ├── network/
│       │   ├── SocketClient.java             # Kết nối Socket TCP, luồng đọc bất đồng bộ
│       │   └── ServerListener.java           # Interface nhận sự kiện từ Server
│       ├── controller/
│       │   └── ClientController.java         # Điều phối luồng màn hình và nghiệp vụ Client
│       └── ui/
│           ├── Theme.java                    # Bảng màu Dark Navy hiện đại, font chữ, style nút
│           ├── BoardGridPanel.java           # Component bàn cờ 10x10 trực quan (A-J, 1-10)
│           ├── LoginFrame.java               # Cửa sổ đăng nhập & chọn tài khoản mẫu
│           ├── LobbyFrame.java               # Sảnh chính: Danh sách online, tìm trận, tạo/vào phòng
│           ├── PlacementPanel.java           # Đặt 5 tàu (kèm nút Xếp ngẫu nhiên & kiểm tra Ready)
│           ├── BattlePanel.java              # Màn hình chiến đấu 2 bàn cờ, đếm ngược 15s
│           └── LeaderboardDialog.java        # Bảng vinh danh Top người chơi (3 tiêu chí)
│
├── data/                                     # Dữ liệu lưu trữ bền vững (Tự sinh khi chạy)
│   ├── users.json                            # Danh sách người chơi, điểm số, trận thắng
│   └── matches.json                          # Lịch sử chi tiết các trận đấu đã diễn ra
│
├── compile.bat                               # Script 1-click biên dịch dự án
├── run_server.bat                            # Script 1-click chạy Server
└── run_client.bat                            # Script 1-click mở Client
```

---

## 👥 ÁNH XẠ NHIỆM VỤ 4 THÀNH VIÊN VÀO MÃ NGUỒN

| Thành viên | Nhiệm vụ chính | Các file code đảm nhiệm |
| :--- | :--- | :--- |
| **Trần Tuấn Dương** | Kết nối TCP đa client, Đăng nhập, Quản lý trạng thái Online thời gian thực | [GameServer.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/network/GameServer.java), [ClientHandler.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/network/ClientHandler.java), [UserManager.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/manager/UserManager.java), [LoginFrame.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/LoginFrame.java) |
| **Nguyễn Ngọc Bảo** | Sảnh (Lobby), Hàng chờ tự ghép cặp, Tạo/Vào phòng bằng Room Code, Lời mời thách đấu | [LobbyManager.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/manager/LobbyManager.java), [Room.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/model/Room.java), [LobbyFrame.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/LobbyFrame.java) |
| **Ngô Thanh Hải** | Bàn cờ 10x10, Giao diện & thuật toán đặt đủ 5 tàu hợp lệ, Đồng bộ Ready | [Board.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/common/model/Board.java), [Ship.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/common/model/Ship.java), [BoardGridPanel.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/BoardGridPanel.java), [PlacementPanel.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/PlacementPanel.java) |
| **Dương Đăng Khoa** | Luật bắn Hit/Miss/Sunk, Timer 15s, Tiếp tục bắn khi trúng, Xác định Thắng/Thua, Thoát = thua, Bảng xếp hạng | [MatchSession.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/model/MatchSession.java), [MatchManager.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/server/manager/MatchManager.java), [BattlePanel.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/BattlePanel.java), [LeaderboardDialog.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/client/ui/LeaderboardDialog.java) |
| **Cả nhóm** | Thống nhất MessageType, JSON Protocol, Kiểm thử tích hợp | [MessageType.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/common/protocol/MessageType.java), [Message.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/common/protocol/Message.java), [JsonUtil.java](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/src/common/protocol/JsonUtil.java), [TASKS_AND_MEMBERS.md](file:///e:/D/ki1_nam4/Lap_trinh_mang/btl/TASKS_AND_MEMBERS.md) |

---

## 🎯 CÁC TÍNH NĂNG CHÍNH ĐÃ HOÀN TẤT 100%

1. **Chuẩn giao thức TCP Socket Stream:** Đóng gói bằng Line-Delimited JSON (kết thúc bằng `\n`), xử lý triệt để bài toán dính gói/xé gói.
2. **Authoritative Server:** Client chỉ gửi tọa độ bắn; Server giữ bí mật bàn cờ đối thủ, kiểm tra tính hợp lệ và trả kết quả để chống nhìn trộm tàu (chống cheat).
3. **Luật chơi chính xác theo đề bài:**
   - Bàn cờ 10x10, bộ 5 tàu: 1 tàu 5 ô, 1 tàu 4 ô, 2 tàu 3 ô, 1 tàu 2 ô (Tổng 17 ô).
   - **Bắn trúng (Hit) hoặc bắn chìm (Sunk) được tiếp tục bắn tiếp**; Trượt (Miss) mới đổi lượt.
   - Mỗi lượt có **15 giây đếm ngược**, hết giờ Server tự động can thiệp.
   - Thắng nhận **+3 điểm**, thua **0 điểm**.
   - Bấm "Thoát / Đầu hàng" giữa trận bị **xử thua ngay lập tức**, đối thủ được xử thắng (+3 điểm).
4. **Đa dạng chế độ vào trận:**
   - Thách đấu trực tiếp người đang rảnh trong danh sách Online.
   - Tạo phòng riêng và chia sẻ mã phòng 6 ký tự (Room Code).
   - Nhập mã phòng có sẵn để vào thi đấu.
   - Hàng chờ ghép trận tự động (Quick Match).
5. **Bảng xếp hạng hệ thống:** Tự động sắp xếp theo đúng 3 tiêu chí đề bài: Tổng điểm giảm dần $\to$ Số trận thắng giảm dần $\to$ Tỷ lệ thắng giảm dần.
