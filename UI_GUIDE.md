# Giao diện Battleship — Naval Command

Giao diện Java Swing thống nhất với nền xanh tím, biển xanh, tàu tím, điểm nhấn ngọc lam/cam, trạng thái cảnh báo màu hổ phách và kết quả bắn màu san hô. Minh họa radar và bàn cờ được vẽ bằng Java2D, không cần thư viện hay tải tài nguyên từ Internet.

**Hai màn hình trong trận được dựng thành cảnh biển động** (xem [mục Ra khơi](#ra-khơi-hiệu-ứng-và-giao-diện-biển-trong-trận)): nền sóng cuộn, hiệu ứng sóng quét khi vào trận, bàn cờ là mặt nước có lưới ánh sáng và sonar, cùng âm thanh sóng biển.

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

## Ra khơi: hiệu ứng và giao diện biển trong trận

Hai màn hình trong trận (**Triển khai hạm đội** và **Chiến đấu**) được dựng như đang ở ngoài biển. Toàn bộ cảnh biển được vẽ bằng Java2D trong `OceanScene.java`, không dùng ảnh hay thư viện ngoài, và chạy theo một đồng hồ chung (`OceanPanel.java`) nên mọi lớp sóng, bọt và ánh sáng luôn khớp nhau.

- **Nền biển sống:** bầu trời chuyển sắc, mặt trời và quầng sáng, mây trôi, đảo xa và thuyền buồm ở đường chân trời, năm lớp sóng cuộn với bọt nước ở từng đỉnh, cột nắng chiếu xuống nước, vệt nắng lấp lánh trên mặt biển, hải âu lượn và bọt khí nổi từ đáy.
- **Hiệu ứng vào trận:** khi màn hình xuất hiện, một con sóng lớn quét qua toàn màn hình kèm bọt và tia nước, đồng thời tấm biển kính trượt vào giữa với tiêu đề **RA KHƠI** (bố trí tàu) hoặc **VÀO TRẬN** (chiến đấu), có la bàn, màn sonar, tàu khu trục chạy ngang để lại vệt bọt và các vòng sonar lan ra. Hiệu ứng kéo dài 2,45 giây, không chặn thao tác: bàn cờ vẫn bấm được, nút Khai hỏa vẫn hoạt động trong lúc sóng đang quét.
- **Bàn cờ là mặt nước:** nền bàn cờ là nước chuyển sắc có đường bình độ hải đồ, hoa la bàn và đường sóng; các ô là những viên kính trong suốt nên thấy được dòng nước chảy bên dưới, kèm lưới ánh sáng (caustics) và vòng sonar quét qua lưới. Bắn trượt để lại vệt nước bắn bốn tia; bắn trúng lóe sáng; tàu chìm tối dần và sủi bọt khí.
- **Hiệu ứng bắn:** quả đạn bay theo vòng cung có khói và vệt lửa, điểm xuất phát có bọt nước; khi chạm: bắn trượt thì **cột nước bắn lên**, vòng bọt lan trên mặt nước và hàng chục giọt nước; bắn trúng thì cầu lửa, mũ bọt trắng, mảnh vỡ và khói; đánh chìm có thêm biển **ĐÁNH CHÌM!** kèm đường sóng.
- **Bảng điều khiển kiểu tàu:** đồng hồ lượt nằm trong màn sonar có điểm sáng, thanh thời gian là **ống nước** rút dần (`WaveProgress.java`), nhật ký hải chiến chạy ngang dưới bàn cờ kèm thước đo độ sâu, mỗi màn hình có la bàn và dòng trạng thái biển đổi theo thời gian (ví dụ “Sóng cấp 3 • Gió Bắc • Tầm nhìn 9 hải lý”).
- **Âm thanh:** thêm tiếng sóng vỗ, tiếng nước bắn (`SPLASH`), tiếng sóng lớn khi vào trận (`WAVE`) và tiếng sonar (`PING`); nhạc nền trận đấu được thay bằng tiếng sóng biển bốn giây lặp liền mạch.
- **Tắt hiệu ứng:** nút **Sóng biển: Bật/Tắt** trong trận và **Biển động: Bật/Tắt** ở sảnh. Khi tắt, cảnh biển vẫn còn nhưng đứng yên, không tốn CPU; lựa chọn được ghi nhớ trong `Preferences`.

Ảnh hiệu ứng: [vào trận (GIF)](docs/ui/sea-intro.gif), [vào trận – bố trí tàu (GIF)](docs/ui/sea-intro-placement.gif), [tấm biển RA KHƠI](docs/ui/sea-intro-5.png), [sóng quét qua](docs/ui/sea-intro-9.png), [tàu chạy ngang](docs/ui/sea-intro-13.png), [nước bắn khi trượt](docs/ui/battle-splash.png), [đạn bay](docs/ui/bomb-flight.png), [tàu chìm](docs/ui/ship-sunk.png), [toàn bộ hiệu ứng (GIF)](docs/ui/bomb-animation.gif), [xem trước đặt tàu](docs/ui/placement-preview.png).

Cảnh biển được tối ưu để không tranh CPU với giao diện: khung hình trung bình khoảng 15 ms (trước tối ưu là 63 ms), các lớp tĩnh được nướng sẵn vào ảnh cache, ô nước dùng một ảnh viên gạch dùng lại, và bộ đếm khung hình **tự giảm tốc** khi mỗi khung hình vượt quá nửa khoảng thời gian cho phép, khi cửa sổ không được chọn hoặc khi cửa sổ đang được đổi kích thước. Nếu máy quá yếu, tắt Sóng biển là đủ.

## Chạy và kiểm tra

```powershell
.\compile.bat
.\run_server.bat
# Chạy trong terminal khác; mở hai client để chơi.
.\run_client.bat

powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Gui -RealTimer
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Ocean   # chỉ render cảnh biển + hiệu ứng vào trận
```

Kiểm thử GUI bao gồm: kiểm tra cổng không hợp lệ, đăng nhập, tạo/vào phòng bằng mã chữ thường, mời đấu, tìm/hủy trận, xem trước tàu và xóa preview, xoay tàu, đặt tàu ngoài biên, xếp ngẫu nhiên, đặt lại, sẵn sàng/hủy sẵn sàng, chọn tọa độ, bắn trượt đổi lượt, bắn trúng/đánh chìm, chống gửi lặp, kết quả, chơi lại, xác nhận rời trận, bảng xếp hạng, mất server và kết nối lại.

`OceanVisualTests` (chế độ `-Ocean`, cũng chạy trong `-Gui`) render lại cảnh biển và hiệu ứng vào trận, kiểm tra hiệu ứng tự kết thúc, không chặn bắn, bàn cờ vẫn bấm được trong lúc sóng quét, nút bật/tắt sóng dừng và chạy lại đồng hồ biển, và đồng hồ biển dừng khi rời màn hình.

Kiểm thử phạt rời trận kiểm tra khóa các nút, đếm ngược, đăng xuất/đăng nhập lại, khởi động lại server và mở khóa khi đủ 60 giây bằng đồng hồ kiểm thử. `PenaltyTests` còn kiểm tra trừ điểm về 0, yêu cầu lặp, mất kết nối, lỗi lưu và retry.

`Theme.java` tập trung màu sắc, kiểu chữ, nút, thẻ, bảng và thanh cuộn. `NavalArt.java` chứa minh họa Java2D. `OceanScene.java` vẽ cảnh biển và hiệu ứng vào trận, `OceanPanel.java` là lớp nền cho hai màn hình trong trận, `SeaDecor.java` là các phụ kiện (la bàn, sonar, thước độ sâu, dải chân trời), `WaveProgress.java` là thanh thời gian hình ống nước. Cần build client và server cùng phiên bản để hiển thị đầy đủ hình phạt.

## Bản nâng cấp màu sắc, hiệu ứng và luyện tập

Đã tham khảo mã CSS và ảnh command-deck của StarNet tại commit `54899e5ff3f2b002b457757916b3977818db6fc1`, clone có chọn phần giao diện ở `references/starnet-reference`. Dùng hướng bảng điều khiển và màu nhấn; giao diện Java2D được viết cho Battleship, không chạy shell Tauri hay agent runtime của StarNet. Xem [ghi chú nguồn](THIRD_PARTY_NOTICES.md).

- Bản đồ chính lớn, tự theo mục tiêu đang bị bắn; nút Bản đồ thu nhỏ lưu lựa chọn.
- Bom bay vòng cung, vệt lửa, vòng nổ và mảnh vỡ khi tàu chìm; trên nền biển có thêm cột nước bắn, vòng bọt và khói (xem mục Ra khơi). Kết quả được server xác nhận trước khi trình diễn; nút bắn chặn gửi lặp trong hiệu ứng.
- Âm thanh riêng theo thao tác/sự kiện, có nút tắt trong sảnh và trận.
- Đấu máy mở bảng chọn ba độ khó. Luyện tập không tính điểm/phạt rời trận.
- Tài khoản mở hộp xác nhận nhập mật khẩu để xóa tài khoản hiện tại khỏi database.

Ảnh và bản ghi hiệu ứng: [đấu máy](docs/ui/bot-levels.png), [phòng thủ](docs/ui/defense.png), [ẩn bản đồ nhỏ](docs/ui/no-minimap.png), [bom bay](docs/ui/bomb-flight.png), [tàu chìm](docs/ui/ship-sunk.png), [animation GIF](docs/ui/bomb-animation.gif), [xóa tài khoản](docs/ui/delete-account.png).
