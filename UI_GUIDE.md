# Giao diện Battleship — Naval Command

Giao diện Java Swing thống nhất với nền xanh tím, biển xanh, tàu tím, điểm nhấn ngọc lam/cam, trạng thái cảnh báo màu hổ phách và kết quả bắn màu san hô. Minh họa radar và bàn cờ được vẽ bằng Java2D, không cần thư viện hay tải tài nguyên từ Internet.

Bảng màu ngoài trận được mở rộng: mỗi khu vực có màu nhấn riêng (điểm vàng hổ phách, trận thắng xanh lá, tỷ lệ thắng xanh da trời, lịch sử hồng tím, bảng xếp hạng vàng/bạc/đồng), thẻ dùng nền chuyển hai màu, bảng biểu tô màu theo cột và nhật ký trận tô màu theo kết quả từng phát bắn.

**Hai màn hình trong trận được dựng thành cảnh biển động** (xem [mục Ra khơi](#ra-khơi-hiệu-ứng-và-giao-diện-biển-trong-trận)): nền sóng cuộn, hiệu ứng sóng quét khi vào trận, bàn cờ là mặt nước có lưới ánh sáng và sonar, cùng âm thanh sóng biển.

## Luồng chơi

1. **Đăng nhập:** nhập máy chủ, cổng, tên chỉ huy và mật khẩu. Nhãn và ô nhập được căn cùng lề, form cân giữa theo chiều dọc. Có kiểm tra dữ liệu ngay trên form và nút hiện mật khẩu; bỏ phần tài khoản mẫu cùng dòng chú thích bên dưới. Nhấn Enter để đăng nhập. Tên mới được server tạo thành tài khoản theo quy tắc hiện có.
2. **Phòng chỉ huy:** xem điểm, trận thắng, tỷ lệ thắng và những người chơi khác đang trực tuyến. Chọn ghép trận nhanh, tạo phòng riêng hoặc nhập mã 6 ký tự. Có thể sao chép mã phòng, hủy tìm trận và rời phòng đang chờ. Chỉ người chơi sẵn sàng mới có thể được mời thách đấu.
3. **Triển khai hạm đội:** chọn tàu ở danh sách bên phải, rê chuột lên bàn cờ để xem trước. Ngọc lam là vị trí hợp lệ, đỏ là không hợp lệ. Nhấn **R** hoặc nút hướng để xoay tàu. Đặt xong tự chọn tàu chưa được bố trí; chọn lại tàu để đổi vị trí. Có xếp ngẫu nhiên, đặt lại và tiến độ 0–5 tàu. Nút Sẵn sàng luôn nằm trong vùng thao tác cố định; trạng thái chỉ được xác nhận khi server phản hồi. Góc trên phải luôn có nút **× THOÁT VỀ SẢNH** (nền san hô, có nhắc lệnh **ESC** ở chân màn hình): rời phòng bố trí trả cả hai về sảnh và **không bị phạt** vì trận chưa bắt đầu. Nút **Cài đặt** cũng có ngay tại đây.
4. **Chiến đấu:** bản đồ đang bị bắn chiếm vùng chính; bản đồ còn lại ở ô nhỏ có thể bật/tắt. Đến lượt bạn hiện bản đồ đối thủ; lượt đối thủ hiện bản đồ bạn. Xem số tàu còn lại, lượt chơi và đồng hồ. Chọn ô trên bàn cờ đối thủ, kiểm tra tọa độ rồi nhấn Khai hỏa. Dùng phím mũi tên để di chuyển tiêu điểm trên lưới, Enter/Space để chọn ô. Nhật ký lưu 30 diễn biến gần nhất. Mỗi phát bắn chờ phản hồi trước khi cho phép gửi tiếp. Bắn trúng giữ lượt, trượt đổi lượt; hết giờ chờ server chuyển lượt.
5. **Kết quả:** màn hình kết quả toàn cửa sổ hiện chữ **WIN** hoặc **LOSE** với hiệu ứng động, sau đó tự mở tung hai hải phận (xem [mục Kết quả trận](#kết-quả-trận-win--lose-và-hai-hải-phận)). Vẫn có đề nghị chơi lại và nút về sảnh. Đóng cửa sổ khi đang chiến đấu cần xác nhận rời trận. Bảng xếp hạng có ba vị trí dẫn đầu và bảng thống kê đầy đủ.

Các thông báo thao tác, lỗi xếp tàu và lỗi kết nối xuất hiện ngay trong màn hình liên quan. Màn hình nhỏ sử dụng thanh cuộn ở các vùng phụ, giữ bàn cờ vuông và các hành động chính dễ tiếp cận.

Rời trận đang chiến đấu có xác nhận mức phạt **−3 điểm (không dưới 0), chờ 1 phút**. Sau khi rời trận hoặc đăng nhập lại, sảnh hiển thị đếm ngược màu hổ phách; các nút ghép nhanh, tạo/vào phòng và mời đấu bị khóa đến khi hết hạn. Người khác thấy trạng thái “Đang bị phạt”. Mất kết nối khi đang đấu áp dụng cùng quy tắc; rời màn hình bố trí tàu không bị phạt.

## Khung chat thu gọn

Chọn một tài khoản ở sảnh rồi bấm **Nhắn tin**. Khung chat nổi ở góc dưới phải có danh sách người nhận, bong bóng tin nhắn hai màu, thời gian, ô soạn và nút **Gửi**. Bấm thanh tiêu đề để thu gọn/mở lại; dấu số màu vàng báo tin nhận khi đang thu gọn hoặc đang xem cuộc trò chuyện khác. Chat không tự mở hay cướp tiêu điểm khi nhận tin.

Vào trận với người thật sẽ tự chọn đối thủ và thu gọn khung. Mở lại để trò chuyện trong lúc xếp tàu hoặc chiến đấu; bản đồ giữ nguyên kích thước. Khung chat đi theo cửa sổ khi thay đổi kích thước, giữ bản nháp và lịch sử khi chuyển giữa sảnh và trận. Danh sách có trạng thái online/offline; người đã có lịch sử vẫn có thể được nhắn khi offline.

**Enter** gửi; **Shift+Enter** xuống dòng. Giới hạn 500 ký tự; không gửi rỗng. Phản hồi lỗi xuất hiện ngay dưới ô soạn, giữ bản nháp để gửi lại; di chuột lên thông báo để đọc đầy đủ. **Tin nhắn trước** tải thêm 20 tin mỗi lần. Đăng xuất xóa cache chat của phiên client, lịch sử vẫn được lưu trong MySQL trên server.

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
| Kết quả trận (WIN) | [result.png](docs/ui/result.png) |
| Kết quả trận (LOSE) | [result-lose.png](docs/ui/result-lose.png) |
| Bảng xếp hạng | [leaderboard.png](docs/ui/leaderboard.png) |
| Lịch sử đấu | [match-history.png](docs/ui/match-history.png) |
| Hồi tượng trận đấu | [match-replay.png](docs/ui/match-replay.png) |
| Cài đặt | [settings.png](docs/ui/settings.png) |

Ảnh cửa sổ nhỏ: [đăng nhập](docs/ui/login-small.png), [sảnh](docs/ui/lobby-small.png), [xếp tàu](docs/ui/placement-small.png), [chiến đấu](docs/ui/battle-small.png).

## Ra khơi: hiệu ứng và giao diện biển trong trận

Hai màn hình trong trận (**Triển khai hạm đội** và **Chiến đấu**) được dựng như đang ở ngoài biển. Toàn bộ cảnh biển được vẽ bằng Java2D trong `OceanScene.java`, không dùng ảnh hay thư viện ngoài, và chạy theo một đồng hồ chung (`OceanPanel.java`) nên mọi lớp sóng, bọt và ánh sáng luôn khớp nhau.

- **Nền biển sống:** bầu trời chuyển sắc, mặt trời và quầng sáng, mây trôi, đảo xa và thuyền buồm ở đường chân trời, năm lớp sóng cuộn với bọt nước ở từng đỉnh, cột nắng chiếu xuống nước, vệt nắng lấp lánh trên mặt biển, hải âu lượn và bọt khí nổi từ đáy.
- **Hiệu ứng vào trận:** khi màn hình xuất hiện, một con sóng lớn quét qua toàn màn hình kèm bọt và tia nước, đồng thời tấm biển kính trượt vào giữa với tiêu đề **RA KHƠI** (bố trí tàu) hoặc **VÀO TRẬN** (chiến đấu), có la bàn, màn sonar, tàu khu trục chạy ngang để lại vệt bọt và các vòng sonar lan ra. Hiệu ứng kéo dài 2,45 giây, không chặn thao tác: bàn cờ vẫn bấm được, nút Khai hỏa vẫn hoạt động trong lúc sóng đang quét.
- **Bàn cờ là mặt nước:** nền bàn cờ là nước chuyển sắc có đường bình độ hải đồ, hoa la bàn và đường sóng; các ô là những viên kính trong suốt nên thấy được dòng nước chảy bên dưới, kèm lưới ánh sáng (caustics) và vòng sonar quét qua lưới. Bắn trượt để lại vệt nước bắn bốn tia; bắn trúng lóe sáng; tàu chìm tối dần và sủi bọt khí.
- **Hiệu ứng bắn:** quả đạn bay theo vòng cung có khói và vệt lửa, điểm xuất phát có bọt nước; khi chạm: bắn trượt thì **cột nước bắn lên**, vòng bọt lan trên mặt nước và hàng chục giọt nước; bắn trúng thì cầu lửa, mũ bọt trắng, mảnh vỡ và khói; đánh chìm có thêm biển **ĐÁNH CHÌM!** kèm đường sóng.
- **Bảng điều khiển kiểu tàu:** đồng hồ lượt nằm trong màn sonar có điểm sáng, thanh thời gian là **ống nước** rút dần (`WaveProgress.java`), nhật ký hải chiến chạy ngang dưới bàn cờ kèm thước đo độ sâu, mỗi màn hình có la bàn và dòng trạng thái biển đổi theo thời gian (ví dụ “Sóng cấp 3 • Gió Bắc • Tầm nhìn 9 hải lý”).
- **Âm thanh:** thêm tiếng sóng vỗ, tiếng nước bắn (`SPLASH`), tiếng sóng lớn khi vào trận (`WAVE`) và tiếng sonar (`PING`); nhạc nền trận đấu được thay bằng tiếng sóng biển bốn giây lặp liền mạch, tự hạ xuống khi có phát đạn lớn.
- **Tắt hiệu ứng:** hộp kiểm **Sóng biển** trong tiêu đề màn hình trận, và hộp kiểm **Sóng biển động** / **Âm thanh** trong cửa sổ **Cài đặt** (mở từ sảnh, màn hình bố trí tàu hoặc màn hình chiến đấu). Khi tắt, cảnh biển vẫn còn nhưng đứng yên, không tốn CPU; lựa chọn được ghi nhớ trong `Preferences`.

Ảnh hiệu ứng: [vào trận (GIF)](docs/ui/sea-intro.gif), [vào trận – bố trí tàu (GIF)](docs/ui/sea-intro-placement.gif), [tấm biển RA KHƠI](docs/ui/sea-intro-5.png), [sóng quét qua](docs/ui/sea-intro-9.png), [tàu chạy ngang](docs/ui/sea-intro-13.png), [nước bắn khi trượt](docs/ui/battle-splash.png), [đạn bay](docs/ui/bomb-flight.png), [tàu chìm](docs/ui/ship-sunk.png), [toàn bộ hiệu ứng (GIF)](docs/ui/bomb-animation.gif), [xem trước đặt tàu](docs/ui/placement-preview.png).

Cảnh biển được tối ưu để không tranh CPU với giao diện: khung hình trung bình khoảng 15 ms (trước tối ưu là 63 ms), các lớp tĩnh được nướng sẵn vào ảnh cache, ô nước dùng một ảnh viên gạch dùng lại, và bộ đếm khung hình **tự giảm tốc** khi mỗi khung hình vượt quá nửa khoảng thời gian cho phép, khi cửa sổ không được chọn hoặc khi cửa sổ đang được đổi kích thước. Nếu máy quá yếu, tắt Sóng biển là đủ.

## Lịch sử đấu, xem lại trận và báo cáo sau trận

**Lịch sử đấu.** Nút **03 Lịch sử đấu** ở cột bên trái của sảnh mở hồ sơ của chính bạn; chọn một chỉ huy trong danh sách trực tuyến rồi bấm **Lịch sử** để xem hồ sơ của người đó. Cửa sổ hiển thị điểm, số trận thắng và số trận đã đấu trên ba thẻ màu, kèm bảng 20 trận gần nhất: thời gian, đối thủ, kết quả **THẮNG** (xanh lá) hoặc **THUA** (đỏ san hô), điểm nhận, số phát bắn, thời lượng, lý do kết thúc (đánh chìm toàn bộ hạm đội, một bên rời trận, mất kết nối) và cột **Hải phận** (`Xem lại` hoặc `—`). Dữ liệu lấy từ bảng `matches` trong MySQL trên server qua `GET_MATCH_HISTORY_REQ`; client không tự tính kết quả.

**Xem lại một trận (hồi tượng).** Nhấn vào một dòng có `Xem lại` để mở màn hình hồi tượng: hai bàn cờ 10×10 cạnh nhau — **hải phận của bạn** (tàu của bạn cùng các phát bắn của đối thủ) và **hải phận đối thủ** (tàu đối thủ cùng các phát bắn của bạn) — kèm danh sách **đường đạn của trận đấu** theo đúng thứ tự bắn (mỗi dòng tô màu theo kết quả: trúng vàng, trượt xanh, chìm san hô). Thanh trượt và ba nút **Về đầu / Phát lại / Cuối** phát lại từng phát bắn; bấm một dòng trong danh sách để nhảy tới đúng phát đó, hai bàn cờ được dựng lại chính xác đến phát đang xem. Dữ liệu do server trả về qua `GET_MATCH_DETAIL_REQ`, chỉ cho hai đấu thủ của trận; trận cũ không có dữ liệu thì hiện dòng giải thích ngay dưới bảng thay vì gọi server.

**Xem tàu đối thủ khi trận kết thúc.** Khi trận đấu kết thúc, server gửi kèm hạm đội của đối phương trong `MATCH_END` (`opponent_ships`). Ngay lúc đó client công bố hạm đội đó lên bàn cờ đối thủ: những ô chưa bị bắn hiện hình tàu, còn ô đã bắn giữ nguyên vết trúng/trượt/chìm. Trước khi trận kết thúc, tọa độ tàu đối phương không bao giờ được gửi về client.

Ảnh: [lịch sử đấu](docs/ui/match-history.png), [hồi tượng trận đấu](docs/ui/match-replay.png), [hồi tượng khi tua lại](docs/ui/match-replay-early.png).

## Kết quả trận: WIN / LOSE và hai hải phận

Màn hình kết quả nằm trong `MatchResultView.java` và phủ toàn bộ cửa sổ trận đấu (cửa sổ không viền, cùng kích thước cửa sổ trận) theo một mốc thời gian duy nhất:

- **Chữ kết quả động:** **WIN** (vàng → xanh mint) hoặc **LOSE** (san hô → tím) rơi xuống từng chữ một với hiệu ứng bật nhẹ, kèm bóng đổ, quầng sáng và vệt sáng chạy ngang; dưới đó là dòng **CHIẾN THẮNG** / **THẤT BẠI** cùng câu mô tả. Phía sau chữ thắng có vệt nắng quay chậm, vòng sonar và một con tàu chạy ngang để lại vệt bọt; phía sau chữ thua là màn nước tối dần, mưa xiên và xác tàu nghiêng chìm cùng bọt khí.
- **Hai hải phận mở tung:** sau khoảng 1,55 giây, hai bàn cờ **mở ra từ đường giữa** (mép mở có đường sóng và bọt) rồi cố định: bên trái là hạm đội của bạn với các phát bắn của đối thủ, bên phải là hạm đội đối thủ (đã được công bố) với các phát bắn của bạn; mỗi bàn có dòng chú thích số phát bắn và số ô tàu, kèm chú giải `×` trúng, `○` trượt, `●` chìm.
- **Thanh kết quả:** thẻ **+3 ĐIỂM** (hoặc số điểm thực nhận, có thể âm khi rời trận), số phát bắn, độ chính xác, thời lượng trận và lý do kết thúc. Hai nút **Chơi lại (Rematch)** và **Về phòng chỉ huy** dùng được ngay trong lúc hiệu ứng đang chạy, không bị chặn.
- **Hiệu ứng tan:** confetti rơi liên tục khi thắng, mưa khi thua, và một nhịp lóe sáng đúng lúc hai bàn cờ bung ra.

Ảnh và bản ghi: [kết quả thắng](docs/ui/result.png), [chữ WIN đang rơi](docs/ui/result-win-7.png), [hai hải phận đang mở](docs/ui/result-win-20.png), [kết quả thua](docs/ui/result-lose.png), [toàn bộ hiệu ứng (GIF)](docs/ui/result-win-animation.gif), [báo cáo sau trận](docs/ui/result-report.png).

## Cài đặt và âm thanh

Nút **05 Cài đặt** ở sảnh (và nút **Cài đặt** ở màn hình bố trí tàu cùng tiêu đề màn hình chiến đấu) mở một cửa sổ dùng chung:

- **Ba mức âm lượng** dạng thanh trượt kèm phần trăm: **Âm lượng tổng**, **Hiệu ứng**, **Tiếng biển**. Kéo thanh trượt có tiếng nghe thử ngay; mức **Tiếng biển** áp dụng trực tiếp vào tiếng sóng đang phát qua gain của thiết bị âm thanh.
- **Bật âm thanh**, **Sóng biển động**, **Bản đồ thu nhỏ trong trận**: đổi là áp dụng ngay cho mọi cửa sổ đang mở (bàn cờ, bố trí tàu, màn hình kết quả) và được ghi nhớ trong `Preferences`.
- **Nghe thử** phát tiếng sonar và tiếng chìm tàu, **Mặc định** trả mọi mức về giá trị gốc.

Âm thanh được tổng hợp tại máy trong `GameAudio.java` và được làm mềm ở ba chỗ: mọi tiếng **vào và ra ở mức 0** bằng cửa sổ cosine (không còn tiếng "tách" ở đầu/cuối), tiếng ồn được lọc thông thấp thay vì dùng nhiễu thô, và các tiếng lớn được xếp lớp (thân trầm + tiếng rền + bọt khí) nên nghe dày mà không chói. Tiếng biển nền bốn giây được nối vòng bằng cách gấp nửa giây đuôi vào đầu tệp nên hết vòng không còn tiếng giật; khi một phát đạn trúng hoặc chìm, tiếng biển tự hạ xuống rồi trở lại (ducking), và khi tắt âm thanh mọi tiếng đang phát được giảm dần thay vì cắt đột ngột. `AudioTests` đo chính các tính chất này: biên đầu/cuối bằng 0, còn dư địa không méo, bước nhảy giữa hai mẫu liền kề nhỏ với các tiếng có cao độ, và mối nối của vòng lặp tiếng biển.

Ảnh: [cửa sổ cài đặt](docs/ui/settings.png).

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

Kiểm thử GUI bao gồm: kiểm tra cổng không hợp lệ, đăng nhập, tạo/vào phòng bằng mã chữ thường, mời đấu, tìm/hủy trận, xem trước tàu và xóa preview, xoay tàu, đặt tàu ngoài biên, xếp ngẫu nhiên, đặt lại, sẵn sàng/hủy sẵn sàng, chọn tọa độ, bắn trượt đổi lượt, bắn trúng/đánh chìm, chống gửi lặp, kết quả và hai hải phận mở ra, chơi lại, xác nhận rời trận, bảng xếp hạng, mất server và kết nối lại.

`ReplayTests` kiểm tra lịch sử và hồi tượng qua TCP: cờ `replay` trong lịch sử, hai hạm đội niêm phong đủ 17 ô và đúng góc nhìn từng người, nhật ký bắn đủ số phát theo thứ tự không lặp ô, `FORBIDDEN` với người ngoài trận, `MATCH_NOT_FOUND`/`INVALID_MESSAGE`, dữ liệu còn sau khi khởi động lại server, và bản ghi cũ trả `REPLAY_UNAVAILABLE` nhưng vẫn đọc được. `AudioTests` đo biên độ/độ liền mạch của âm thanh tổng hợp.

`OceanVisualTests` (chế độ `-Ocean`, cũng chạy trong `-Gui`) render lại cảnh biển, hiệu ứng vào trận, màn hình kết quả WIN/LOSE (chữ đang rơi, hai hải phận đang mở, bản thua), màn hình hồi tượng và cửa sổ cài đặt; kiểm tra hiệu ứng tự kết thúc, không chặn bắn, bàn cờ vẫn bấm được trong lúc sóng quét, hai hải phận chứa đủ tàu và vết bắn, nhấn một dòng lịch sử thì hỏi server đúng mã trận, thanh trượt âm lượng tới được bộ âm thanh, và đồng hồ biển dừng khi rời màn hình.

Kiểm thử phạt rời trận kiểm tra khóa các nút, đếm ngược, đăng xuất/đăng nhập lại, khởi động lại server và mở khóa khi đủ 60 giây bằng đồng hồ kiểm thử. `PenaltyTests` còn kiểm tra trừ điểm về 0, yêu cầu lặp, mất kết nối, lỗi lưu và retry.

`Theme.java` tập trung màu sắc, kiểu chữ, nút, thẻ, bảng và thanh cuộn. `NavalArt.java` chứa minh họa Java2D. `MatchHistoryDialog.java` là cửa sổ lịch sử đấu, `MatchReplayDialog.java` là màn hình hồi tượng một trận, `MatchResultView.java` là màn hình kết quả WIN/LOSE có hai hải phận mở ra, `SettingsDialog.java` là cửa sổ cài đặt âm lượng/hiển thị, `GameAudio.java` tổng hợp và trộn âm thanh. `OceanScene.java` vẽ cảnh biển và hiệu ứng vào trận, `OceanPanel.java` là lớp nền cho các màn hình biển, `SeaDecor.java` là các phụ kiện (la bàn, sonar, thước độ sâu, dải chân trời), `WaveProgress.java` là thanh thời gian hình ống nước. Cần build client và server cùng phiên bản để hiển thị đầy đủ hình phạt.

## Bản nâng cấp màu sắc, hiệu ứng và luyện tập

Đã tham khảo mã CSS và ảnh command-deck của StarNet tại commit `54899e5ff3f2b002b457757916b3977818db6fc1`, clone có chọn phần giao diện ở `references/starnet-reference`. Dùng hướng bảng điều khiển và màu nhấn; giao diện Java2D được viết cho Battleship, không chạy shell Tauri hay agent runtime của StarNet. Xem [ghi chú nguồn](THIRD_PARTY_NOTICES.md).

- Bản đồ chính lớn, tự theo mục tiêu đang bị bắn; nút Bản đồ thu nhỏ lưu lựa chọn.
- Bom bay vòng cung, vệt lửa, vòng nổ và mảnh vỡ khi tàu chìm; trên nền biển có thêm cột nước bắn, vòng bọt và khói (xem mục Ra khơi). Kết quả được server xác nhận trước khi trình diễn; nút bắn chặn gửi lặp trong hiệu ứng.
- Âm thanh riêng theo thao tác/sự kiện, có cửa sổ **Cài đặt** chỉnh ba mức âm lượng và bật/tắt tiếng ở sảnh, màn hình bố trí tàu và màn hình chiến đấu.
- Đấu máy mở bảng chọn ba độ khó. Luyện tập không tính điểm/phạt rời trận.
- Tài khoản mở hộp xác nhận nhập mật khẩu để xóa tài khoản hiện tại khỏi database.

Ảnh và bản ghi hiệu ứng: [đấu máy](docs/ui/bot-levels.png), [phòng thủ](docs/ui/defense.png), [ẩn bản đồ nhỏ](docs/ui/no-minimap.png), [bom bay](docs/ui/bomb-flight.png), [tàu chìm](docs/ui/ship-sunk.png), [animation GIF](docs/ui/bomb-animation.gif), [xóa tài khoản](docs/ui/delete-account.png).

## Các thao tác bổ sung ngày 26/10/2026

- **Tài khoản:** chọn Đổi mật khẩu để mở form không chặn mạng, nhập hiện tại/mới/nhập lại. Lỗi ngay trong form; thành công xóa nội dung ô mật khẩu. Xóa tài khoản là lựa chọn riêng trong menu.
- **Bố trí:** góc trên hiện 120 giây, đổi màu khi còn 30 giây; hết giờ chờ server rồi về sảnh. Mẫu bố trí nằm trong vùng cuộn biên đội, có 5 ô lưu/dùng/đổi tên/xóa. Ready khóa mẫu và bàn cờ.
- **Kết nối:** sảnh/đặt tàu/chiến đấu hiện ms, không dùng độ trễ để đổi lượt. Mất kết nối đưa về đăng nhập, trận cũ được server kết thúc theo luật.
- **Lịch sử:** chọn kết quả, đối thủ, từ/đến dd/MM/yyyy và bấm Lọc. Xem thêm nối trang; lọc mới tải lại từ đầu. Cửa sổ nhỏ kéo ngang bảng để đọc các cột, không ép cột mất chữ.
- **Kết quả:** chờ lưu nếu chưa commit, rematch bị khóa. Lưu xong thì điểm và quyền rematch được cập nhật theo server.


Ảnh bản hoàn thiện: [Đổi mật khẩu](docs/ui/completion-password.png), [Đặt tàu có hạn chờ](docs/ui/completion-placement.png), [Lịch sử có bộ lọc](docs/ui/completion-history.png), [Lịch sử ở cửa sổ nhỏ](docs/ui/completion-history-small.png).
