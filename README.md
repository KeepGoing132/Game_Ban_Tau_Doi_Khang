# Battleship Online

Kế hoạch hoàn thiện hiện tại: [PLAN.md](PLAN.md) — luồng người dùng, thứ tự triển khai, các chức năng cần bổ sung và tiêu chí nghiệm thu. Bản hoàn thiện 26/10/2026 bổ sung đổi mật khẩu, hạn chuẩn bị 120 giây, 5 mẫu bố trí, tình trạng kết nối, lịch sử có bộ lọc/phân trang và phục hồi kết quả chờ lưu.

Giao diện **Naval Command**: đăng nhập, phòng chỉ huy, bố trí hạm đội có xem trước, chiến đấu với nhật ký, màn hình kết quả WIN/LOSE mở ra hai hải phận, lịch sử đấu xem lại được từng trận, cửa sổ cài đặt âm lượng và bảng xếp hạng. Hai màn hình trong trận là cảnh biển động có hiệu ứng sóng quét khi vào trận. Xem [hướng dẫn giao diện và ảnh minh họa](UI_GUIDE.md).

Game bắn tàu đối kháng dùng Java SE, Swing và TCP Socket. Server quản lý tài khoản, sảnh, bàn cờ bí mật, lượt bắn, điểm và lịch sử. Giữ stack Java Swing/TCP và nguồn Word của đồ án. Server dùng SQLite JDBC được đóng gói trong `lib/`, không cần cài dịch vụ database riêng.

## Chuẩn bị Java trên Windows và macOS

Project dùng Java Swing/TCP, cùng mã nguồn và cùng file JAR cho hai hệ điều hành. Cài **JDK 21** từ [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21): chọn Windows hoặc macOS đúng máy. Mac chip Apple Silicon (M1/M2/M3/M4…) chọn **aarch64**, Mac Intel chọn **x64**. Windows Intel/AMD thường chọn **x64**.

Sau khi cài, mở terminal mới và kiểm tra:

```text
java -version
javac -version
```

Máy biên dịch cần JDK có `javac` và `jar`; máy chỉ chạy file JAR cần Java có hỗ trợ desktop. Không cần đổi sang JavaFX hoặc viết lại ứng dụng riêng cho Mac.

## Chạy trên Windows

Cần JDK có `java` và `javac` trong PATH. Mã dùng API Java 8; đã biên dịch `--release 8` và chạy kiểm thử trên JDK 25.0.1. Runtime Java 8 chưa được thử.

```powershell
# Trong thư mục project
powershell -NoProfile -ExecutionPolicy Bypass -File .\compile.ps1
.\run_server.bat
```

Mở hai terminal khác và chạy trong mỗi terminal:

```powershell
.\run_client.bat
```

Có thể nhấp đúp `compile.bat`, `run_server.bat`, rồi `run_client.bat` nhiều lần. Các script tự tìm thư mục project, không cần chạy từ đúng working directory, không pause và trả exit code của chương trình. Sau khi đổi code, chạy compile trước khi chạy server/client.

Cổng mặc định **8888**. Đổi cổng bằng `run_server.bat 9999`, rồi nhập cùng cổng trên màn hình đăng nhập. Hoặc chạy trực tiếp:

```powershell
java '-Dfile.encoding=UTF-8' -cp bin server.ServerMain 8888
java '-Dfile.encoding=UTF-8' -cp bin client.ClientMain
```

Tài khoản mẫu: `tuan_duong`, `ngoc_bao`, `thanh_hai`, `dang_khoa`, `player1`, `player2`; mật khẩu ban đầu `123456`. Điểm mẫu lấy từ dữ liệu hiện có. Tên mới sẽ tự tạo tài khoản: 1–32 ký tự chữ ASCII, số hoặc `_`; không phân biệt hoa/thường. Mật khẩu không được rỗng, tối đa 128 ký tự. Đăng nhập trùng bị từ chối, phiên đang chơi tiếp tục hoạt động.

## Chạy trên macOS

Chép project sang Mac. Mở Terminal tại thư mục project (thay đường dẫn minh họa bằng thư mục thực tế):

```bash
cd "/Users/ten-ban/Downloads/btl"
bash compile.sh
bash run_server.sh
```

Giữ terminal server đang chạy. Mở terminal khác, vào cùng thư mục rồi chạy:

```bash
bash run_client.sh
```

Mở thêm terminal và chạy `bash run_client.sh` lần nữa nếu cần hai người chơi trên một máy. Đổi cổng bằng `bash run_server.sh 9999`, rồi nhập cổng 9999 trong client. Nếu server nằm ở máy khác, Mac chỉ cần chạy client.

Để nhấp đúp mở từ Finder, cấp quyền một lần trong Terminal:

```bash
chmod +x run_client.command run_server.command
```

Sau đó mở `run_server.command` hoặc `run_client.command`. Các file `.command` dùng cùng script `.sh`; cách chạy bằng `bash` không cần cấp quyền thực thi. Script tự tìm thư mục project, dùng UTF-8 và báo lỗi nếu thiếu Java. Sau khi sửa code, chạy lại `bash compile.sh`.

Giao diện tự chọn phông có trên máy (Segoe UI/Helvetica Neue, Consolas/Menlo và phông Java dự phòng). Script Bash đã được kiểm tra trên Git Bash/Windows, gồm đường dẫn có dấu cách và gọi từ thư mục khác. **Chưa chạy thử trực tiếp trên macOS**; cần kiểm tra giao diện và kết nối trên máy Mac đích trước buổi demo.

## Chuyển bản chạy JAR giữa Windows và Mac

Cả `compile.bat`/`compile.ps1` và `compile.sh` tạo:

```text
dist/battleship-client.jar
dist/battleship-server.jar
```

Có thể biên dịch trên Windows rồi gửi chính các file này sang Mac; không cần biên dịch lại ở máy nhận. Gửi cả hai JAR **và thư mục `dist/lib/`** sang máy nhận. Server cần thư viện SQLite trong `lib/` cạnh file JAR. Trong thư mục chứa hai JAR:

```text
java -Dfile.encoding=UTF-8 -jar battleship-server.jar 8888
```

Mở terminal khác tại cùng thư mục:

```text
java -Dfile.encoding=UTF-8 -jar battleship-client.jar
```

Server lưu `data/` bên trong thư mục đang chạy lệnh. Bản JAR không chứa tài khoản, mật khẩu hoặc dữ liệu của project. Thư mục mới sẽ có dữ liệu mẫu; muốn chuyển điểm/lịch sử hiện tại, dừng server rồi sao chép nguyên thư mục `data/` sang máy mới. Chỉ chạy một server dùng mỗi thư mục dữ liệu.

## Kiểm thử

```powershell
# Bộ test nhanh: socket thật, bốn client, clock giả, race và storage fault
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1

# Cùng bộ test, timeout chiến đấu thật 15 giây
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -RealTimer

# Swing thật + TCP: bấm component trên EDT, kiểm tra toàn luồng và render ảnh
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Gui -RealTimer

# Chỉ render cảnh biển và hiệu ứng vào trận (ảnh PNG + GIF)
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Ocean

# Kiểm tra runner báo lỗi đúng: lệnh này CỐ Ý trả exit code 1
powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -SelfTestFailure
```

Test tự tạo thư mục `%TEMP%/battleship-*`, chọn cổng riêng bằng port 0, không dùng hoặc sửa dữ liệu thật. Build test cũng ở `%TEMP%`; runner có assertion, hạn chờ 120 giây và exit code khác 0 khi lỗi. GUI test cần desktop và sẽ mở rồi đóng các cửa sổ test; đường dẫn ảnh được in ở cuối. Bằng chứng và ánh xạ từng ca test nằm trong [TASKS_AND_MEMBERS.md](TASKS_AND_MEMBERS.md).

## Kịch bản demo

1. Chạy server và mở ít nhất ba client bằng ba tài khoản khác nhau. Quan sát danh sách online; thử đăng nhập tài khoản đang dùng ở client thứ tư: phải bị từ chối.
2. Client A tạo phòng, lấy mã sáu ký tự; B nhập mã. Hai người chuyển sang bố trí tàu, người quan sát thấy BUSY. Thử mã sai và tự join để thấy lỗi.
3. Rời màn hình đặt tàu bằng nút **× THOÁT VỀ SẢNH** (hoặc phím **ESC**, hoặc đóng cửa sổ): cả hai về sảnh, không cộng điểm và không bị phạt. Thử tiếp bằng lời mời Accept/Reject, rồi Quick Match; thử hủy tìm trận trước khi có người thứ hai.
4. Đặt đúng 5 tàu `[5,4,3,3,2]`; có thể dùng Xếp tàu ngẫu nhiên. Bấm Ready: chờ xác nhận server rồi khóa sửa tàu. Thử Hủy sẵn sàng trước khi đối thủ Ready, sửa rồi Ready lại.
5. Cả hai Ready: chỉ có một người được bắn trước. Chọn ô rồi Bắn. HIT/SUNK giữ lượt, MISS đổi lượt. Đợi 15 giây không bắn: mất lượt, không có phát bắn tự động.
6. Bắn hết 17 ô tàu: thắng +3, thua +0, mỗi bên tăng một trận. Đầu hàng/đóng cửa sổ/mất kết nối khi đang đấu: người rời trận bị trừ 3 điểm (không dưới 0), cấm ghép trận 1 phút; đối thủ thắng +3. Sảnh hiển thị đếm ngược và khóa ghép nhanh, tạo/vào phòng, lời mời. Đăng nhập lại không xóa hạn phạt. Mở xếp hạng để kiểm tra số liệu.
7. Sau kết thúc bình thường, cả hai Chơi lại trong 30 giây: trở lại đặt tàu với match ID mới, timer của ván hai hoạt động. Từ chối, thoát hoặc hết hạn thì về sảnh.
8. Dừng server rồi chạy lại: điểm và lịch sử giữ nguyên. Có thể mở bốn client, ghép hai cặp và cho một trận timeout/thoát để chứng minh trận còn lại tiếp tục.
9. Sau khi hết trận, xem chữ **WIN** hoặc **LOSE** rơi xuống rồi **hai hải phận mở tung**: bàn của bạn với các phát bắn của đối thủ và bàn đối thủ với các phát bắn của bạn, kèm điểm, số phát bắn, độ chính xác và lý do kết thúc.
10. Mở **Lịch sử đấu** ở cột trái sảnh rồi nhấn vào dòng vừa đấu: màn hình hồi tượng mở ra với hai bàn cờ đã dựng sẵn toàn bộ vết bắn cùng danh sách đường đạn theo đúng thứ tự. Dòng ghi trước khi có tính năng sẽ báo không lưu hải phận, và chỉ hai người trong trận mở được.
11. Mở **Cài đặt** (ở sảnh, màn hình bố trí tàu hoặc trong trận) để kéo ba mức âm lượng, tắt/bật âm thanh, sóng biển và bản đồ thu nhỏ; kéo thanh trượt có tiếng nghe thử ngay.

Muốn quan sát HIT/SUNK nhanh, hai người có thể tự đặt tàu ngang lần lượt từ cột A ở các hàng 1–5, chiều dài 5,4,3,3,2. Trong demo bình thường, client vẫn không nhận board bí mật của đối thủ.

## Dữ liệu và cấu hình

Server lưu database SQLite tại **`data/battleship.db`** (tương đối với thư mục chạy lệnh). `accounts` chứa username, mật khẩu băm PBKDF2-HMAC-SHA256 có salt/120000 vòng, điểm, thống kê và hạn phạt; `matches` chứa lịch sử; `metadata` quản lý phiên bản schema. Transaction ghi tài khoản và kết quả cùng nhau. Không lưu mật khẩu gốc trong database. JDBC 3.53.4.0 nằm trong `lib/`; bản server JAR cần `dist/lib/` đi kèm.

Lần đầu, server tự nhập `snapshot.json` hiện có; nếu chưa có snapshot thì nhập `users.json`/`matches.json`. ID, mật khẩu băm, điểm, lịch sử và thời gian phạt được giữ. File nguồn cũ được giữ nguyên làm bản lưu trước chuyển đổi, có thể còn thông tin đăng nhập cũ; server chỉ đọc database sau khi nhập thành công, không đồng bộ ngược JSON. Không khôi phục JSON cũ đè lên database đang sử dụng. Chỉ chạy một server trên mỗi thư mục dữ liệu; dừng server trước khi sao lưu; sao chép cả `battleship.db` và thư mục `pending-results/` nếu còn kết quả chờ.

Trong sảnh chọn **Tài khoản → Đổi mật khẩu** để nhập mật khẩu hiện tại, mật khẩu mới và xác nhận. Chỉ đổi khi rảnh ở sảnh (kể cả đang chờ hết phạt); không đổi khi tìm trận, ở phòng hoặc đang chơi. Đổi thành công giữ phiên hiện tại, ID, điểm, lịch sử và chat. Chọn **Tài khoản → Xóa tài khoản**, nhập lại mật khẩu và xác nhận để xóa tài khoản đang đăng nhập. Server chỉ cho xóa khi ở sảnh, kiểm tra mật khẩu và chờ các kết quả chưa lưu. Username/hash bị xóa khỏi database đang dùng; lịch sử trận giữ ID ẩn danh để không làm sai thống kê đối thủ. Người chơi bị đăng xuất; nếu tạo lại cùng tên thì là tài khoản mới, không nhận lại điểm/ID cũ. Các bản lưu JSON có trước chuyển đổi không được tự xóa.

Rời trận online đang RUNNING bị trừ tối đa 3 điểm (không dưới 0) và cấm vào trận 60 giây. Đăng nhập/restart không xóa hạn phạt đã lưu. Trước khi cập nhật điểm, server đồng bộ kết quả vào journal `data/pending-results/`, rồi commit SQLite. Nếu transaction thất bại, điểm chưa đổi, người rời trận vẫn bị chặn; server thử lại mỗi giây và khi khởi động lại. Kết quả đã được ghi journal được phục hồi đúng một lần. Nếu journal cũng không ghi được thì kết quả chỉ còn trong RAM và chưa được bảo đảm qua crash; giao diện báo chờ lưu. Journal hỏng khiến khởi động thất bại có thông báo, giữ nguyên file để xử lý, không tự bỏ kết quả.

Có thể dùng dữ liệu riêng cho demo/test:

```powershell
java '-Dbattleship.data_dir=E:\battle-demo-data' '-Dfile.encoding=UTF-8' -cp 'bin;lib/*' server.ServerMain 9999
```

| Property | Mặc định |
| --- | ---: |
| `battleship.turn_ms` | 15000 |
| `battleship.placement_ms` | 120000 |
| `battleship.slow_connection_ms` | 250 |
| `battleship.heartbeat_ms` | 5000 |
| `battleship.idle_ms` | 20000 |
| `battleship.invite_ms` | 30000 |
| `battleship.rematch_ms` | 30000 |

Giá trị phải dương; server/client cần dùng heartbeat phù hợp nhau.

## Đấu máy, bản đồ và âm thanh

Trong sảnh chọn **Đấu máy** rồi chọn Dễ/Vừa/Khó. Đây là luyện tập, không thay đổi điểm/xếp hạng và không phạt khi rời trận. Vẫn dùng server, đặt tàu, thời gian lượt và luật bắn như trận online; có thể chơi lại với máy.

- **Dễ:** chọn ngẫu nhiên trong các ô chưa bắn.
- **Vừa:** săn theo ô xen kẽ và nhắm các ô cạnh vị trí bắn trúng.
- **Khó:** tính mật độ các vị trí hợp lệ của tàu còn lại, ưu tiên vị trí giải thích được những ô đã trúng. Máy chỉ nhận kết quả bắn công khai, không đọc bản đồ bí mật.

Đến lượt bạn, bản đồ đối thủ được phóng lớn để ngắm bắn; đến lượt đối thủ, bản đồ của bạn được phóng lớn để quan sát. Bật/tắt **Bản đồ thu nhỏ** ở góc trên; lựa chọn được lưu trên máy. Bom bay từ bản đồ nhỏ tới mục tiêu, với tia lửa và vòng nổ khi đánh chìm tàu. Hiệu ứng chỉ hiển thị kết quả server, không đổi luật hoặc kéo dài lượt.

Nút **Cài đặt** ở sảnh, ở màn hình bố trí tàu và ở tiêu đề màn hình chiến đấu mở cửa sổ chỉnh **ba mức âm lượng** (tổng, hiệu ứng, tiếng biển) cùng các hộp kiểm **Bật âm thanh**, **Sóng biển động** và **Bản đồ thu nhỏ trong trận**; thay đổi áp dụng ngay và được ghi nhớ trên máy. Âm thanh được tổng hợp tại máy, gồm bấm nút, chọn ô, phóng bom, trượt, trúng, chìm tàu, tới lượt, thắng/thua, tiếng nước bắn, tiếng sóng lớn và tiếng sonar; nền trận đấu là tiếng sóng biển lặp liền mạch. Mọi tiếng vào/ra ở mức 0, tiếng ồn được lọc thông thấp và các tiếng lớn được xếp lớp nên nghe mềm, không "tách" hay giật ở đầu/cuối; tiếng biển tự hạ xuống khi có phát đạn lớn rồi trở lại. Không cần tải âm thanh từ Internet; máy không có thiết bị âm thanh vẫn chạy game.

## Cảnh biển trong trận

Màn hình **Bố trí hạm đội** và **Chiến đấu** được dựng như đang ở ngoài biển: nền sóng cuộn nhiều lớp có bọt, mặt trời và cột nắng, hải âu, đảo xa và thuyền buồm; bàn cờ là mặt nước trong suốt có lưới ánh sáng, sonar quét qua và thước độ sâu; nhật ký hải chiến chạy ngang dưới bàn cờ. Khi vào trận có hiệu ứng **sóng quét qua màn hình** cùng tấm biển RA KHƠI/VÀO TRẬN, la bàn, tàu khu trục chạy ngang và các vòng sonar lan ra — hiệu ứng không chặn thao tác, bàn cờ và nút bắn vẫn dùng được ngay. Bắn trượt tạo cột nước bắn lên cùng vòng bọt, bắn trúng có cầu lửa và khói, tàu chìm sủi bọt khí.

Cảnh biển vẽ hoàn toàn bằng Java2D (không tài nguyên ngoài), tự giảm tốc khi máy bận và tắt được bằng hộp kiểm **Sóng biển** trong trận hoặc trong cửa sổ **Cài đặt** (lựa chọn được ghi nhớ trên máy). Ảnh và GIF minh họa nằm trong [UI_GUIDE.md](UI_GUIDE.md).

## Kết quả trận, lịch sử đấu và xem lại trận

Khi trận kết thúc, màn hình kết quả phủ toàn cửa sổ: chữ **WIN** (vàng – mint) hoặc **LOSE** (san hô – tím) rơi xuống từng chữ một kèm quầng sáng, vệt nắng quay và tàu chạy ngang khi thắng, hoặc mưa xiên và xác tàu chìm khi thua. Sau đó **hai hải phận mở tung từ đường giữa**: bên trái là hạm đội của bạn với các phát bắn của đối thủ, bên phải là hạm đội đối thủ (đã được server công bố) với các phát bắn của bạn, kèm số phát bắn, độ chính xác, thời lượng và lý do kết thúc. Hai nút **Chơi lại** và **Về phòng chỉ huy** dùng được ngay cả khi hiệu ứng đang chạy.

Sảnh có nút **Lịch sử đấu** ở cột bên trái để xem hồ sơ của chính bạn, và nút **Lịch sử** cạnh **Nhắn tin** để xem hồ sơ của người đang chọn. Cửa sổ hiển thị 20 trận mỗi trang; dùng **Xem thêm**, lọc **Tất cả/Thắng/Thua**, tên đối thủ và khoảng ngày `dd/MM/yyyy` theo múi giờ hiển thị. Bảng cuộn ngang khi cửa sổ nhỏ. Mỗi dòng có đối thủ, kết quả thắng/thua, điểm nhận, số phát bắn, thời lượng, lý do kết thúc và cột **Hải phận**; dữ liệu đọc từ bảng `matches` trên server, client không tự tính kết quả.

**Nhấn vào một dòng để xem lại cả trận:** server trả hai hạm đội và toàn bộ nhật ký bắn (`GET_MATCH_DETAIL_REQ`), client mở màn hình hồi tượng với hai bàn cờ 10×10 cạnh nhau đã dựng sẵn mọi vết bắn, kèm danh sách đường đạn theo đúng thứ tự bắn; màn hình chỉ để xem toàn bộ diễn biến một lúc, không tua lại từng phát. Tàu của hai bên chỉ được lưu và công bố **sau khi trận kết thúc**, và chỉ hai đấu thủ của trận xem được (người khác nhận `FORBIDDEN`); trận cũ ghi trước khi có tính năng này báo không có dữ liệu. Suốt trận, tọa độ tàu đối phương vẫn không được gửi về client.

Giao diện cũng được tăng màu: mỗi khu vực một màu nhấn (điểm hổ phách, thắng xanh lá, tỷ lệ xanh da trời, lịch sử hồng tím), thẻ nền chuyển hai màu, bảng tô màu theo cột, nhật ký trận tô màu theo từng phát bắn, và cảnh biển trong trận đổi sang hoàng hôn tím – cam – xanh ngọc.

## Chat riêng giữa hai người chơi

- Ở sảnh: chọn người trong **Chỉ huy trực tuyến** rồi bấm **Nhắn tin**. Có thể đổi người nhận trong danh sách trên khung chat; danh sách cũng chứa các cuộc trò chuyện gần đây, kể cả người đã offline.
- Khi vào trận với người thật: khung chat tự chọn đối thủ. Lịch sử và bản nháp đi cùng bạn từ sảnh sang xếp tàu/trận đấu và ngược lại.
- Bấm thanh tiêu đề **Chat / Tin nhắn** để mở hoặc thu gọn. Khung nằm ở góc dưới bên phải, không thu nhỏ bản đồ; khi mở có thể che vùng phụ, thu gọn để xem lại. Tin mới khi thu gọn hiện số chưa đọc.
- **Enter** gửi, **Shift+Enter** xuống dòng; tối đa 500 ký tự mỗi tin. Tin chỉ hiện là đã gửi sau khi server lưu thành công; lỗi gửi giữ nội dung để thử lại. Bấm **Tin nhắn trước** để tải lịch sử cũ.
- Lưu trong bảng `chat_messages` của `data/battleship.db` trên **máy chạy server**. Lịch sử còn sau khi đóng game/server. Nếu người nhận offline, tin chờ họ đăng nhập; bot không có tài khoản chat.

Cần dùng client và server bản mới để chat. Kiểm tra riêng bằng `powershell -ExecutionPolicy Bypass -File .\run_tests.ps1 -Chat` hoặc `-ChatGui -RealTimer`.

## LAN và xử lý kết nối

Windows và Mac có thể chơi chung bằng cách kết nối đến **cùng một server**:

1. Cho hai máy vào cùng mạng Wi-Fi/LAN. Chọn một máy chạy server, Windows hoặc Mac đều được.
2. Trên máy server, tìm IPv4 của kết nối mạng: Windows chạy `ipconfig`; Mac xem địa chỉ IP của kết nối Wi-Fi/Ethernet đang dùng trong cài đặt mạng.
3. Ví dụ server có IP `192.168.1.10`: máy còn lại nhập **Máy chủ = `192.168.1.10`**, **Cổng = `8888`** trong màn hình đăng nhập. `localhost` chỉ trỏ về chính máy đang chạy client; không dùng nó để kết nối máy khác.
4. Cho phép Java nhận kết nối đến trên firewall của máy server. Không cần tắt toàn bộ firewall. Hai client dùng hai tài khoản khác nhau, rồi tạo/vào cùng phòng hoặc ghép trận nhanh.

Nếu không kết nối được: kiểm tra server còn chạy, host/cổng chính xác, hai máy cùng mạng, mạng không chặn liên lạc giữa các thiết bị và firewall cho phép kết nối. Windows có thể kiểm tra `Test-NetConnection 192.168.1.10 -Port 8888`; trên macOS dùng `nc -vz 192.168.1.10 8888` (thay IP bằng IP thực tế). Trùng cổng khiến server báo lỗi khởi động. Hai máy LAN chưa được kiểm thử trong môi trường này; localhost đã được kiểm thử.

## Mã nguồn và tài liệu

- `src/common`: config, model, JSON/envelope và transport framing dùng chung.
- `src/server/network`: listener và handler; `src/server/manager`: user/lobby/match/storage; `src/server/model`: room và state machine trận.
- `src/client`: controller, kết nối bất đồng bộ và giao diện Swing.
- `tests`: protocol, TCP integration, race, domain/storage và GUI smoke.
- [NETWORK_PROTOCOL.md](NETWORK_PROTOCOL.md): schema và mã lỗi; [GAME_RULES.md](GAME_RULES.md): luật cuối; [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md): khóa, lifecycle và lưu trữ; [TASKS_AND_MEMBERS.md](TASKS_AND_MEMBERS.md): phân công, nguồn Word và kết quả test.

TCP hiện tại không dùng TLS. Phạm vi là đồ án Java TCP trong môi trường demo/LAN; chưa kiểm thử triển khai Internet hoặc soak test dài ngày.

## Luồng hoàn thiện và thử nhanh

1. Đăng nhập: tên chưa tồn tại sẽ tạo tài khoản; màn hình có chú thích rõ.
2. Vào trận: đặt tàu trong 120 giây, cảnh báo khi còn 30 giây. Ready/hủy Ready không gia hạn. Hết giờ chuẩn bị trả cả hai về sảnh, không tính trận hoặc phạt. Luyện tập và chơi lại cũng có hạn mới.
3. Trong màn hình đặt tàu, bấm **Mẫu bố trí (5 ô lưu)**; chọn ô để lưu/dùng/đổi tên/xóa. Chỉ lưu bộ tàu hợp lệ; mẫu theo máy chủ và ID tài khoản trên máy đang dùng. Ready khóa cả mẫu và bàn cờ.
4. Sảnh/đặt tàu/chiến đấu hiển thị thời gian khứ hồi: dưới 250 ms là kết nối tốt, từ 250 ms là chậm. Đang đo hoặc mất kết nối có trạng thái riêng; độ trễ không thay đổi luật 15 giây.
5. Kết quả chờ lưu khóa chơi lại; sau khi lưu xong, điểm trên màn hình kết quả được cập nhật. Trận do rời/mất mạng trở về sảnh theo luồng hiện có.
6. Lịch sử hỗ trợ tải thêm/lọc; đổi lọc sẽ bỏ phản hồi của truy vấn cũ. Replay vẫn chỉ dành cho hai đấu thủ.

Kiểm thử riêng phần hoàn thiện: `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Upgrade`; giao diện: thêm `-UpgradeGui -RealTimer` thay cho `-Upgrade`. Bộ kiểm thử mặc định và `-Gui -RealTimer` cũng bao gồm các ca mới.

Script Windows ưu tiên JAVA_HOME, sau đó Java trực tiếp trong PATH và các JDK cài ở Program Files. Các shim Oracle javapath được bỏ qua để tránh treo launcher; không thay cấu hình Java toàn máy.

Sao lưu: dừng server, sao chép nguyên thư mục dữ liệu gồm database và `pending-results/`, khôi phục cùng nhau vào thư mục riêng rồi chỉ chạy một server. Journal giới hạn 64 MiB, mỗi kết quả tối đa 1 MiB; file được dọn sau commit. File `.tmp` chưa công bố được giữ để đối chiếu và báo trong log. Bảo đảm đã kiểm chứng là phục hồi sau crash tiến trình; chưa kiểm chứng mất điện đột ngột hoặc hỏng thiết bị lưu trữ.


Sau khi build, kiểm tra bản JAR bằng `powershell -NoProfile -ExecutionPolicy Bypass -File .\run_tests.ps1 -Package -RealTimer`. Lệnh này chạy server JAR từ thư mục tạm và client từ JAR độc lập; không dùng dữ liệu thật. Bằng chứng nghiệm thu ngày 26/10 nằm trong TASKS_AND_MEMBERS.md; LAN hai máy, macOS và runtime Java 8 vẫn NOT-RUN.
