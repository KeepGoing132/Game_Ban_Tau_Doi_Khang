# Luật Battleship Online

Đặc tả triển khai thống nhất theo đề bài BANTAUDOIKHANG.docx, bốn báo cáo và quyết định tại PLAN_GPT_6_1_SOL.md, cập nhật 04/10/2026.

## Bàn cờ và đặt tàu

Bàn 10×10, `x=col`, `y=row`, chỉ số 0..9. Bộ tàu đúng `[5,4,3,3,2]`, tổng 17 ô. ID tàu riêng biệt; tọa độ nguyên, thẳng ngang/dọc và liên tục; không vượt biên hoặc chồng ô. Tàu sát nhau vẫn hợp lệ. Server validate board tạm rồi mới thay board đã chấp nhận; board sai không làm mất bố trí cũ.

Match bắt đầu ở PLACING. Client gửi PLACE_SHIPS_REQ, đợi PLACE_SHIPS_RESP rồi mới PLAYER_READY_REQ với ready=true. Ready chỉ thành công nếu board hợp lệ; sau Ready khóa sửa tàu và click board. Có thể ready=false khi còn PLACING, chỉ mở sửa sau xác nhận server. Hai Ready chuyển RUNNING một lần; Ready lặp không chọn lại người đi trước/reset timer. Người đi trước được chọn ngẫu nhiên 50/50.

## Lượt bắn và timeout

Mỗi lượt xử lý có match_id, turn_id và deadline server, mặc định 15 giây. FIRE phải đúng trận, lượt, người có lượt, trước deadline và tọa độ chưa bắn trong biên. Yêu cầu sai không thay board, deadline hoặc shot count.

- MISS chuyển cho đối thủ.
- HIT/SUNK giữ người bắn; lượt xử lý mới có turn_id tăng và 15 giây mới.
- SUNK chỉ công khai tọa độ tàu đã chìm; server không gửi tàu đối thủ còn bí mật.
- Hết hạn mất lượt và gửi TIMEOUT_NOTIFY + TURN_CHANGE, không bắn hộ, không tăng số phát bắn.
- FIRE tại/sau deadline bị TURN_EXPIRED nếu vẫn là lượt đó; yêu cầu mang lượt cũ bị STALE_TURN. Khóa match và đồng hồ đơn điệu quyết định thứ tự; callback lượt cũ không tác động.
- Phát cuối phá đủ 17 ô kết thúc ngay, không tạo thêm lượt.

Quyết định mất lượt thay mô tả auto-fire cũ, phù hợp KHOA-05 trong Word. Chỉ server quyết định kết quả; đồng hồ UI dựa trên deadline và không tự đổi lượt khi về 0.

## Kết thúc, thoát và chơi lại

| Giai đoạn | Thoát/đóng cửa sổ/ngắt mạng |
| --- | --- |
| PLACING | Hủy chuẩn bị, không tính trận/điểm |
| RUNNING | Người thoát thua, trừ 3 điểm (không dưới 0) và cấm ghép trận 60 giây; đối thủ thắng +3; reason SURRENDER cho thoát chủ động, DISCONNECTED cho mất kết nối/logout |
| FINISHED/POST_GAME | Dọn ngữ cảnh, không cộng điểm lại |

Kết thúc có một đường xử lý duy nhất. Thắng +3, một trận thắng và một trận chơi; thua do bị bắn chìm hết tàu +0 và một trận chơi. Rời trận đang RUNNING bị trừ `min(3, tổng điểm hiện tại)` và tính một trận chơi. Mỗi trận chỉ xử phạt một lần; yêu cầu thoát lặp không trừ thêm hoặc kéo dài hạn phạt. Server chủ động dừng không phạt người chơi.

Hạn phạt 60 giây tính từ lúc server xác định rời trận, được lưu cùng điểm và lịch sử trong transaction SQLite nguyên tử theo match ID. Đăng xuất, đăng nhập lại hay khởi động lại server không xóa hạn phạt đã lưu. Trong thời gian phạt, server chặn ghép nhanh, tạo/vào phòng, gửi/nhận lời mời và chơi lại; hết hạn tự trở về IDLE. UI hiển thị thời gian còn lại, server quyết định quyền vào trận. Bản ghi cũ không bị phạt hồi tố.

Nếu storage lỗi thì kết quả chờ lưu, không công bố đã lưu/thay đổi điểm thành công; người rời trận vẫn bị chặn ghép cho đến khi lưu thành công và đủ 60 giây. Retry không đặt lại thời điểm hết hạn.

Kết thúc bình thường giữ POST_GAME tối đa 30 giây. Hai bên cùng đồng ý thì tạo MatchSession và match_id mới trong cùng phòng, đặt lại tàu và có timer mới. Từ chối, thoát, mất kết nối hoặc hết hạn đưa cả hai về IDLE. Đầu hàng/ngắt mạng hủy phòng ngay sau xử lý kết quả.

## Luyện tập với máy

Sảnh có Dễ/Vừa/Khó: ngẫu nhiên, săn ô xen kẽ/ô cạnh điểm trúng, tính xác suất vị trí tàu. Máy đặt tàu hợp lệ ngẫu nhiên, không biết tàu của người chơi; theo cùng luật bắn và timeout. Trận luyện tập không cộng/trừ điểm, không tăng thống kê xếp hạng và không phạt rời trận. Người đang bị phạt online vẫn phải hết hạn trước khi vào luyện tập.

## Lobby và xếp hạng

Bốn lối vào trận: mời trực tiếp Accept/Reject, tạo phòng, join mã sáu ký tự, queue tự ghép. Một người chỉ ở một queue/phòng/trận. Queue là BUSY; chỉ IDLE được mời/tạo/join. Không tự mời/tự join. Invitation có ID và hạn 30 giây; Accept kiểm tra lại cả hai người và chỉ receiver được phản hồi.

Xếp hạng: total_points giảm dần, total_wins giảm dần, tỷ lệ wins/games giảm dần; không có trận thì tỷ lệ 0. Nếu còn bằng, username rồi user ID tăng dần. UI hiển thị phần trăm, so sánh thứ hạng dùng tỷ lệ chưa làm tròn.

Heartbeat mặc định mỗi 5 giây; không nhận message hợp lệ 20 giây thì cleanup theo giai đoạn trên. Server xác định người gửi từ session, không tin user ID do client tự khai.
