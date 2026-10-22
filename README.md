# MiniChat – Chat Client/Server bằng Java Socket

## Kiến trúc
- **Client/Server**, Java (JDK 17), `java.net.Socket` / `ServerSocket`.
- **Server** (`server.ChatServer`): luôn chờ kết nối, mỗi client 1 luồng (`ClientHandler`),
  quản lí danh sách kết nối + danh sách nhóm, phân phối tin nhắn.
- **Client**: có 2 phiên bản
  - `client.ChatClientGUI` – giao diện Swing.
  - `client.ConsoleClient` – chạy trên cmd.

## Chức năng (đủ cả 3)
| Chức năng | GUI | Console |
|---|---|---|
| Chat chung (tất cả user) | Tab **Chat chung** | gõ nội dung trực tiếp |
| Tạo nhóm | nút **Tao** | `/create <nhom>` |
| Tham gia nhóm có sẵn | chọn nhóm → **Vao** (hoặc double-click) | `/join <nhom>` |
| Rời nhóm | **Roi** | `/leave <nhom>` |
| Chat nhóm | Tab **#nhom** | `/g <nhom> <noi dung>` |
| Chat riêng | double-click user → Tab **@user** | `/w <user> <noi dung>` |

Quy trình đúng theo đề: vừa kết nối user chưa thuộc nhóm nào; tạo nhóm thì người tạo tự vào nhóm;
chat nhóm chỉ gửi cho thành viên nhóm; server dùng danh sách socket đã lưu để chuyển tin.

## Cấu trúc
```
src/common/Protocol.java        giao thức text giữa client và server
src/server/ChatServer.java      server: quản lí user, nhóm, phân phối tin
src/server/ClientHandler.java   luồng xử lý cho từng client
src/client/ChatConnection.java  kết nối socket phía client
src/client/ChatClientGUI.java   client giao diện Swing
src/client/ConsoleClient.java   client chạy cmd
```

## Chạy bằng Eclipse
1. `File > Import > General > Existing Projects into Workspace` → chọn thư mục `MiniChat`.
2. Chạy `server/ChatServer.java` (Run As > Java Application) – port mặc định 5000.
3. Chạy `client/ChatClientGUI.java` nhiều lần (mỗi lần là 1 user), nhập tên rồi chat.

## Chạy bằng cmd (cần JDK 17 trong PATH)
```
build.bat               biên dịch vào bin/
run-server.bat          chạy server
run-client-gui.bat      mở client giao diện (mở nhiều lần cho nhiều user)
run-client-console.bat  client dạng cmd
```
Chat qua mạng LAN: ở client nhập IP máy chạy server thay cho `localhost`.
