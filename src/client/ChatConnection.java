package client;

import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Kết nối socket từ client tới MiniChat Server.
 * Một luồng riêng đọc dữ liệu từ server và đẩy từng dòng cho listener.
 */
public class ChatConnection {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public ChatConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /** Gửi LOGIN và chờ phản hồi. Trả về null nếu thành công, ngược lại là lý do lỗi. */
    public String login(String name) throws IOException {
        out.println(Protocol.LOGIN + " " + name);
        String line = in.readLine();
        if (line == null) return "Server dong ket noi";
        if (line.startsWith(Protocol.LOGIN_OK)) return null;
        return line.startsWith(Protocol.LOGIN_FAIL) ? line.substring(Protocol.LOGIN_FAIL.length()).trim() : line;
    }

    /** Bắt đầu luồng lắng nghe tin từ server. onClose được gọi khi mất kết nối. */
    public void listen(Consumer<String> onMessage, Runnable onClose) {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) onMessage.accept(line);
            } catch (IOException ignored) {
            }
            onClose.run();
        });
        t.setDaemon(true);
        t.start();
    }

    public void send(String line) {
        out.println(line);
    }

    public void close() {
        try {
            out.println(Protocol.QUIT);
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
