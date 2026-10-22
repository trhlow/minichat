# MiniChat – Ứng dụng chat realtime kiểu Zalo bằng Java Socket

Ứng dụng chat **Client/Server** viết bằng Java thuần (JDK 17+, `java.net.Socket` / `ServerSocket`, Swing),
không dùng thư viện ngoài. Giao diện và chức năng mô phỏng **Zalo PC**.

## Chức năng

| Nhóm | Chức năng |
|---|---|
| Tài khoản | Đăng ký / đăng nhập bằng mật khẩu (băm SHA-256 + salt), đổi tên hiển thị, đăng nhập nhiều máy cùng lúc, đăng xuất |
| Trạng thái | Online / "Truy cập x phút trước", **đang soạn tin…**, **Đã gửi / Đã nhận / Đã xem** (nhóm: "Đã xem: A, B") |
| Hội thoại | **Chat chung** (mọi người), **chat riêng 1-1**, **chat nhóm**; danh sách hội thoại sắp theo tin mới nhất, xem trước tin cuối, **số tin chưa đọc**, tìm kiếm |
| Tin nhắn | Văn bản nhiều dòng, emoji, **trả lời (trích dẫn)**, **thả cảm xúc** ❤️👍😆😮😭😡, **thu hồi** (trong 24h), **xoá ở phía tôi**, **chuyển tiếp**, sao chép, tìm tin nhắn, nút 👍 gửi nhanh |
| File | Gửi **ảnh** (xem trước trong khung chat, bấm để phóng to/lưu) và **file** bất kỳ ≤ 10 MB; chọn file, **kéo thả**, hoặc **dán ảnh Ctrl+V** |
| Bạn bè | Gửi / đồng ý / từ chối / thu hồi lời mời kết bạn, huỷ kết bạn, gợi ý kết bạn, thanh "Kết bạn" khi chat với người lạ |
| Nhóm | Tạo nhóm (chọn nhiều thành viên), tham gia nhóm có sẵn, thêm thành viên, trưởng nhóm mời ra / giải tán, đổi tên, rời nhóm (tự chuyển trưởng nhóm), tin hệ thống |
| Lịch sử | Server lưu toàn bộ dữ liệu vào `data/` → nhận **tin nhắn khi offline**, cuộn lên để tải tin cũ hơn (phân trang) |
| Thông báo | Huy hiệu số chưa đọc, tiêu đề cửa sổ `(3) MiniChat`, thông báo Windows + nháy taskbar khi cửa sổ không được chọn |

## Kiến trúc

```
                ┌──────────────── ChatServer (port 5000) ────────────────┐
 Client GUI ──► │ ClientHandler (luồng đọc + luồng ghi riêng / client)   │
 Client GUI ──► │ ChatServer: tài khoản, bạn bè, nhóm, tin nhắn, đã xem  │
 Console    ──► │ DataStore ──► data/minichat.db  +  data/files/<id>     │
                └────────────────────────────────────────────────────────┘
```

- **Server** luôn chờ kết nối; mỗi client có 1 `ClientHandler` (luồng đọc lệnh + luồng ghi có hàng đợi,
  nên client mạng chậm không làm nghẽn server). Mọi thao tác dữ liệu chạy trong `synchronized` nên an toàn đa luồng.
  Dữ liệu tự lưu mỗi 2 giây và khi tắt server.
- **Giao thức** dạng text, mỗi lệnh 1 dòng UTF-8, các trường cách nhau bằng TAB (có escape nên tin nhắn được nhiều dòng).
  Đặc tả đầy đủ ở đầu file [`src/common/Protocol.java`](src/common/Protocol.java). Ví dụ:
  ```
  C→S  SEND  u:alice:bob  Chào Bob  0
  S→C  MSG   15  u:alice:bob  alice  1761100000000  TEXT  Chào Bob  ...
  S→C  READ  u:alice:bob  bob  15
  ```
- **File/ảnh**: client gửi Base64, server lưu vào `data/files/`, tin nhắn chỉ chứa mã file; client tải về khi cần
  (`GET_FILE`) và server kiểm tra quyền (chỉ thành viên hội thoại mới tải được).

## Cấu trúc mã nguồn

```
src/common/Protocol.java        giao thức, mã hoá dòng lệnh, kiểm tra dữ liệu
src/common/Message.java         model tin nhắn (dùng chung client/server)
src/server/ChatServer.java      server: điều phối lệnh, tài khoản, bạn bè, nhóm, phân phối tin
src/server/ClientHandler.java   luồng đọc/ghi cho từng client
src/server/DataStore.java       dữ liệu + lưu/nạp file, băm mật khẩu
src/client/ChatConnection.java  kết nối socket phía client
src/client/LoginFrame.java      màn hình đăng nhập / đăng ký
src/client/ChatClientGUI.java   cửa sổ chính (danh sách hội thoại, danh bạ, khung chat, hộp thoại)
src/client/MessagePanel.java    khung tin nhắn dạng bong bóng
src/client/Conversation.java    trạng thái 1 hội thoại phía client
src/client/Theme.java           màu sắc, font, avatar, icon vẽ bằng Java2D
src/client/ConsoleClient.java   client chạy trên cmd
test/MiniChatServerTest.java    kiểm thử tích hợp server qua socket thật (69 kiểm tra)
test/client/GuiSnapshot.java    dựng dữ liệu mẫu và vẽ giao diện ra ảnh PNG
```

## Chạy bằng cmd (cần JDK 17+)

Các file `.bat` **tự tìm JDK** theo thứ tự: biến `JAVA_HOME` → `PATH` → các thư mục cài đặt thường gặp
(`C:\Program Files\Java`, Adoptium, Microsoft, Zulu, `%USERPROFILE%\.jdks`, JDK đi kèm IntelliJ / Android Studio).
Nếu không tìm thấy, cài JDK 17+ (vd. https://adoptium.net) rồi đặt `JAVA_HOME`.
Script dùng đường dẫn tương đối nên chạy được cả khi thư mục dự án có dấu tiếng Việt.

```
build.bat               biên dịch vào bin/
run-server.bat          chạy server (port 5000, dữ liệu trong data/)
run-client-gui.bat      mở client giao diện (mở nhiều lần cho nhiều user)
run-client-console.bat  client dạng cmd (gõ /help để xem lệnh)
test.bat                chạy kiểm thử tự động + xuất ảnh giao diện vào snapshots/
```

Chat qua mạng LAN: ở màn hình đăng nhập bấm **Cài đặt máy chủ** và nhập IP máy chạy server.

## Chạy bằng Eclipse / VS Code

- **Eclipse**: `File > Import > General > Existing Projects into Workspace` → chọn thư mục `MiniChat`.
  Chạy `server/ChatServer.java`, sau đó chạy `client/ChatClientGUI.java` nhiều lần (mỗi lần 1 tài khoản).
- **VS Code**: mở thư mục, dùng cấu hình chạy có sẵn `Server + 2 Client GUI` trong `.vscode/launch.json`.

## Hướng dẫn nhanh

1. Mở client → tab **Đăng ký** → tạo tài khoản (tên đăng nhập chữ thường không dấu, tên hiển thị tuỳ ý).
2. Biểu tượng **Danh bạ** bên trái: kết bạn, đồng ý lời mời, tham gia nhóm có sẵn.
3. Nút **tạo nhóm** cạnh ô tìm kiếm: đặt tên, chọn thành viên.
4. Trong khung chat: **rê chuột** vào tin nhắn để hiện nút *Trả lời / Thả cảm xúc / Thêm*; **chuột phải** để
   thu hồi, xoá, chuyển tiếp, sao chép, lưu file. **Enter** để gửi, **Shift+Enter** để xuống dòng.

### Lệnh của client cmd

```
<noi dung>              chat chung          /w <user> <noi dung>    chat riêng
/g <maNhom> <noi dung>  chat nhóm           /reply <id> <noi dung>  trả lời tin nhắn
/recall <id>            thu hồi             /react <id> <1-6>       thả cảm xúc
/create <ten nhom>      tạo nhóm            /join, /leave <maNhom>  vào / rời nhóm
/add <maNhom> <u1,u2>   thêm thành viên     /groups, /users         danh sách
/friend <user>          kết bạn             /accept <user>          đồng ý kết bạn
```
