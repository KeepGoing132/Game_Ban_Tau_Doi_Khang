# Battleship Online

Giao diện **Naval Command**: đăng nhập, phòng chỉ huy, bố trí hạm đội có xem trước, chiến đấu với nhật ký, kết quả và bảng xếp hạng. Hai màn hình trong trận là cảnh biển động có hiệu ứng sóng quét khi vào trận. Xem [hướng dẫn giao diện và ảnh minh họa](UI_GUIDE.md).

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
3. Rời cửa sổ đặt tàu: cả hai về sảnh, không cộng điểm. Thử tiếp bằng lời mời Accept/Reject, rồi Quick Match; thử hủy tìm trận trước khi có người thứ hai.
4. Đặt đúng 5 tàu `[5,4,3,3,2]`; có thể dùng Xếp tàu ngẫu nhiên. Bấm Ready: chờ xác nhận server rồi khóa sửa tàu. Thử Hủy sẵn sàng trước khi đối thủ Ready, sửa rồi Ready lại.
5. Cả hai Ready: chỉ có một người được bắn trước. Chọn ô rồi Bắn. HIT/SUNK giữ lượt, MISS đổi lượt. Đợi 15 giây không bắn: mất lượt, không có phát bắn tự động.
6. Bắn hết 17 ô tàu: thắng +3, thua +0, mỗi bên tăng một trận. Đầu hàng/đóng cửa sổ/mất kết nối khi đang đấu: người rời trận bị trừ 3 điểm (không dưới 0), cấm ghép trận 1 phút; đối thủ thắng +3. Sảnh hiển thị đếm ngược và khóa ghép nhanh, tạo/vào phòng, lời mời. Đăng nhập lại không xóa hạn phạt. Mở xếp hạng để kiểm tra số liệu.
7. Sau kết thúc bình thường, cả hai Chơi lại trong 30 giây: trở lại đặt tàu với match ID mới, timer của ván hai hoạt động. Từ chối, thoát hoặc hết hạn thì về sảnh.
8. Dừng server rồi chạy lại: điểm và lịch sử giữ nguyên. Có thể mở bốn client, ghép hai cặp và cho một trận timeout/thoát để chứng minh trận còn lại tiếp tục.

Muốn quan sát HIT/SUNK nhanh, hai người có thể tự đặt tàu ngang lần lượt từ cột A ở các hàng 1–5, chiều dài 5,4,3,3,2. Trong demo bình thường, client vẫn không nhận board bí mật của đối thủ.

## Dữ liệu và cấu hình

Server lưu database SQLite tại **`data/battleship.db`** (tương đối với thư mục chạy lệnh). `accounts` chứa username, mật khẩu băm PBKDF2-HMAC-SHA256 có salt/120000 vòng, điểm, thống kê và hạn phạt; `matches` chứa lịch sử; `metadata` quản lý phiên bản schema. Transaction ghi tài khoản và kết quả cùng nhau. Không lưu mật khẩu gốc trong database. JDBC 3.53.4.0 nằm trong `lib/`; bản server JAR cần `dist/lib/` đi kèm.

Lần đầu, server tự nhập `snapshot.json` hiện có; nếu chưa có snapshot thì nhập `users.json`/`matches.json`. ID, mật khẩu băm, điểm, lịch sử và thời gian phạt được giữ. File nguồn cũ được giữ nguyên làm bản lưu trước chuyển đổi, có thể còn thông tin đăng nhập cũ; server chỉ đọc database sau khi nhập thành công, không đồng bộ ngược JSON. Không khôi phục JSON cũ đè lên database đang sử dụng. Chỉ chạy một server trên mỗi thư mục dữ liệu; dừng server trước khi sao lưu/copy `battleship.db`.

Trong sảnh chọn **Tài khoản**, nhập lại mật khẩu và xác nhận để xóa tài khoản đang đăng nhập. Server chỉ cho xóa khi ở sảnh, kiểm tra mật khẩu và chờ các kết quả chưa lưu. Username/hash bị xóa khỏi database đang dùng; lịch sử trận giữ ID ẩn danh để không làm sai thống kê đối thủ. Người chơi bị đăng xuất; nếu tạo lại cùng tên thì là tài khoản mới, không nhận lại điểm/ID cũ. Các bản lưu JSON có trước chuyển đổi không được tự xóa.

Rời trận online đang RUNNING bị trừ tối đa 3 điểm (không dưới 0) và cấm vào trận 60 giây. Đăng nhập/restart không xóa hạn phạt đã lưu. Nếu transaction thất bại, kết quả chờ trong RAM và retry mỗi giây; điểm chưa đổi, người rời trận vẫn bị chặn. Dừng/crash server khi còn kết quả chưa commit không bảo đảm lưu được kết quả đó.

Có thể dùng dữ liệu riêng cho demo/test:

```powershell
java '-Dbattleship.data_dir=E:\battle-demo-data' '-Dfile.encoding=UTF-8' -cp 'bin;lib/*' server.ServerMain 9999
```

| Property | Mặc định |
| --- | ---: |
| `battleship.turn_ms` | 15000 |
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

Nút **Âm thanh: Bật/Tắt** có trong sảnh và trận đấu. Âm thanh được tổng hợp tại máy, gồm bấm nút, chọn ô, phóng bom, trượt, trúng, chìm tàu, tới lượt, thắng/thua, tiếng nước bắn, tiếng sóng lớn và tiếng sonar; nền trận đấu là tiếng sóng biển lặp liền mạch. Không cần tải âm thanh từ Internet; máy không có thiết bị âm thanh vẫn chạy game.

## Cảnh biển trong trận

Màn hình **Bố trí hạm đội** và **Chiến đấu** được dựng như đang ở ngoài biển: nền sóng cuộn nhiều lớp có bọt, mặt trời và cột nắng, hải âu, đảo xa và thuyền buồm; bàn cờ là mặt nước trong suốt có lưới ánh sáng, sonar quét qua và thước độ sâu; nhật ký hải chiến chạy ngang dưới bàn cờ. Khi vào trận có hiệu ứng **sóng quét qua màn hình** cùng tấm biển RA KHƠI/VÀO TRẬN, la bàn, tàu khu trục chạy ngang và các vòng sonar lan ra — hiệu ứng không chặn thao tác, bàn cờ và nút bắn vẫn dùng được ngay. Bắn trượt tạo cột nước bắn lên cùng vòng bọt, bắn trúng có cầu lửa và khói, tàu chìm sủi bọt khí.

Cảnh biển vẽ hoàn toàn bằng Java2D (không tài nguyên ngoài), tự giảm tốc khi máy bận và tắt được bằng nút **Sóng biển: Bật/Tắt** trong trận hoặc **Biển động: Bật/Tắt** ở sảnh (lựa chọn được ghi nhớ trên máy). Ảnh và GIF minh họa nằm trong [UI_GUIDE.md](UI_GUIDE.md).

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
