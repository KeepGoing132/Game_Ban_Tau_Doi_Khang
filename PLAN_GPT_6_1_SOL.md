# Kế hoạch hoàn thiện Battleship Online cho GPT 6.1 Sol

Ngày lập: 03/10/2026. Mục tiêu là hoàn thiện dự án Java TCP Socket và Swing hiện có theo đề bài cùng bốn báo cáo thành viên, xử lý lỗi nghiệp vụ và có bằng chứng kiểm thử trước khi demo. Đã triển khai trong phiên 03–04/10/2026; bằng chứng và các giới hạn nghiệm thu được ghi trong TASKS_AND_MEMBERS.md.

## 1. Nguồn yêu cầu và phạm vi

Đã đọc nội dung năm file Word, các bảng phân công và các hình kiến trúc, luồng xử lý, phác thảo giao diện trong báo cáo. Phần I của bốn báo cáo có nội dung văn bản giống nhau; phần II là yêu cầu riêng của từng thành viên.

| Nguồn | Nội dung dùng để lập kế hoạch |
| --- | --- |
| [BANTAUDOIKHANG.docx](BANTAUDOIKHANG.docx) | Đề bài, luật chơi, bốn cách vào trận, lưu kết quả, phân công nhóm |
| [Báo cáo Trần Tuấn Dương](Bao_cao_Lap_trinh_mang_Tran_Tuan_Duong.docx) | TCP đa client, xác thực, chặn phiên trùng, heartbeat, presence; DUONG-01 đến DUONG-06 |
| [Báo cáo Nguyễn Ngọc Bảo](Bao_cao_Lap_trinh_mang_Nguyen_Ngoc_Bao.docx) | Phòng, queue, invitation có thời hạn, kiểm tra đồng thời; BAO-01 đến BAO-06 |
| [Báo cáo Ngô Thanh Hải](Bao_cao_Lap_trinh_mang_Ngo_Thanh_Hai.docx) | Đặt tàu, kiểm tra server, Ready và hủy Ready; HAI-01 đến HAI-06 |
| [Báo cáo Dương Đăng Khoa](Bao_cao_Lap_trinh_mang_Duong_Dang_Khoa.docx) | Deadline, luật bắn, kết thúc một lần, xếp hạng; KHOA-01 đến KHOA-07 |

Tài liệu đối chiếu: `SYSTEM_ARCHITECTURE.md`, `TASKS_AND_MEMBERS.md`, `NETWORK_PROTOCOL.md`, `GAME_RULES.md`, `README.md`, `.agents/rules/battleship_rules.md` và mã nguồn trong `src/`.

Phạm vi triển khai là sửa và bổ sung hệ thống hiện có. Giữ Java SE, Swing, TCP, cổng mặc định 8888 và cách chạy bằng script. Không chuyển sang web, Spring, WebSocket hay thêm cơ sở dữ liệu dịch vụ ngoài. Các báo cáo Word hiện có là tài liệu đầu vào; việc sửa định dạng Word hoặc tạo slide là công việc bàn giao riêng khi người dùng yêu cầu.

Chỉ cập nhật các tài liệu Markdown hiện có phục vụ nội dung thiết kế. Ghi kết quả kiểm thử và ánh xạ nhiệm vụ trong `TASKS_AND_MEMBERS.md`, hướng dẫn chạy/demo trong `README.md`; không yêu cầu tạo thêm file Markdown riêng cho các nội dung này.

## 2. Hiện trạng đã kiểm tra

Mã nguồn đã có các lớp network, manager, model và UI chính. Luật kích thước bàn cờ, bộ tàu, Hit/Miss/Sunk và ba tiêu chí xếp hạng đã được cài đặt ở mức cơ bản. Không cần dựng lại dự án từ đầu.

Đã biên dịch toàn bộ `src/` thành công bằng `javac 25.0.1`, với đầu ra ở thư mục tạm. Biên dịch kiểm tra API Java 8 bằng `javac --release 8` thất bại ở hai lệnh `FileInputStream.readAllBytes()` trong `StorageManager.java`. Chưa chạy kiểm thử TCP nhiều client hoặc kiểm thử giao diện trong lần lập kế hoạch này. Các phát hiện nghiệp vụ bên dưới đến từ đọc mã, cần có test tái hiện khi sửa.

| Ưu tiên | Phát hiện | Vị trí và tác động |
| --- | --- | --- |
| P0 | Mật khẩu rỗng bỏ qua kiểm tra với tài khoản đã tồn tại; mật khẩu được lưu trực tiếp | `UserManager.login`, `StorageManager.saveUsers`; không đạt yêu cầu xác thực và lưu mật khẩu dạng băm |
| P0 | Đăng nhập trùng đóng phiên cũ rồi thay thế | `UserManager.login`; trái DUONG-03, có thể làm người đang thi đấu bị xử thua |
| P0 | Thiếu cổng kiểm tra xác thực chung và cleanup logout thống nhất | `ClientHandler.handleMessage`; nhiều lệnh vào manager trước đăng nhập, logout chưa dọn toàn bộ room/match ngay trong nhánh xử lý |
| P0 | Ready không kiểm tra board hợp lệ; Ready lặp có thể gọi lại `startMatch()` | `MatchManager.handleReady`, `MatchSession.setPlayerReady`; thiếu trạng thái PLACING/RUNNING rõ ràng |
| P0 | Gán tàu vào board trước khi xác nhận hợp lệ, thiếu chặn sửa board trong trận | `MatchSession.setPlayerShips`; cấu hình sai có thể thay thế dữ liệu đã chấp nhận |
| P0 | Rematch dùng lại scheduler đã shutdown và giữ nguyên matchId | `MatchSession.endMatch`, `checkBothRematchAgreed`; timer ván mới không hoạt động đúng và lịch sử không tách ván |
| P0 | Invitation không có bản ghi/id/hạn sử dụng; nhận Accept không xác thực invitation | `LobbyManager.handleInviteResponse`; có thể ghép sai người hoặc ghi đè phiên trận đang có |
| P0 | Trạng thái room, queue và match chưa có ràng buộc loại trừ thống nhất | `LobbyManager`, `MatchManager`; cần chặn tự join, tự mời, vào nhiều trận và chuyển sảnh giữa trận trái phép |
| P0 | Timer chưa có deadline và mã lượt để loại callback cũ | `MatchSession.startTurnTimer`, `handleFire`; chưa chống đầy đủ tình huống bắn sát timeout |
| P1 | Thiếu heartbeat, requestId, errorCode và kiểm tra matchId trong yêu cầu | `Message`, `MessageType`, hai lớp socket và controller; khó phát hiện mất mạng im lặng, ghép phản hồi và loại lệnh cũ |
| P1 | Lịch sử thiếu loser, thời điểm bắt đầu/kết thúc, điểm từng bên; timestamp không được đọc lại | `MatchRecord`, `StorageManager.loadMatches`; dữ liệu chưa đầy đủ và không giữ nguyên sau restart |
| P1 | Lưu điểm và lịch sử thành hai lần ghi riêng; lỗi ghi chỉ được in log | `MatchSession.endMatch`, `StorageManager`; có thể lưu điểm nhưng mất lịch sử hoặc ngược lại |
| P1 | Client gửi đặt tàu rồi Ready ngay, chưa đợi xác nhận; sửa vị trí thất bại xóa tàu cũ trước | `ClientController.sendReady`, `PlacementPanel.placeCurrentShipAt`; UI dễ lệch trạng thái server |
| P1 | Đóng cửa sổ đặt tàu chưa có xử lý rời trận; về sảnh sau trận chỉ đổi UI | `ClientController`; server có thể còn giữ phiên cũ, rematch hoặc tài nguyên |
| P1 | I/O gửi/đóng socket có thể chạy trên EDT; đóng reader trước socket | `ClientController`, `SocketClient`; cần kiểm tra nguy cơ treo khi reader đang chờ hoặc peer không đọc |
| P1 | Chưa có bộ test tự động trong cây file đã kiểm tra; README tuyên bố hoàn tất 100% | Cần thay tuyên bố bằng phạm vi và kết quả kiểm thử thực tế |

## 3. Quyết định thiết kế áp dụng khi triển khai

Các quyết định này giải quyết chỗ tài liệu chưa thống nhất. GPT thực hiện cần ghi chúng vào đặc tả chung trước khi sửa đồng loạt client/server. Nếu người dùng đưa thêm yêu cầu, cập nhật quyết định liên quan và test tương ứng.

| Chủ đề | Chênh lệch nguồn | Quyết định của kế hoạch |
| --- | --- | --- |
| Framing TCP | Phần I mục 4.2 của báo cáo dùng phần độ dài; quy tắc repository, Markdown và code dùng JSON theo dòng | Giữ JSON UTF-8 kết thúc bằng `\n` theo quy tắc dự án. Ghi rõ sai khác với báo cáo; không triển khai hai kiểu framing cùng lúc |
| MessageType | Word có `LOGIN_REQUEST`, `SHOT_REQUEST`, `GAME_START`; code có `LOGIN_REQ`, `FIRE_REQ`, `MATCH_START` | Giữ tên hiện hành, thêm bảng ánh xạ tên trong Word sang tên cài đặt; chỉ thêm loại tin còn thiếu |
| Payload | Word dùng `payload`, `row/col`; code dùng `data`, `x/y` | Giữ `data`, quy ước `x = col`, `y = row`, chỉ số 0..9. Dùng snake_case trên wire để khớp dự án |
| Timeout | Code tự bắn ngẫu nhiên; báo cáo Khoa mục II.6 và KHOA-05 yêu cầu chuyển lượt | Chọn mất lượt: hết 15 giây thì chuyển cho đối thủ, không tạo phát bắn và không tăng shot count. Sửa mô tả auto-fire trong Markdown/UI cho thống nhất |
| Phiên trùng | Code thay phiên cũ; báo cáo yêu cầu giữ phiên cũ | Từ chối phiên mới với `ALREADY_LOGGED_IN`; phiên cũ tiếp tục hoạt động |
| Tạo tài khoản | README cho phép tự tạo nếu tên chưa có | Giữ hành vi này, nhưng bắt buộc username/password hợp lệ, mật khẩu không rỗng; không tự thay mật khẩu tài khoản đã có |
| Trạng thái | Word có FREE/QUEUED/IN_ROOM/IN_MATCH; UI dùng IDLE/BUSY | Server lưu lifecycle chi tiết; presence công khai ánh xạ IDLE/BUSY/OFFLINE và có thể thêm `phase`. Queue cũng là BUSY để không bị mời/ghép chồng |
| Thoát lúc đặt tàu | Đề bài xác định thua khi đang chơi; chưa nói rõ trước khi bắt đầu | PLACING thì hủy chuẩn bị, không cộng điểm và không tính trận. RUNNING thì thoát/ngắt mạng bị xử thua. FINISHED thì rời sảnh không chấm điểm lại |
| Rematch | Một số Markdown nói reset trận; đề bài và Word nói trận mới | Có thể giữ phòng nếu hai bên còn kết nối, nhưng tạo `MatchSession` và `match_id` mới. Không hồi sinh đối tượng trận đã kết thúc |
| Hủy Ready | Báo cáo Hải có `ready=true/false`; code chỉ có Ready | Hỗ trợ hủy khi còn PLACING và chưa chuyển sang RUNNING; sửa tàu chỉ sau khi server xác nhận đã hủy Ready |
| Java | README hứa Java 8+, code có API mới hơn | Giữ tương thích Java 8 bằng thay hai chỗ đọc file và tránh API mới. Kiểm tra bằng `--release 8` trên JDK hỗ trợ tùy chọn đó |

Thông số mới do kế hoạch đề xuất, không phải số liệu bắt buộc trong đề bài: invitation và chờ rematch 30 giây; heartbeat mỗi 5 giây, hết hạn sau 20 giây không nhận tín hiệu hợp lệ; giới hạn một khung JSON 64 KiB. Đưa vào cấu hình và cho test thay bằng giá trị ngắn hơn. Timer chiến đấu vẫn là 15 giây.

## 4. Hợp đồng chung cần hoàn thiện trước

### Thông điệp

Giữ `type`, `status`, `message`, `data`; bổ sung trường envelope `request_id`, `error_code`, `server_time_ms`. Phản hồi trực tiếp lặp lại `request_id`; event phòng/trận phải có `room_id` hoặc `match_id` trong `data`. Server xác định user từ session, không nhận danh tính người gửi do payload tự khai.

Các trường và loại tin dự kiến bổ sung:

- `HEARTBEAT_REQ`, `HEARTBEAT_RESP`; trước login chỉ cho login và heartbeat.
- `PLACE_SHIPS_RESP`, `PLAYER_READY_RESP`, `READY_STATE_NOTIFY` để xác nhận đặt tàu, Ready/hủy Ready và trạng thái của hai bên.
- `LEAVE_ROOM_RESP`, `QUEUE_JOIN_RESP`, `QUEUE_LEAVE_RESP`; `JOIN_ROOM_RESP` có cả nhánh thành công và lỗi.
- `INVITE_REQ` trả xác nhận nhận yêu cầu; event và phản hồi invitation mang `invitation_id`, `expires_at_ms`.
- `MATCH_START`, `TURN_CHANGE` mang `match_id`, `turn_id`, người có lượt, `deadline_ms` và thời gian server. `turn_id` tăng sau mọi phát bắn hợp lệ, kể cả Hit/Sunk giữ người bắn, và sau timeout.
- `FIRE_REQ` mang `match_id`, `turn_id`, `x`, `y`. `FIRE_RESULT` nêu lượt đã xử lý; `TURN_CHANGE` nêu lượt tiếp theo. Yêu cầu đến sau deadline bị từ chối; thao tác timeout được xử lý một lần.
- Rematch và rời phòng phải chỉ rõ phòng/trận cũ. `REMATCH_START` mang mã trận mới ngay từ giai đoạn đặt tàu.
- Chuẩn hóa theo trường code hiện dùng như `sunk_ship_coords`, `player1_shots`, `player2_shots`; cập nhật các ví dụ Markdown đang dùng tên khác.

Mã lỗi tối thiểu: `INVALID_MESSAGE`, `UNKNOWN_TYPE`, `UNAUTHENTICATED`, `AUTH_FAILED`, `ALREADY_LOGGED_IN`, `INVALID_STATE`, `ROOM_NOT_FOUND`, `ROOM_FULL`, `INVITE_EXPIRED`, `INVALID_FLEET`, `OUT_OF_BOUNDS`, `OVERLAP`, `DUPLICATE_SHIP_ID`, `NOT_READY`, `NOT_YOUR_TURN`, `ALREADY_SHOT`, `STALE_MATCH`, `STALE_TURN`, `TURN_EXPIRED`, `STORAGE_ERROR`.

Các lệnh thay đổi trạng thái có cơ chế chống thực hiện hai lần theo session và `request_id`, với bộ nhớ giới hạn. Yêu cầu lặp trả lại phản hồi đã ghi nhận; không phát lại tác dụng phụ. Cùng requestId nhưng nội dung khác phải bị từ chối. Các bất biến trong manager vẫn phải đúng kể cả client gửi requestId mới liên tục.

### Trạng thái và đồng thời

Lifecycle đề xuất: `OFFLINE -> IDLE -> QUEUED hoặc IN_ROOM -> PLACING -> READY_WAITING -> IN_BATTLE -> POST_GAME -> IDLE`. Match có `PLACING`, `RUNNING`, `FINISHED`, `CANCELLED`; Ready là trạng thái riêng của từng người trong PLACING.

Một user thuộc tối đa một room/queue/match đang hoạt động. Tạo cặp, nhận Accept, join phòng và cleanup phải dùng cùng cơ chế đồng bộ cho registry liên quan. Các thay đổi một Match đi qua khóa của Match. Không giữ khóa nghiệp vụ khi ghi socket hoặc gọi chéo manager có thể lấy khóa theo thứ tự ngược; tạo snapshot event trong khóa rồi đưa vào hàng đợi gửi theo thứ tự.

Server chỉ gửi board của chính người chơi nếu cần, các ô đã bắn và tàu đã chìm. Không đưa tọa độ tàu chưa bị lộ của đối phương vào payload, log gửi client hoặc dữ liệu UI.

## 5. Các bước triển khai

Thực hiện theo thứ tự P0 đến P8. Mỗi bước gồm mã nguồn, test cho hành vi đã thay đổi và cập nhật checklist. Không tích lũy toàn bộ test đến cuối. Các tên lớp mới bên dưới là gợi ý tổ chức; ưu tiên sửa nhỏ trong cấu trúc hiện có.

### P0 Kiểm tra baseline và khóa đặc tả

- [x] Đọc lại nguồn ở mục 1, kiểm tra `git status`, giữ nguyên thay đổi của người dùng và bốn báo cáo Word đang untracked tại thời điểm lập kế hoạch.
- [x] Chạy biên dịch baseline; ghi JDK, lệnh và lỗi thực tế vào phần kiểm thử của `TASKS_AND_MEMBERS.md`.
- [x] Cập nhật `NETWORK_PROTOCOL.md`, `GAME_RULES.md`, `SYSTEM_ARCHITECTURE.md` theo mục 3–4; bổ sung nguồn Word, yêu cầu, module và test tương ứng vào `TASKS_AND_MEMBERS.md`.
- [x] Chuẩn bị test runner Java thuần trong `tests/` và `run_tests.ps1`: có assertion thực sự, timeout và exit code khác 0 nếu thất bại. Dùng thư mục dữ liệu tạm và cổng riêng; không sửa `data/users.json` để chạy test.
- [x] Sửa tương thích Java 8, chẳng hạn dùng `Files.readAllBytes(Path)` thay API trên `FileInputStream`.

Nghiệm thu: đặc tả có bảng message, state, lỗi và timeout thống nhất; build thường và kiểm tra `--release 8` qua; test runner chạy được một test có ý nghĩa và không phụ thuộc dữ liệu thật.

### P1 Giao thức và kết nối TCP

Phụ thuộc P0. File chính: `Message.java`, `MessageType.java`, `JsonUtil.java`, `ClientHandler.java`, `SocketClient.java`, `GameServer.java`.

- [x] Bổ sung envelope, loại tin và validation schema đã chốt. Tọa độ/size phải là số nguyên đúng kiểu; không mặc định trường thiếu về `(0,0)` hoặc cắt số thập phân thành int.
- [x] Giữ framing theo dòng nhưng giới hạn độ dài trong lúc đọc, không chờ đọc xong mới kiểm tra; xử lý EOF giữa khung, UTF-8, escape, JSON hỏng, kiểu tin lạ và dữ liệu thừa sau JSON.
- [x] Tuần tự hóa việc gửi của từng connection; có giới hạn hàng đợi và phát hiện peer không đọc/lỗi ghi. Không để một client chậm chặn timer hay các trận khác.
- [x] Đưa connect/send/close ra khỏi EDT; có timeout kết nối. Đóng socket để giải phóng vòng đọc đang chờ trước khi thu hồi reader/writer.
- [x] Thêm heartbeat và cleanup idempotent; mỗi connection có vòng đời riêng để callback kết nối cũ không đóng nhầm lần kết nối mới.
- [x] Khi stop server, đóng listener, client sockets, scheduler và executor đang thuộc server.

Nghiệm thu: nhiều JSON trong một lần ghi, một JSON chia nhiều lần ghi, tiếng Việt, khung thiếu/hỏng/quá dài và peer chậm không làm server treo; mất kết nối im lặng được dọn trong giới hạn heartbeat; shutdown không để thread sống ngoài ý muốn.

### P2 Tài khoản và presence của Trần Tuấn Dương

Phụ thuộc P1. File chính: `UserManager.java`, `StorageManager.java`, `User.java`, `ClientHandler.java`, `LoginFrame.java`, `ClientController.java`.

- [x] Chặn lệnh nghiệp vụ trước login và sau logout. Không cho đổi sang tài khoản khác bằng login lại trên socket đã xác thực.
- [x] Kiểm tra username/password, loại bỏ nhánh bỏ qua mật khẩu rỗng. Giữ quy tắc tên tài khoản không phân biệt hoa thường như storage hiện tại, dùng chuẩn hóa nhất quán.
- [x] Lưu mật khẩu bằng hàm dẫn xuất có salt, ví dụ PBKDF2 qua Java SE, có trường thuật toán/tham số/phiên bản; không tự thiết kế thuật toán mật mã. Migration tài khoản cũ giữ nguyên ID, điểm và lịch sử, có bản sao lưu; không in password/hash vào log.
- [x] Đăng nhập trùng phải bị từ chối nguyên tử; logout chỉ xóa ánh xạ nếu đúng handler đang sở hữu session.
- [x] Hợp nhất logout, đóng app, EOF và heartbeat timeout vào một đường cleanup; phát OFFLINE một lần sau khi xử lý room/match.
- [x] Trả snapshot online sau login, đồng bộ điểm, số thắng và trạng thái khi có thay đổi; thông tin tài khoản cá nhân lấy từ server.

Nghiệm thu: DUONG-01 đến DUONG-06; thêm mật khẩu rỗng, login lại trên cùng socket, hai login trùng đồng thời và logout khi đang đấu. Ba client phải thấy trạng thái nhất quán; phiên cũ không bị đẩy ra khi phiên mới đăng nhập trùng.

### P3 Lobby và ghép trận của Nguyễn Ngọc Bảo

Phụ thuộc P2. File chính: `LobbyManager.java`, `Room.java`, `MatchManager.java`, `LobbyFrame.java`, `ClientController.java`; thêm model invitation nếu cần.

- [x] Cả bốn cách vào trận đều đi qua một thao tác tạo cặp hợp lệ, tạo room và session trước khi gửi event chuyển màn hình.
- [x] Kiểm tra cả hai user online, rảnh, khác nhau; ngăn tự join phòng mình, join khi đang đấu và người thứ ba vào phòng đầy.
- [x] Queue không trùng user; khi loại client chết phải giữ người còn hợp lệ trong hàng đợi. Hủy queue và ghép trận phải có kết quả xác định theo thứ tự server xử lý.
- [x] Lưu invitation gồm id, sender, receiver, thời hạn và trạng thái. Chỉ receiver được phản hồi invitation tồn tại; Accept kiểm tra lại cả hai bên trong thao tác nguyên tử; Reject/hết hạn/disconnect xóa đúng invitation.
- [x] Accept thành công vô hiệu hóa invitation khác liên quan để không tạo hai trận cho cùng người. Cleanup đồng thời không xóa ánh xạ mới bằng tham chiếu phiên cũ.
- [x] Có nút hủy chờ, rời phòng, phản hồi rõ mã sai/phòng đầy/lời mời hết hạn; cập nhật presence khi vào và ra queue/room.

Nghiệm thu: BAO-01 đến BAO-06; thêm tự mời, tự join, Accept giả hoặc lặp, hai lời mời giao nhau, một người đồng thời queue và nhận Accept. Bốn client ghép thành đúng hai cặp, không ai thuộc hai trận.

### P4 Đặt tàu và Ready của Ngô Thanh Hải

Phụ thuộc P3. File chính: `Board.java`, `Ship.java`, `MatchSession.java`, `MatchManager.java`, `PlacementPanel.java`, `BoardGridPanel.java`, `ClientController.java`.

- [x] Validate board tạm: đúng năm tàu với kích thước `[5,4,3,3,2]`, ID riêng biệt, tọa độ nguyên trong biên, thẳng hàng liên tục, không trùng ô. Tàu sát cạnh nhau vẫn hợp lệ.
- [x] Chỉ commit board khi hợp lệ và session cho phép. Payload sai không thay board đã chấp nhận; người ngoài trận và lệnh trận cũ bị từ chối.
- [x] Ready phía server chỉ thành công khi đã có board hợp lệ; client đợi `PLACE_SHIPS_RESP` trước khi gửi Ready. Nếu server từ chối, mở lại thao tác sửa và hiển thị lý do.
- [x] Ready/hủy Ready được đồng bộ cho hai bên; sau Ready khóa cả tương tác click bàn cờ, không chỉ khóa nút. Khi cả hai Ready, chuyển PLACING -> RUNNING đúng một lần.
- [x] Gửi lặp Ready không chọn lại người đi trước, không reset thời gian bắt đầu hoặc timer.
- [x] Sửa tàu trên client theo cách kiểm tra vị trí mới trước rồi mới bỏ vị trí cũ; hỗ trợ đổi vị trí, xóa/reset và random hợp lệ. Rời màn hình đặt tàu phải thông báo server.

Nghiệm thu: HAI-01 đến HAI-06; thêm ID trùng, tàu đứt đoạn/chéo, thiếu tọa độ, số thập phân, Ready không đặt tàu, sửa tàu sau Ready và hai Ready đồng thời. Mỗi client nhận một `MATCH_START`; không có board đối thủ trong traffic.

### P5 Chiến đấu và timer của Dương Đăng Khoa

Phụ thuộc P4. File chính: `MatchSession.java`, `MatchManager.java`, `GameConfig.java`, `BattlePanel.java`, `ClientController.java`.

- [x] Khi bắn kiểm tra session, RUNNING, membership, matchId, turnId, lượt, deadline, biên và lịch sử. Yêu cầu không hợp lệ không đổi board, lượt, số phát bắn hoặc thời hạn hiện tại.
- [x] Giữ luật Miss đổi lượt, Hit/Sunk giữ lượt; mỗi phát bắn hợp lệ chưa kết thúc trận tạo lượt xử lý mới với 15 giây. Đủ 17 ô bị phá hủy thì dừng, không phát thêm lượt.
- [x] Dùng deadline server và đồng hồ đơn điệu cho quyết định timeout; clock/scheduler/random có thể thay trong test. Gửi deadline epoch và thời gian server cho UI ước lượng thời gian còn lại.
- [x] Callback timer giữ matchId và turnId khi được tạo, chỉ tác động nếu vẫn là lượt đó. Hủy future không thay thế được kiểm tra này.
- [x] Hết hạn thì mất lượt, phát `TIMEOUT_NOTIFY` và `TURN_CHANGE` một lần; không bắn hộ. Định nghĩa rõ thứ tự xử lý khi FIRE và timeout cùng đến: khóa và thời gian server quyết định, lệnh sau hạn không được chấp nhận.
- [x] Tập trung kết thúc trận qua một đường xử lý idempotent cho hết tàu, surrender, logout và disconnect; phân biệt `SURRENDER` với `DISCONNECTED`.

Nghiệm thu: KHOA-01 đến KHOA-06; thêm phát cuối cùng chìm hạm đội, Hit tại sát deadline, callback lượt cũ, FIRE đến sau hạn trước khi scheduler chạy và surrender trùng disconnect. Một tác động hợp lệ cho mỗi lượt, không cộng điểm hai lần.

### P6 Lịch sử, xếp hạng và chơi lại

Phụ thuộc P5. File chính: `StorageManager.java`, `MatchRecord.java`, `UserManager.java`, `MatchManager.java`, `MatchSession.java`, `LeaderboardDialog.java`.

- [x] Lưu đủ matchId, hai người, winner/loser, startedAt/endedAt, duration, số phát bắn mỗi bên, điểm mỗi bên và lý do kết thúc. Đọc lại timestamp gốc; bản ghi cũ thiếu trường phải được đánh dấu/đọc tương thích, không bịa thời gian mới.
- [x] Một thao tác storage cập nhật kết quả và điểm cùng nhau, chống lặp theo matchId. Phương án Java thuần đề xuất: snapshot JSON có phiên bản chứa cả users và matches, ghi file tạm rồi thay thế nguyên tử trên cùng filesystem. Giữ bản sao lưu và báo lỗi nếu không thể bảo đảm cách ghi đã chọn.
- [x] Import `users.json`/`matches.json` cũ khi chưa có snapshot mới; giữ nguyên thống kê sẵn có và file nguồn. Không tính lại toàn bộ điểm từ lịch sử có thể chưa đầy đủ; kiểm tra và báo bản ghi trùng/mâu thuẫn.
- [x] Nếu ghi thất bại, không phát thông báo đã lưu thành công và không áp dụng điểm lần hai khi thử lại. Giữ kết quả ở trạng thái chờ lưu, báo lỗi rõ; có test lỗi ghi và khởi động lại.
- [x] Xếp hạng theo điểm, thắng, tỷ lệ thắng giảm dần; không có trận thì tỷ lệ 0; đồng hạng dùng username rồi ID cho thứ tự ổn định.
- [x] Sau kết thúc giữ ngữ cảnh POST_GAME có thời hạn nếu cả hai còn ở phòng. Hai bên đồng ý thì `MatchManager` tạo phiên mới và mã mới; từ chối/thoát/hết hạn thì dọn ngữ cảnh, trả user về IDLE.
- [x] Rematch không dùng lại scheduler đã shutdown; timer dùng chung do server quản lý hoặc scheduler mới được thu hồi đúng vòng đời. Dọn map và future của phiên cũ, không xóa nhầm phiên mới.

Nghiệm thu: KHOA-07, INT-06/07, TC-08/09/10; hai ván liên tiếp có hai matchId và timer hoạt động ở ván hai. Restart giữ nguyên điểm và lịch sử. Lặp kết thúc cùng matchId không cộng thêm điểm. Rời/hết hạn rematch không để người chơi mắc BUSY.

### P7 Hoàn thiện luồng giao diện

Phụ thuộc P1–P6; cập nhật UI liên quan ngay ở từng bước trước, dùng P7 để kiểm tra toàn luồng. File chính: `ClientController.java`, `LoginFrame.java`, `LobbyFrame.java`, `PlacementPanel.java`, `BattlePanel.java`, `LeaderboardDialog.java`.

- [x] Dùng session/matchId/turnId và requestId để bỏ sự kiện cũ, tránh popup/điểm bị xử lý lặp và không chuyển về trận đã rời.
- [x] Nút theo đúng trạng thái: hủy queue, rời phòng, đặt tàu, chờ xác nhận, Ready/hủy Ready, chọn ô rồi Bắn, đầu hàng và kết quả.
- [x] Có cờ chờ kết quả bắn để không gửi nhiều phát khi người dùng click liên tục; lỗi server mở lại thao tác phù hợp mà không tự reset timer.
- [x] Đồng hồ hiển thị tính theo thời hạn server, không chỉ trừ một biến mỗi giây. UI không tự đổi lượt hoặc quyết định kết quả khi về 0.
- [x] Dừng Swing Timer, bỏ listener và xóa trạng thái bàn cờ cũ khi rời màn hình, mất kết nối hoặc chuyển ván; xử lý nút đóng cửa sổ ở cả đặt tàu lẫn chiến đấu.
- [x] Điểm/số trận trên sảnh lấy snapshot server. Các dialog không làm mất event đến trong lúc mở; popup lời mời đã hết hạn không thể tạo trận.

Nghiệm thu: thực hiện luồng login -> mời/tạo/join/queue -> đặt tàu -> đấu -> kết quả -> rematch/thoát -> xếp hạng bằng client Swing thật. Không treo UI khi server ngắt, kết nối thất bại hoặc đối phương rời trận.

### P8 Kiểm thử tích hợp và tài liệu bàn giao

Phụ thuộc P7.

- [x] Chạy bộ test mục 6 bằng dữ liệu tạm; cập nhật phần kiểm thử trong `TASKS_AND_MEMBERS.md` với lệnh, kết quả và phần chưa kiểm tra.
- [x] Chạy ít nhất ba client cho presence và bốn client cho hai trận độc lập; kiểm tra một trận timeout/thoát không ảnh hưởng trận kia.
- [x] Môi trường một máy: đã ghi LAN hai máy NOT-RUN; không suy ra từ localhost.
- [x] Cập nhật README, scripts compile/chạy/test, kiến trúc, luật, giao thức và phân công sao cho khớp bản cuối. Scripts chạy từ thư mục khác vẫn tìm đúng project, trả exit code đúng và không pause trong chế độ test.
- [x] Bổ sung hướng dẫn demo vào `README.md`, gồm các bước, vai trò client, dữ liệu mẫu, kết quả cần quan sát và xử lý tình huống không kết nối được.
- [x] Rà lại diff; chỉ giữ thay đổi thuộc phạm vi, không commit/push tự động. Ghi tổng kết file đổi, test đã chạy và giới hạn còn lại.

Nghiệm thu: người khác làm theo README để build, chạy server, mở nhiều client và thực hiện demo được; tài liệu không còn tuyên bố tính năng đã hoàn tất nếu chưa có bằng chứng.

## 6. Bộ kiểm thử bắt buộc

### Đối chiếu với tài liệu gốc

| Bộ test | Phạm vi | Bước chịu trách nhiệm |
| --- | --- | --- |
| DUONG-01..06 | Login đúng/sai, phiên trùng, ba client, mất mạng, presence | P1–P2 |
| BAO-01..06 | Invite Accept/Reject, mã sai, tranh chỗ cuối, ghép bốn người, hủy queue | P3 |
| HAI-01..06 | Bộ tàu đúng/sai, biên, overlap, một Ready, hai Ready đồng thời | P4 |
| KHOA-01..07 | Miss/Hit/Sunk, bắn trùng/sai lượt, timeout, surrender, xếp hạng | P5–P6 |
| INT-01..07 | Tích hợp các module theo phần I của báo cáo Word | P8 |
| TC-01..10 | Các kịch bản trong `TASKS_AND_MEMBERS.md`, bao gồm rematch | P8 |

### Các ca hồi quy bổ sung

| Mã | Kịch bản | Kết quả bắt buộc |
| --- | --- | --- |
| REG-01 | Split/coalesce TCP, tiếng Việt, JSON có escape | Không mất, nhập nhằng hoặc lặp message |
| REG-02 | JSON hỏng, thiếu trường, số thập phân thay tọa độ, khung quá dài | Từ chối có kiểm soát, server còn phục vụ client khác |
| REG-03 | Mật khẩu rỗng, lệnh trước login/sau logout, login đổi user trên socket | Không vượt qua xác thực hoặc để phiên ma |
| REG-04 | Hai login trùng đồng thời; callback cleanup phiên cũ | Một chủ session, không xóa phiên hợp lệ |
| REG-05 | Tự join, Accept giả/lặp/hết hạn, queue cạnh tranh invitation | Không tạo cặp với chính mình, không có user thuộc hai trận |
| REG-06 | Queue có client chết bên cạnh client hợp lệ | Người hợp lệ không bị mất khỏi queue |
| REG-07 | Board sai sau board đúng, Ready không board, Ready lặp | Board đúng không bị hủy, chỉ bắt đầu một lần |
| REG-08 | Sửa tàu sau Ready hoặc RUNNING, payload trận cũ | Server từ chối; dữ liệu trận không đổi |
| REG-09 | FIRE và timeout sát nhau, callback timer cũ | Chỉ xử lý theo đúng lượt/deadline; không bắn hoặc đổi lượt hai lần |
| REG-10 | Bắn lỗi/lặp không đúng lượt | Không reset timer, tăng shot count hoặc thay đổi board |
| REG-11 | Phát cuối, surrender và disconnect cạnh tranh | Một kết quả, một lần tính trận và điểm |
| REG-12 | Disconnect ở queue/room/PLACING/RUNNING/POST_GAME | Cleanup đúng từng giai đoạn; PLACING không bị tính trận |
| REG-13 | Rematch liên tiếp, từ chối, hết hạn và thoát về sảnh | Phiên/matchId mới, timer hoạt động, không còn ngữ cảnh cũ |
| REG-14 | Restart, JSON lỗi, ghi thất bại, import dữ liệu cũ | Không ghi đè âm thầm bằng dữ liệu mẫu; giữ số liệu, báo lỗi rõ |
| REG-15 | Hai trận song song và một client không đọc dữ liệu | Trận còn lại hoạt động; không deadlock hoặc tăng thread vô hạn |
| REG-16 | Nghe traffic của từng client trong giai đoạn đặt tàu/đấu | Không có vị trí tàu đối thủ chưa bị lộ |
| REG-17 | Đóng cửa sổ, server chết, reconnect và bấm nhanh trên Swing | UI phản hồi, không dùng callback/timer cũ hoặc gửi lặp tác dụng phụ |

Test domain dùng board cố định, clock/random giả và assertion trên trạng thái/event. Test tích hợp dùng socket thật để gửi cả các lệnh mà UI bình thường không cho phép. Test race dùng latch/barrier và hạn chờ rõ ràng, tránh chỉ `sleep` rồi hy vọng tái hiện. Ít nhất một smoke test phải dùng cấu hình timeout thật 15 giây. GUI và LAN ghi riêng với test tự động.

## 7. Điều kiện hoàn thành và bàn giao

- [x] Build thành công; kiểm tra API Java 8 thành công. Nếu không có runtime Java 8 để chạy, ghi rõ đã kiểm tra biên dịch tương thích, không tuyên bố đã chạy trên Java 8.
- [x] Năm nguồn Word được ánh xạ sang chức năng và test; các sai khác framing/tên tin/timeout được giải thích công khai trong đặc tả.
- [x] Toàn bộ ca tài liệu gốc và hồi quy có trạng thái pass/fail/not-run, kèm bằng chứng thích hợp. Không đánh dấu pass bằng suy luận từ đọc code.
- [x] Không còn lỗi P0 ở mục 2; tài khoản, queue, room, match, timer và cleanup nhất quán.
- [x] Chơi hoàn chỉnh và rematch được; kết quả lưu đúng một lần, dữ liệu giữ nguyên sau restart.
- [x] `README.md`, `NETWORK_PROTOCOL.md`, `SYSTEM_ARCHITECTURE.md`, `GAME_RULES.md`, `TASKS_AND_MEMBERS.md` khớp code cuối.

Giữ ánh xạ trách nhiệm trong báo cáo: Dương phụ trách kết nối/xác thực/presence; Bảo phụ trách lobby; Hải phụ trách board/Ready; Khoa phụ trách combat/timer/kết quả/xếp hạng. Protocol, storage liên module và kiểm thử tích hợp là phần phối hợp chung. Một GPT có thể triển khai tuần tự toàn bộ kế hoạch này, không cần chia thành nhiều agent.

## 8. Prompt bàn giao cho GPT 6.1 Sol

Sao chép đoạn dưới vào phiên GPT 6.1 Sol có quyền truy cập repository này:

```text
Hãy triển khai kế hoạch trong PLAN_GPT_6_1_SOL.md cho dự án Battleship Online hiện có.

Trước khi sửa, đọc kế hoạch, .agents/rules/battleship_rules.md, các tài liệu Markdown được dẫn và kiểm tra git status. Đối chiếu năm file .docx khi cần làm rõ nguồn yêu cầu. Giữ các thay đổi của người dùng và tài liệu Word gốc.

Thực hiện tuần tự P0 đến P8, bắt đầu từ lỗi P0. Giữ Java SE, Swing, TCP JSON UTF-8 theo dòng và tương thích Java 8. Áp dụng các quyết định tại mục 3–4, đặc biệt: không đẩy phiên đăng nhập cũ ra, Ready phải được server xác nhận trên board hợp lệ, hết 15 giây thì mất lượt, rematch tạo MatchSession và matchId mới.

Với mỗi bước, sửa mã nguồn, bổ sung test có ý nghĩa cho hành vi thay đổi, chạy kiểm tra liên quan rồi cập nhật checklist và phần kiểm thử trong TASKS_AND_MEMBERS.md bằng kết quả thật. Gộp hướng dẫn demo vào README.md, không tạo thêm file Markdown ngoài các tài liệu hiện có. Dùng dữ liệu tạm và cổng riêng khi test. Không chuyển stack hoặc dựng lại toàn bộ dự án. Không tự sửa báo cáo Word, xóa dữ liệu thật, commit hoặc push.

Tiếp tục đến khi đáp ứng mục 7. Nếu gặp trở ngại môi trường, ghi rõ bước chưa thể kiểm chứng và hoàn thành các phần độc lập còn lại. Không đánh dấu hoàn tất hoặc test pass khi chưa thực hiện.

Kết thúc bằng báo cáo ngắn: chức năng/lỗi đã sửa, các file chính đã thay đổi, lệnh kiểm tra và kết quả, cách chạy server + nhiều client, cùng giới hạn còn lại.
```
