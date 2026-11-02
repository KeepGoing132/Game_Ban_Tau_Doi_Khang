# Giao thức Battleship Online

Đặc tả wire, cập nhật 26/10/2026. Client và server trong repository cần được build cùng phiên bản.

## Transport và envelope

TCP UTF-8, mỗi JSON object kết thúc bằng `\n`; chấp nhận CRLF. `LineConnection.readLine()` giới hạn ngay trong lúc đọc, tối đa 65536 byte trước newline. Nhiều JSON trong một lần ghi hoặc một JSON chia nhiều lần ghi đều được ghép đúng. UTF-8 hỏng, EOF giữa frame hoặc frame quá dài đóng connection; JSON hỏng/tin lạ trả lỗi và connection còn dùng được. JSON không chấp nhận key trùng, escape sai, số không hợp lệ, dữ liệu thừa hay nesting quá 64.

```json
{"type":"FIRE_REQ","request_id":"shot-42","data":{"match_id":"M_uuid","turn_id":7,"x":2,"y":3}}
```

| Envelope | Quy tắc |
| --- | --- |
| type | String trong MessageType, bắt buộc |
| request_id | String tối đa 128 ký tự, client chính thức dùng UUID cho mọi request |
| status | SUCCESS/ERROR trên phản hồi; client không quyết định kết quả bằng trường này |
| message | Thông báo tùy chọn |
| error_code | Mã lỗi trên phản hồi ERROR |
| server_time_ms | Epoch milliseconds lúc server gửi |
| data | Object; nếu có phải đúng kiểu, không nhận array/scalar |

Phản hồi nghiệp vụ trực tiếp lặp request_id. Event gửi cả phòng/trận không mang request_id của người khác, có room_id hoặc match_id. Envelope không parse được có thể trả ERROR_NOTIFY không request_id. Server lấy danh tính từ socket đã xác thực; không dùng user ID do payload tự khai làm người gửi.

Mỗi connection cache 256 request_id/nội dung/phản hồi. Gửi lại cùng ID và nội dung trả phản hồi cũ, không phát lại side effect; cùng ID nội dung khác bị INVALID_MESSAGE. Logout xóa cache phiên xác thực; mọi nghiệp vụ sau logout vẫn UNAUTHENTICATED. Client không có ID vẫn bị kiểm tra các bất biến server, nhưng không có replay theo ID.

Trước login chỉ cho LOGIN_REQ, HEARTBEAT_REQ. Các server event/response gửi từ client bị UNKNOWN_TYPE. Heartbeat mỗi 5 giây; 20 giây không nhận message hợp lệ thì đóng/cleanup. Hàng đợi gửi 256 message, một writer mỗi socket; queue đầy/lỗi ghi/peer không đọc dẫn đến đóng connection. Giới hạn 128 connections.

## Danh mục request và response

Các số x/y/size/user ID/turn_id phải là JSON integer, không phải chuỗi hoặc số thập phân. Boolean ready/accept phải là JSON boolean. x=col, y=row, chỉ số 0..9.

| Request | data | Phản hồi trực tiếp |
| --- | --- | --- |
| HEARTBEAT_REQ | {} | HEARTBEAT_RESP |
| LOGIN_REQ | username, password (String) | LOGIN_RESP: user_id, username, total_points/wins/games, win_rate, status, phase; sau đó ONLINE_USERS_RESP snapshot |
| LOGOUT_REQ | {} | LOGOUT_RESP sau cleanup |
| CHANGE_PASSWORD_REQ | old_password, new_password (String) | CHANGE_PASSWORD_RESP sau commit; chỉ IDLE/COOLDOWN |
| DELETE_ACCOUNT_REQ | password (String) | DELETE_ACCOUNT_RESP sau transaction xóa và logout; chỉ tài khoản của socket, chỉ ở sảnh |
| BOT_MATCH_REQ | difficulty: EASY/MEDIUM/HARD | BOT_MATCH_RESP rồi MATCH_FOUND có practice=true và opponent_id âm |
| GET_ONLINE_USERS_REQ | {} | ONLINE_USERS_RESP: users[] |
| GET_LEADERBOARD_REQ | {} | LEADERBOARD_RESP: leaderboard[], rank và các trường public user |
| GET_MATCH_HISTORY_REQ | target_user_id (0 = chính mình), limit (1–50, mặc định 20), result/opponent/from_ms/to_ms/cursor tùy chọn | MATCH_HISTORY_RESP: user_id, username, total_games/wins/points, matches[], has_more, next_cursor |
| GET_MATCH_DETAIL_REQ | match_id (String) | MATCH_DETAIL_RESP: match_id, opponent_id/name, won, points, reason, duration_seconds, ended_at_ms, my_shots, opponent_shots, my_ships[], opponent_ships[], shot_log[], replay_available |
| CREATE_ROOM_REQ | {} | CREATE_ROOM_RESP: room_code, room_id |
| JOIN_ROOM_REQ | room_code | JOIN_ROOM_RESP: room_id, match_id hoặc ERROR |
| LEAVE_ROOM_REQ | match_id khi có trận; room_id khi phòng chờ | LEAVE_ROOM_RESP |
| QUEUE_JOIN_REQ | {} | QUEUE_JOIN_RESP |
| QUEUE_LEAVE_REQ | {} | QUEUE_LEAVE_RESP |
| INVITE_REQ | target_user_id | INVITE_RESP: invitation_id, expires_at_ms |
| INVITE_RESPOND_REQ | invitation_id, accept | INVITE_RESULT: invitation_id, expires_at_ms, accepted; successful pairing là event riêng có match_id/opponent |
| PLACE_SHIPS_REQ | match_id, ships[] | PLACE_SHIPS_RESP sau validate/commit |
| PLAYER_READY_REQ | match_id, ready | PLAYER_READY_RESP: match_id, ready |
| FIRE_REQ | match_id, turn_id, x, y | FIRE_RESULT thành công hoặc ERROR có error_code |
| SURRENDER_REQ | match_id | SURRENDER_RESP sau xử lý kết quả và release phòng |
| REMATCH_REQ | match_id | REMATCH_RESP: accepted=true |
| REMATCH_CONFIRM_REQ | match_id, accept | REMATCH_CONFIRM_RESP: accepted |

LOGIN tên chưa có tạo mới; username chuẩn hóa trim/lowercase ROOT, 1–32 ASCII chữ/số/underscore. Password bắt buộc không rỗng, không quá 128 ký tự. Login socket đã xác thực bị INVALID_STATE; login trùng bị ALREADY_LOGGED_IN, giữ phiên cũ. Response không gửi password/hash/salt.

Đấu máy dùng cùng PLACE_SHIPS/READY/FIRE/TURN/MATCH_END/REMATCH. Máy không xuất hiện trong danh sách online/xếp hạng; trận practice có points_awarded=0, không thay đổi thống kê. DELETE_ACCOUNT_REQ không nhận user_id để chọn tài khoản khác; mật khẩu sai trả AUTH_FAILED, còn trong trận/phòng/queue trả INVALID_STATE.

Public user trong LOGIN_RESP, ONLINE_USERS_RESP, USER_STATUS_BROADCAST và MATCH_END.scores có thêm `matchmaking_blocked_until` (epoch milliseconds), `matchmaking_wait_ms` (thời gian còn lại, không âm) và `penalty_pending` (đang chờ lưu hình phạt). Người đang bị phạt ở sảnh có `phase=COOLDOWN`, `status=BUSY`. Client dùng thời gian còn lại để đếm ngược; server kiểm tra hạn phạt trên mọi lối vào trận và broadcast IDLE khi hết hạn. Bảng xếp hạng chỉ trả các trường thống kê và status.

Ships schema:

```json
{"id":"CARRIER","size":5,"coordinates":[{"x":0,"y":0},{"x":1,"y":0},{"x":2,"y":0},{"x":3,"y":0},{"x":4,"y":0}]}
```

Lịch sử đấu (`MATCH_HISTORY_RESP`) chỉ chứa dữ liệu công khai của từng trận đã lưu: `match_id`, `opponent_id`, `opponent_name`, `won`, `points` (điểm người được hỏi nhận), `shots`, `duration_seconds`, `reason`, `ended_at_ms`, `legacy`, `replay`. Không có tọa độ tàu trong lịch sử. Chỉ socket đã đăng nhập gọi được; `target_user_id=0` là tài khoản của chính socket, tài khoản đã xóa trả về tên ẩn danh.

Khi trận kết thúc, server lưu kèm kết quả hai hạm đội đã niêm phong và toàn bộ nhật ký bắn (`player1Ships`, `player2Ships`, `shotLog` trong payload của bảng `matches`). `replay=true` báo trận có dữ liệu đó; bản ghi cũ không có thì vẫn xem được lịch sử nhưng `replay=false`.

`GET_MATCH_DETAIL_REQ` mở lại một trận đã kết thúc cho **chính hai đấu thủ của trận đó**: `my_ships`/`opponent_ships` theo đúng góc nhìn người hỏi, `shot_log` là mảng theo thứ tự bắn với mỗi phần tử `{turn_id, shooter_id, x, y, result, sunk_ship_coords, sunk_ship_name}` (`sunk_ship_coords`/`sunk_ship_name` chỉ có khi `result=SUNK`). Trận của người khác trả `FORBIDDEN`, mã trận không tồn tại trả `MATCH_NOT_FOUND`, trận không có dữ liệu lưu trả `REPLAY_UNAVAILABLE`, `match_id` rỗng trả `INVALID_MESSAGE`. Tọa độ tàu vẫn không rời server trong lúc trận đang chạy.

Mảng ships phải có đủ năm tàu theo [5,4,3,3,2], ID riêng biệt, tọa độ nguyên và hình học liên tục. ID tàu không rỗng, tối đa 64 ký tự. Payload sai không thay board đã nhận. Client đợi PLACE_SHIPS_RESP rồi mới Ready.

## Events

| Event | Payload chính |
| --- | --- |
| USER_STATUS_BROADCAST | public user đầy đủ: user_id, username, total_points, total_wins, total_games, win_rate, status, phase |
| ONLINE_USERS_RESP | users[]; cũng gửi sau login |
| ROOM_USER_JOINED | room_id/room_code, match_id, host_id/name, guest_id/name |
| MATCH_FOUND | room_id, match_id, opponent_id/name |
| INVITE_NOTIFY | invitation_id, inviter_id/name/points, expires_at_ms |
| INVITE_RESULT | accepted=false hoặc INVITE_EXPIRED; khi ghép có invitation_id, room_id, match_id, opponent_id/name |
| READY_STATE_NOTIFY | room_id, match_id, player1_id, player2_id, player1_ready, player2_ready |
| MATCH_START | match_id, room_id, player1/player2 public user, first_turn_player_id, current_turn_player_id, turn_id, deadline_ms, turn_time_limit_sec/time_limit_sec |
| FIRE_RESULT | match_id, room_id, turn_id đã xử lý, shooter_id, target_id, coordinate:{x,y}, result, remaining_ships_target, next_turn_player_id; SUNK thêm sunk_ship_coords và sunk_ship_name |
| TURN_CHANGE | match_id, room_id, turn_id mới, current_turn_player_id, deadline_ms, time_limit_sec |
| TIMEOUT_NOTIFY | match_id, room_id, turn_id cũ, player_id, deadline_ms, action=LOSE_TURN; không auto_x/auto_y |
| MATCH_END | match_id, room_id, winner_id, loser_id, reason, duration_seconds, player1_shots, player2_shots, scores, storage_saved, expires_at_ms, opponent_ships[] của đối phương, opponent_shots |
| REMATCH_NOTIFY | match_id, requester_id, expires_at_ms |
| REMATCH_START | previous_match_id, match_id mới, room_id |
| ROOM_USER_LEFT | match_id, room_id, message; cả hai trở lại sảnh |
| ERROR_NOTIFY | error_code, message, match_id nếu liên quan |

scores map khóa user ID dạng String; mỗi value có points_awarded, total_points, total_wins, total_games và các trường public user. `points_awarded` là thay đổi điểm có dấu: thắng +3, thua bình thường 0, SURRENDER/DISCONNECTED từ −3 đến 0 tùy tổng điểm trước trận. Rời trận RUNNING bị cấm ghép 60 giây. Khi storage_saved=false, điểm chưa được áp dụng và points_awarded=0; server retry mỗi giây, sau commit broadcast thống kê mới. Rematch bị chặn trong thời gian chưa lưu xong.

`opponent_ships` dùng đúng schema ships ở trên và là bản duy nhất được phép rời server sau khi trận kết thúc: mỗi người chơi chỉ nhận hạm đội của đối phương, gửi kèm MATCH_END để client vẽ lại bàn cờ và bảng báo cáo sau trận. Trong lúc trận đang chạy (PLACING/RUNNING) server tuyệt đối không gửi tọa độ tàu của đối phương.

turn_id bắt đầu 1, tăng sau từng phát bắn hợp lệ (kể cả HIT/SUNK giữ người bắn) và timeout. Deadline quyết định bằng nanoTime; deadline_ms và server_time_ms dùng cho UI. Timer callback kiểm tra trạng thái + turn_id; FIRE sát hạn dùng cùng khóa match. FIRE sau hạn không được tạo phát bắn. Không có TURN_CHANGE sau phát kết thúc trận.

## Mã lỗi

`INVALID_MESSAGE`, `UNKNOWN_TYPE`, `UNAUTHENTICATED`, `AUTH_FAILED`, `ALREADY_LOGGED_IN`, `INVALID_STATE`, `MATCHMAKING_COOLDOWN`, `ROOM_NOT_FOUND`, `ROOM_FULL`, `INVITE_EXPIRED`, `INVALID_FLEET`, `OUT_OF_BOUNDS`, `OVERLAP`, `DUPLICATE_SHIP_ID`, `NOT_READY`, `NOT_YOUR_TURN`, `ALREADY_SHOT`, `STALE_MATCH`, `STALE_TURN`, `TURN_EXPIRED`, `STORAGE_ERROR`, `MATCH_NOT_FOUND`, `FORBIDDEN`, `REPLAY_UNAVAILABLE`.

`MATCHMAKING_COOLDOWN` trả data gồm `user_id` của người bị phạt và ba trường thời gian/phạt nêu trên. Có thể là người gửi yêu cầu hoặc người được mời; client chỉ áp dụng đếm ngược cho chính mình khi user_id khớp. Chặn cả ghép nhanh, tạo/vào phòng, lời mời và đồng ý chơi lại. `penalty_pending=true` vẫn chặn khi thời gian còn lại bằng 0, cho đến khi lưu kết quả thành công.

OVERLAP/OUT_OF_BOUNDS khi đặt tàu có message nêu ID tàu và ô lỗi. Invitation giả/đã dùng/sai receiver/hết hạn bị INVITE_EXPIRED. Lệnh trận cũ hoặc không có membership bị STALE_MATCH. Lỗi bắn không reset timer hoặc shot count. CHAT_MSG là event server gửi cho người nhận; client gửi bằng CHAT_SEND_REQ. OPPONENT_READY_NOTIFY giữ tên cũ; READY_STATE_NOTIFY thay event Ready cũ.

## Chat riêng và lịch sử SQLite

Tất cả lệnh chat yêu cầu đăng nhập, dùng được trong sảnh, xếp tàu, trận đấu và thời gian phạt. Người gửi/đọc lịch sử luôn lấy từ socket đã xác thực; các trường giả `sender_id`/`user_id` từ client không thay đổi danh tính. Không broadcast nội dung chat ra sảnh. Lượt bắn và thời gian lượt không thay đổi khi gửi tin.

| Request → Response/Event | Dữ liệu |
| --- | --- |
| `CHAT_SEND_REQ` → `CHAT_SEND_RESP` | `recipient_id`, `body` (1–500 đơn vị UTF-16), `client_message_id` (1–64 ký tự chữ/số/`_`/`-`). Response: `chat` là bản ghi đã commit. |
| `CHAT_MSG` (event) | Chỉ gửi cho người nhận đang online; `chat` giống bản ghi trong response của người gửi. |
| `CHAT_HISTORY_REQ` → `CHAT_HISTORY_RESP` | `peer_id`, `before_id` tùy chọn (0 = mới nhất). Response: `peer_id`, `peer_name`, `available`, `before_id`, `messages` tối đa 20 tin theo ID tăng dần, `has_more`. Trang trước dùng ID nhỏ nhất hiện có. |
| `CHAT_CONVERSATIONS_REQ` → `CHAT_CONVERSATIONS_RESP` | `conversations`: tối đa 100 cuộc trò chuyện gần đây, có `user_id`, `username`, `available`, `online`, `last_message_id`, `sent_at_ms`. |

Mỗi bản ghi `chat` gồm `message_id`, `sender_id`, `recipient_id`, `sender_name`, `recipient_name`, `body`, `sent_at_ms`, `client_message_id`. Nội dung tiếng Việt, emoji, xuống dòng được giữ dưới dạng văn bản thuần; bỏ khoảng trắng đầu/cuối. Không cho ký tự điều khiển ngoài tab/xuống dòng hoặc chuỗi surrogate hỏng. Giới hạn trang giữ frame UTF-8 dưới 64 KiB.

Server commit SQLite trước khi phản hồi/phát event. Khóa unique `(sender_id, client_message_id)` chống tin trùng kể cả qua kết nối mới hoặc restart: retry cùng đích/nội dung trả lại cùng ID, không phát lại event; nội dung khác dùng cùng khóa bị `INVALID_MESSAGE`. Mất ACK có thể tra lại lịch sử. Người nhận offline đọc tin khi đăng nhập lại. Tối đa 10 lần gửi/5 giây/socket, vượt ngưỡng trả `CHAT_RATE_LIMIT`; tài khoản đích không tồn tại/đã xóa trả `USER_NOT_FOUND`; lỗi commit trả `STORAGE_ERROR` và không phát tin.

Tin cũ với tài khoản đã xóa vẫn thuộc lịch sử của người còn lại, tên hiển thị “Tài khoản đã xóa” và không thể gửi thêm. Đăng ký lại cùng tên nhận ID mới, không thừa hưởng cuộc trò chuyện cũ.

## Sai khác có chủ đích với Word

Giữ JSON theo dòng theo quy tắc repository thay framing length-prefix trong phần I của báo cáo. Giữ data thay payload và x/y thay col/row. Timeout chọn mất lượt theo KHOA-05, không auto-fire. Bảng tên tương đương:

| Word | Repository |
| --- | --- |
| LOGIN_REQUEST / LOGIN_RESPONSE | LOGIN_REQ / LOGIN_RESP |
| ONLINE_LIST / PRESENCE_UPDATE | ONLINE_USERS_RESP / USER_STATUS_BROADCAST |
| ROOM_JOINED | ROOM_USER_JOINED, MATCH_FOUND hoặc INVITE_RESULT có match_id theo lối vào |
| PLACEMENT_REQUEST / PLACEMENT_RESULT | PLACE_SHIPS_REQ / PLACE_SHIPS_RESP |
| READY_UPDATE | READY_STATE_NOTIFY |
| GAME_START | MATCH_START |
| SHOT_REQUEST / SHOT_RESULT | FIRE_REQ / FIRE_RESULT |
| TURN_UPDATE | TURN_CHANGE |
| GAME_OVER | MATCH_END |

Phần shared/module giữ ranh giới phân công Dương/Bảo/Hải/Khoa như báo cáo. Các nguồn Word giữ nguyên, đặc tả này là hợp đồng client/server đang chạy.

## Các trường bổ sung của bản hoàn thiện

- CHANGE_PASSWORD_REQ: old_password và new_password là chuỗi, mật khẩu mới 1–128 ký tự và không toàn khoảng trắng. Người dùng lấy từ socket; user_id tự khai không chọn được tài khoản khác. Thành công trả CHANGE_PASSWORD_RESP với request_id; sai mật khẩu → AUTH_FAILED, dữ liệu sai → INVALID_MESSAGE, chưa login → UNAUTHENTICATED, không ở sảnh rảnh → INVALID_STATE, ghi lỗi → STORAGE_ERROR. Giữ phiên; response không có hash/password.
- ROOM_USER_JOINED, MATCH_FOUND, INVITE_RESULT ghép cặp và REMATCH_START thêm placement_deadline_ms và placement_limit_ms (mặc định 120000). Client đếm theo server_time_ms; Ready không reset hạn. Đến hạn trả ROOM_USER_LEFT có lý do hết chuẩn bị; yêu cầu đặt/Ready tại/sau hạn có thể trả PLACEMENT_EXPIRED trước release. Không tính điểm/trận/phạt.
- HEARTBEAT_REQ/RESP giữ schema; phản hồi lặp request_id để đo nanoTime khứ hồi trong đúng phiên. Số ms chỉ là thông tin giao diện.
- RESULT_SAVED_NOTIFY là event có match_id, room_id và scores cùng schema MATCH_END; phát cho phiên trận còn giữ khi pending commit thành công. Client cập nhật điểm và trạng thái lưu của kết quả. Broadcast public user đồng bộ sảnh kể cả khi trận đã release.
- Lịch sử: result = ALL/WIN/LOSS (mặc định ALL); opponent tìm trong tên đối thủ, tối đa 64 ký tự, không phân biệt hoa thường; from_ms/to_ms là epoch ms nguyên không âm, hai đầu bao gồm. Thiếu cận tương ứng 0/Long.MAX_VALUE. UI đổi dd/MM/yyyy theo múi giờ hiển thị thành khoảng epoch này.
- cursor là chuỗi opaque từ next_cursor trang trước, phải dùng đúng target/bộ lọc. Không tự ghép cursor với truy vấn khác. Cursor/phạm vi sai trả INVALID_MESSAGE. has_more=false thì next_cursor rỗng. Đổi lọc bỏ cursor, tạo request_id mới; phản hồi cũ không thay dữ liệu đang xem.

Mẫu bố trí là dữ liệu cục bộ, không có thông điệp server mới; Ready vẫn gửi PLACE_SHIPS_REQ hiện hành.
