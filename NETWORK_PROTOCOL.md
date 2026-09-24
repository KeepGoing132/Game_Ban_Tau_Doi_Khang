# ĐẶC TẢ GIAO THỨC MẠNG (NETWORK PROTOCOL SPECIFICATION)

Tài liệu này định nghĩa chi tiết định dạng gói tin, cơ chế truyền thông và danh mục Message Types giữa **Client** và **Server** qua kết nối **TCP Socket**.

---

## 1. NGUYÊN TẮC TRUYỀN THÔNG & ĐÓNG GÓI TIN (FRAMING)

### 1.1. Giao thức vận chuyển
- Giao thức tầng transport: **TCP Socket**.
- Đảm bảo tính toàn vẹn dữ liệu, đúng thứ tự và không bị mất gói tin.

### 1.2. Định dạng đóng gói (Line-Delimited JSON)
- Toàn bộ dữ liệu trao đổi được định dạng bằng chuỗi **JSON mã hóa UTF-8**.
- **Ký tự phân cách (Framing Delimiter):** Mỗi gói tin JSON bắt buộc kết thúc bằng ký tự ngắt dòng `\n`.
- *Lý do:* Giải quyết triệt để bài toán **dính gói (packet concatenation)** và **xé gói (packet fragmentation)** đặc thù của TCP stream. Cả Client và Server chỉ cần đọc đến khi gặp `\n` là có trọn vẹn 1 message để parse (`readLine()`).

### 1.3. Cấu trúc gói tin chuẩn (Base Message Envelope)

```json
{
  "type": "MESSAGE_TYPE_STRING",
  "status": "SUCCESS | ERROR | INFO",
  "message": "Mô tả nội dung hoặc thông điệp lỗi (nếu có)",
  "data": { ... }
}
```

- `type` *(String, Bắt buộc)*: Mã định danh hành động / sự kiện.
- `status` *(String, Tùy chọn)*: Trạng thái phản hồi (`"SUCCESS"`, `"ERROR"`, `"INFO"`).
- `message` *(String, Tùy chọn)*: Chuỗi text thông báo giao diện hoặc giải thích lỗi.
- `data` *(Object, Tùy chọn)*: Chứa payload chi tiết của nghiệp vụ tương ứng.

---

## 2. BẢNG MÃ TIN NHẮN (MESSAGE TYPES ENUM)

| Nhóm chức năng | Message Type | Người gửi -> Nhận | Ý nghĩa |
| :--- | :--- | :---: | :--- |
| **1. Auth & User** | `LOGIN_REQ` | Client -> Server | Gửi thông tin đăng nhập |
| | `LOGIN_RESP` | Server -> Client | Kết quả đăng nhập + Thông tin user |
| | `LOGOUT_REQ` | Client -> Server | Đăng xuất / Ngắt kết nối |
| | `GET_ONLINE_USERS_REQ` | Client -> Server | Yêu cầu danh sách người chơi online |
| | `ONLINE_USERS_RESP` | Server -> Client | Trả về danh sách người chơi & trạng thái |
| | `USER_STATUS_BROADCAST` | Server -> Broadcast | Thông báo 1 user đổi trạng thái (Rảnh/Bận) |
| **2. Lobby & Room** | `CREATE_ROOM_REQ` | Client -> Server | Yêu cầu tạo phòng riêng |
| | `CREATE_ROOM_RESP` | Server -> Client | Trả về mã phòng (Room Code) |
| | `JOIN_ROOM_REQ` | Client -> Server | Yêu cầu vào phòng bằng Room Code |
| | `JOIN_ROOM_RESP` | Server -> Client | Kết quả vào phòng |
| | `LEAVE_ROOM_REQ` | Client -> Server | Rời khỏi phòng hiện tại |
| | `ROOM_USER_JOINED` | Server -> Client | Thông báo có đối thủ bước vào phòng |
| | `ROOM_USER_LEFT` | Server -> Client | Đối thủ đã rời phòng |
| **3. Invite & Matchmaking** | `INVITE_REQ` | Client -> Server | Mời 1 người chơi đang rảnh |
| | `INVITE_NOTIFY` | Server -> Đối thủ | Gửi thông báo lời mời đến đối thủ |
| | `INVITE_RESPOND_REQ` | Đối thủ -> Server | Phản hồi lời mời (`ACCEPT` hoặc `REJECT`) |
| | `INVITE_RESULT` | Server -> Người mời | Thông báo kết quả lời mời |
| | `QUEUE_JOIN_REQ` | Client -> Server | Tham gia hàng chờ tự ghép (Quick Match) |
| | `QUEUE_LEAVE_REQ` | Client -> Server | Hủy hàng chờ |
| | `MATCH_FOUND` | Server -> Cả 2 Client | Tìm thấy trận đấu, chuyển sang phòng đấu |
| **4. Ship Placement** | `PLACE_SHIPS_REQ` | Client -> Server | Gửi tọa độ 5 con tàu đã đặt |
| | `PLAYER_READY_REQ` | Client -> Server | Bấm nút Sẵn sàng (Ready) |
| | `OPPONENT_READY_NOTIFY` | Server -> Đối thủ | Báo đối thủ đã bấm Ready |
| | `MATCH_START` | Server -> Cả 2 Client | Cả 2 đã Ready, bắt đầu trận, chỉ định lượt đầu |
| **5. Battle & Turn** | `FIRE_REQ` | Client -> Server | Gửi tọa độ bắn `(x, y)` |
| | `FIRE_RESULT` | Server -> Cả 2 Client | Kết quả phát bắn (Hit/Miss/Sunk) + người bắn |
| | `TURN_CHANGE` | Server -> Cả 2 Client | Đổi quyền bắn cho người tiếp theo + Timer 15s |
| | `TIMEOUT_NOTIFY` | Server -> Cả 2 Client | Hết 15s lượt bắn, xử lý bắn hộ/mất lượt |
| **6. End Game & Rematch** | `SURRENDER_REQ` | Client -> Server | Đầu hàng / Bấm nút Thoát khi đang đấu |
| | `MATCH_END` | Server -> Cả 2 Client | Kết thúc trận, công bố Winner/Loser, điểm số |
| | `REMATCH_REQ` | Client -> Server | Yêu cầu chơi lại |
| | `REMATCH_NOTIFY` | Server -> Đối thủ | Đối thủ yêu cầu chơi lại |
| | `REMATCH_CONFIRM_REQ` | Client -> Server | Đồng ý hoặc từ chối chơi lại |
| | `REMATCH_START` | Server -> Cả 2 Client | Cả 2 đồng ý chơi lại -> bắt đầu đặt tàu lại |
| **7. Leaderboard** | `GET_LEADERBOARD_REQ` | Client -> Server | Yêu cầu xem Bảng xếp hạng |
| | `LEADERBOARD_RESP` | Server -> Client | Dữ liệu BXH (đã sắp xếp đúng tiêu chí) |

---

## 3. CHI TIẾT PAYLOAD TỪNG GIAI ĐOẠN (API FLOW & SCHEMA)

### 3.1. Đăng nhập & Danh sách Online
#### Client -> Server: `LOGIN_REQ`
```json
{
  "type": "LOGIN_REQ",
  "data": {
    "username": "tuan_duong",
    "password": "hashed_or_plain_password"
  }
}
```

#### Server -> Client: `LOGIN_RESP`
```json
{
  "type": "LOGIN_RESP",
  "status": "SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "user_id": 101,
    "username": "tuan_duong",
    "total_points": 15,
    "total_wins": 5,
    "total_games": 8,
    "status": "IDLE"
  }
}
```

#### Client -> Server: `GET_ONLINE_USERS_REQ`
```json
{
  "type": "GET_ONLINE_USERS_REQ"
}
```

#### Server -> Client: `ONLINE_USERS_RESP`
```json
{
  "type": "ONLINE_USERS_RESP",
  "status": "SUCCESS",
  "data": {
    "users": [
      { "user_id": 101, "username": "tuan_duong", "total_points": 15, "total_wins": 5, "status": "IDLE" },
      { "user_id": 102, "username": "ngoc_bao", "total_points": 21, "total_wins": 7, "status": "BUSY" },
      { "user_id": 103, "username": "thanh_hai", "total_points": 9, "total_wins": 3, "status": "IDLE" }
    ]
  }
}
```

#### Server -> Broadcast: `USER_STATUS_BROADCAST`
```json
{
  "type": "USER_STATUS_BROADCAST",
  "data": {
    "user_id": 101,
    "username": "tuan_duong",
    "status": "BUSY"
  }
}
```
*(Status hợp lệ: `"IDLE"` - Đang rảnh, `"BUSY"` - Đang bận thi đấu/trong phòng, `"OFFLINE"`).*

---

### 3.2. Ghép trận, Tạo/Vào phòng, Mời đấu

#### Client (User A) -> Server: `INVITE_REQ`
```json
{
  "type": "INVITE_REQ",
  "data": {
    "target_user_id": 103
  }
}
```

#### Server -> Client (User B): `INVITE_NOTIFY`
```json
{
  "type": "INVITE_NOTIFY",
  "data": {
    "inviter_id": 101,
    "inviter_name": "tuan_duong",
    "inviter_points": 15
  }
}
```

#### Client (User B) -> Server: `INVITE_RESPOND_REQ`
```json
{
  "type": "INVITE_RESPOND_REQ",
  "data": {
    "inviter_id": 101,
    "accept": true
  }
}
```

#### Client -> Server: `CREATE_ROOM_REQ`
```json
{
  "type": "CREATE_ROOM_REQ"
}
```

#### Server -> Client: `CREATE_ROOM_RESP`
```json
{
  "type": "CREATE_ROOM_RESP",
  "status": "SUCCESS",
  "data": {
    "room_code": "BTL888"
  }
}
```

#### Client -> Server: `JOIN_ROOM_REQ`
```json
{
  "type": "JOIN_ROOM_REQ",
  "data": {
    "room_code": "BTL888"
  }
}
```

---

### 3.3. Đặt tàu & Khởi tạo trận đấu (Ship Placement & Ready)

Mỗi tàu được định nghĩa bởi danh sách tọa độ các ô:
- Ô tàu: `{"x": int, "y": int}` (với `0 <= x, y <= 9`).

#### Client -> Server: `PLACE_SHIPS_REQ`
```json
{
  "type": "PLACE_SHIPS_REQ",
  "data": {
    "ships": [
      {
        "id": "CARRIER",
        "size": 5,
        "coordinates": [{"x": 0, "y": 0}, {"x": 1, "y": 0}, {"x": 2, "y": 0}, {"x": 3, "y": 0}, {"x": 4, "y": 0}]
      },
      {
        "id": "BATTLESHIP",
        "size": 4,
        "coordinates": [{"x": 0, "y": 2}, {"x": 0, "y": 3}, {"x": 0, "y": 4}, {"x": 0, "y": 5}]
      },
      {
        "id": "CRUISER_1",
        "size": 3,
        "coordinates": [{"x": 3, "y": 3}, {"x": 4, "y": 3}, {"x": 5, "y": 3}]
      },
      {
        "id": "CRUISER_2",
        "size": 3,
        "coordinates": [{"x": 7, "y": 1}, {"x": 7, "y": 2}, {"x": 7, "y": 3}]
      },
      {
        "id": "DESTROYER",
        "size": 2,
        "coordinates": [{"x": 8, "y": 8}, {"x": 9, "y": 8}]
      }
    ]
  }
}
```

#### Client -> Server: `PLAYER_READY_REQ`
```json
{
  "type": "PLAYER_READY_REQ"
}
```

#### Server -> Cả 2 Client: `MATCH_START`
```json
{
  "type": "MATCH_START",
  "status": "SUCCESS",
  "data": {
    "match_id": "M_20260924_001",
    "player1": { "user_id": 101, "username": "tuan_duong" },
    "player2": { "user_id": 103, "username": "thanh_hai" },
    "first_turn_player_id": 101,
    "turn_time_limit_sec": 15
  }
}
```

---

### 3.4. Bắn & Lượt chơi (Combat Protocol)

#### Client -> Server: `FIRE_REQ`
```json
{
  "type": "FIRE_REQ",
  "data": {
    "x": 4,
    "y": 5
  }
}
```

#### Server -> Cả 2 Client: `FIRE_RESULT`
```json
{
  "type": "FIRE_RESULT",
  "data": {
    "shooter_id": 101,
    "target_id": 103,
    "coordinate": { "x": 4, "y": 5 },
    "result": "HIT",
    "sunk_ship": null,
    "remaining_ships_target": 4,
    "next_turn_player_id": 101
  }
}
```
*Ghi chú về `result`:*
- `"MISS"`: Bắn trượt. `next_turn_player_id` sẽ chuyển sang người còn lại.
- `"HIT"`: Bắn trúng 1 phần tàu. `next_turn_player_id` giữ nguyên là `shooter_id` (được bắn tiếp).
- `"SUNK"`: Bắn chìm toàn bộ tàu. `sunk_ship` chứa danh sách các ô của tàu vừa chìm (ví dụ: `[{"x": 4, "y": 3}, {"x": 4, "y": 4}, {"x": 4, "y": 5}]`). `next_turn_player_id` giữ nguyên là `shooter_id`.

#### Server -> Cả 2 Client: `TURN_CHANGE`
```json
{
  "type": "TURN_CHANGE",
  "data": {
    "current_turn_player_id": 103,
    "time_limit_sec": 15
  }
}
```

---

### 3.5. Kết thúc trận, Thoát trận & Bảng xếp hạng

#### Server -> Cả 2 Client: `MATCH_END`
```json
{
  "type": "MATCH_END",
  "data": {
    "match_id": "M_20260924_001",
    "winner_id": 101,
    "loser_id": 103,
    "reason": "ALL_SHIPS_DESTROYED",
    "scores": {
      "101": { "points_awarded": 3, "total_points": 18, "total_wins": 6 },
      "103": { "points_awarded": 0, "total_points": 9, "total_wins": 3 }
    },
    "duration_seconds": 245,
    "total_shots_player1": 32,
    "total_shots_player2": 28
  }
}
```
*(Nếu một bên bấm Thoát: `reason` là `"SURRENDER"` hoặc `"DISCONNECTED"`).*

#### Client -> Server: `GET_LEADERBOARD_REQ`
```json
{
  "type": "GET_LEADERBOARD_REQ"
}
```

#### Server -> Client: `LEADERBOARD_RESP`
```json
{
  "type": "LEADERBOARD_RESP",
  "status": "SUCCESS",
  "data": {
    "leaderboard": [
      { "rank": 1, "username": "ngoc_bao", "total_points": 21, "total_wins": 7, "total_games": 9, "win_rate": 77.78 },
      { "rank": 2, "username": "tuan_duong", "total_points": 18, "total_wins": 6, "total_games": 9, "win_rate": 66.67 },
      { "rank": 3, "username": "thanh_hai", "total_points": 9, "total_wins": 3, "total_games": 8, "win_rate": 37.5 }
    ]
  }
}
```
