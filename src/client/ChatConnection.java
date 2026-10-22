package client;

import common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Kết nối socket từ client tới MiniChat Server.
 * Một luồng riêng đọc dữ liệu từ server và đẩy từng lệnh (đã tách trường) cho listener.
 */
public class ChatConnection {

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;

    public ChatConnection(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Gửi 1 lệnh rồi chờ đúng 1 dòng trả lời (chỉ dùng trước khi gọi listen). */
    public String[] request(Object... fields) throws IOException {
        send(fields);
        socket.setSoTimeout(15000);
        try {
            String line = in.readLine();
            if (line == null) throw new IOException("Server đóng kết nối");
            return Protocol.decode(line);
        } finally {
            socket.setSoTimeout(0);
        }
    }

    /** Đăng ký tài khoản. Trả về null nếu thành công, ngược lại là lý do lỗi. */
    public String register(String user, String pass, String displayName) throws IOException {
        String[] r = request(Protocol.REGISTER, user, pass, displayName);
        return Protocol.REGISTER_OK.equals(r[0]) ? null : (r.length > 1 ? r[1] : "Đăng ký thất bại");
    }

    /** Đăng nhập. Trả về [username, displayName] nếu thành công, ném IOException kèm lý do nếu thất bại. */
    public String[] login(String user, String pass) throws IOException {
        String[] r = request(Protocol.LOGIN, user, pass);
        if (Protocol.AUTH_OK.equals(r[0]) && r.length >= 3) return new String[]{r[1], r[2]};
        throw new IOException(r.length > 1 ? r[1] : "Đăng nhập thất bại");
    }

    /** Bắt đầu luồng lắng nghe tin từ server. onClose được gọi khi mất kết nối. */
    public void listen(Consumer<String[]> onMessage, Runnable onClose) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (!line.isEmpty()) onMessage.accept(Protocol.decode(line));
                }
            } catch (IOException ignored) {
            }
            onClose.run();
        }, "minichat-reader");
        t.setDaemon(true);
        t.start();
    }

    /** Gửi 1 lệnh. Đồng bộ để luồng gửi file và luồng giao diện không ghi chồng lên nhau. */
    public synchronized void send(Object... fields) {
        try {
            out.write(Protocol.encode(fields));
            out.write('\n');
            out.flush();
        } catch (IOException ignored) {
            // mất kết nối: luồng đọc sẽ phát hiện và báo onClose
        }
    }

    public void close() {
        send(Protocol.QUIT);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
