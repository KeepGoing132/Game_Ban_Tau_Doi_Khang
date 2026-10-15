# Giao diện Battleship — Naval Command

Giao diện Java Swing thống nhất với nền xanh tím, biển xanh, tàu tím, điểm nhấn ngọc lam/cam, trạng thái cảnh báo màu hổ phách và kết quả bắn màu san hô. Minh họa radar và bàn cờ được vẽ bằng Java2D, không cần thư viện hay tải tài nguyên từ Internet.

## Luồng chơi

1. **Đăng nhập:** nhập máy chủ, cổng, tên chỉ huy và mật khẩu. Nhãn và ô nhập được căn cùng lề, form cân giữa theo chiều dọc. Có kiểm tra dữ liệu ngay trên form và nút hiện mật khẩu; bỏ phần tài khoản mẫu cùng dòng chú thích bên dưới. Nhấn Enter để đăng nhập. Tên mới được server tạo thành tài khoản theo quy tắc hiện có.
2. **Phòng chỉ huy:** xem điểm, trận thắng, tỷ lệ thắng và những người chơi khác đang trực tuyến. Chọn ghép trận nhanh, tạo phòng riêng hoặc nhập mã 6 ký tự. Có thể sao chép mã phòng, hủy tìm trận và rời phòng đang chờ. Chỉ người chơi sẵn sàng mới có thể được mời thách đấu.
3. **Triển khai hạm đội:** chọn tàu ở danh sách bên phải, rê chuột lên bàn cờ để xem trước. Ngọc lam là vị trí hợp lệ, đỏ là không hợp lệ. Nhấn **R** hoặc nút hướng để xoay tàu. Đặt xong tự chọn tàu chưa được bố trí; chọn lại tàu để đổi vị trí. Có xếp ngẫu nhiên, đặt lại và tiến độ 0–5 tàu. Nút Sẵn sàng luôn nằm trong vùng thao tác cố định; trạng thái chỉ được xác nhận khi server phản hồi.
4. **Chiến đấu:** bản đồ đang bị bắn chiếm vùng chính; bản đồ còn lại ở ô nhỏ có thể bật/tắt. Đến lượt bạn hiện bản đồ đối thủ; lượt đối thủ hiện bản đồ bạn. Xem số tàu còn lại, lượt chơi và đồng hồ. Chọn ô trên bàn cờ đối thủ, kiểm tra tọa độ rồi nhấn Khai hỏa. Dùng phím mũi tên để di chuyển tiêu điểm trên lưới, Enter/Space để chọn ô. Nhật ký lưu 30 diễn biến gần nhất. Mỗi phát bắn chờ phản hồi trước khi cho phép gửi tiếp. Bắn trúng giữ lượt, trượt đổi lượt; hết giờ chờ server chuyển lượt.
5. **Kết quả:** xem điểm nhận, số phát bắn và độ chính xác; đề nghị chơi lại hoặc về sảnh. Đóng cửa sổ khi đang chiến đấu cần xác nhận rời trận. Bảng xếp hạng có ba vị trí dẫn đầu và bảng thống kê đầy đủ.

Các thông báo thao tác, lỗi xếp tàu và lỗi kết nối xuất hiện ngay trong màn hình liên quan. Màn hình nhỏ sử dụng thanh cuộn ở các vùng phụ, giữ bàn cờ vuông và các hành động chính dễ tiếp cận.

Rời trận đang chiến đấu có xác nhận mức phạt **−3 điểm (không dưới 0), chờ 1 phút**. Sau khi rời trận hoặc đăng nhập lại, sảnh hiển thị đếm ngược màu hổ phách; các nút ghép nhanh, tạo/vào phòng và mời đấu bị khóa đến khi hết hạn. Người khác thấy trạng thái “Đang bị phạt”. Mất kết nối khi đang đấu áp dụng cùng quy tắc; rời màn hình bố trí tàu không bị phạt.

## Khung chat thu gọn

Chọn một tài khoản ở sảnh rồi bấm **Nhắn tin**. Khung chat nổi ở góc dưới phải có danh sách người nhận, bong bóng tin nhắn hai màu, thời gian, ô soạn và nút **Gửi**. Bấm thanh tiêu đề để thu gọn/mở lại; dấu số màu vàng báo tin nhận khi đang thu gọn hoặc đang xem cuộc trò chuyện khác. Chat không tự mở hay cướp tiêu điểm khi nhận tin.

Vào trận với người thật sẽ tự chọn đối thủ và thu gọn khung. Mở lại để trò chuyện trong lúc xếp tàu hoặc chiến đấu; bản đồ giữ nguyên kích thước. Khung chat đi theo cửa sổ khi thay đổi kích thước, giữ bản nháp và lịch sử khi chuyển giữa sảnh và trận. Danh sách có trạng thái online/offline; người đã có lịch sử vẫn có thể được nhắn khi offline.

**Enter** gửi; **Shift+Enter** xuống dòng. Giới hạn 500 ký tự; không gửi rỗng. Phản hồi lỗi xuất hiện ngay dưới ô soạn, giữ bản nháp để gửi lại; di chuột lên thông báo để đọc đầy đủ. **Tin nhắn trước** tải thêm 20 tin mỗi lần. Đăng xuất xóa cache chat của phiên client, lịch sử vẫn được lưu ở SQLite trên server.

Ảnh: [sảnh có chat](docs/ui/chat-lobby.png), [chat trong trận](docs/ui/chat-battle.png), [thu gọn](docs/ui/chat-battle-collapsed.png), [cửa sổ nhỏ](docs/ui/chat-battle-small.png), [lịch sử](docs/ui/chat-history.png).

## Ảnh giao diện thật

Ảnh được render từ Swing trong kiểm thử hai client và TCP server dùng dữ liệu tạm. Tài khoản `gui_a`, `gui_b` là tài khoản kiểm thử.

| Màn hình | Ảnh |
| --- | --- |
| Đăng nhập | [login.png](docs/ui/login.png) |
| Phòng chỉ huy | [lobby.png](docs/ui/lobby.png) |
| Sảnh trong thời gian phạt | [penalty.png](docs/ui/penalty.png) |
| Phòng riêng đang chờ | [room.png](docs/ui/room.png) |
| Triển khai hạm đội | [placement.png](docs/ui/placement.png) |
| Chiến đấu và nhật ký | [battle-active.png](docs/ui/battle-active.png) |
| Kết quả trận | [result.png](docs/ui/result.png) |
| Bảng xếp hạng | [leaderboard.png](docs/ui/leaderboard.png) |

Ảnh cửa sổ nhỏ: [đăng nhập](docs/ui/login-small.png), [sảnh](docs/ui/lobby-small.png), [xếp tàu](docs/ui/placement-small.png), [chiến đấu](docs/ui/battle-small.png).

## Chạy và kiểm tra

```powershell
.\compile.bat
.\run_server.bat
# Chạy trong terminal khác; mở hai client để chơi.
.\run_client.bat

powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Gui -RealTimer
```

Kiểm thử GUI bao gồm: kiểm tra cổng không hợp lệ, đăng nhập, tạo/vào phòng bằng mã chữ thường, mời đấu, tìm/hủy trận, xem trước tàu và xóa preview, xoay tàu, đặt tàu ngoài biên, xếp ngẫu nhiên, đặt lại, sẵn sàng/hủy sẵn sàng, chọn tọa độ, bắn trượt đổi lượt, bắn trúng/đánh chìm, chống gửi lặp, kết quả, chơi lại, xác nhận rời trận, bảng xếp hạng, mất server và kết nối lại.

Kiểm thử phạt rời trận kiểm tra khóa các nút, đếm ngược, đăng xuất/đăng nhập lại, khởi động lại server và mở khóa khi đủ 60 giây bằng đồng hồ kiểm thử. `PenaltyTests` còn kiểm tra trừ điểm về 0, yêu cầu lặp, mất kết nối, lỗi lưu và retry.

`Theme.java` tập trung màu sắc, kiểu chữ, nút, thẻ, bảng và thanh cuộn. `NavalArt.java` chứa minh họa Java2D. Cần build client và server cùng phiên bản để hiển thị đầy đủ hình phạt.

## Bản nâng cấp màu sắc, hiệu ứng và luyện tập

Đã tham khảo mã CSS và ảnh command-deck của StarNet tại commit `54899e5ff3f2b002b457757916b3977818db6fc1`, clone có chọn phần giao diện ở `references/starnet-reference`. Dùng hướng bảng điều khiển và màu nhấn; giao diện Java2D được viết cho Battleship, không chạy shell Tauri hay agent runtime của StarNet. Xem [ghi chú nguồn](THIRD_PARTY_NOTICES.md).

- Bản đồ chính lớn, tự theo mục tiêu đang bị bắn; nút Bản đồ thu nhỏ lưu lựa chọn.
- Bom bay vòng cung, vệt lửa, vòng nổ và mảnh vỡ khi tàu chìm. Kết quả được server xác nhận trước khi trình diễn; nút bắn chặn gửi lặp trong hiệu ứng.
- Âm thanh riêng theo thao tác/sự kiện, có nút tắt trong sảnh và trận.
- Đấu máy mở bảng chọn ba độ khó. Luyện tập không tính điểm/phạt rời trận.
- Tài khoản mở hộp xác nhận nhập mật khẩu để xóa tài khoản hiện tại khỏi database.

Ảnh và bản ghi hiệu ứng: [đấu máy](docs/ui/bot-levels.png), [phòng thủ](docs/ui/defense.png), [ẩn bản đồ nhỏ](docs/ui/no-minimap.png), [bom bay](docs/ui/bomb-flight.png), [tàu chìm](docs/ui/ship-sunk.png), [animation GIF](docs/ui/bomb-animation.gif), [xóa tài khoản](docs/ui/delete-account.png).
