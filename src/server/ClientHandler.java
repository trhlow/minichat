package server;

import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Mỗi client kết nối tới server được phục vụ bởi 1 luồng ClientHandler riêng.
 * Đọc lệnh từ client và chuyển cho ChatServer xử lý.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private String name;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getName() {
        return name;
    }

    /** Gửi 1 dòng tới client (đồng bộ để tránh 2 luồng ghi đè nhau). */
    public synchronized void send(String msg) {
        if (out != null) out.println(msg);
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            if (!login(in)) return;

            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line.trim())) break;
            }
        } catch (IOException e) {
            // client mất kết nối
        } finally {
            server.removeClient(name);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Bước đăng nhập: client phải gửi LOGIN <ten> hợp lệ và chưa bị trùng. */
    private boolean login(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            String[] p = line.trim().split(" ", 2);
            if (!p[0].equals(Protocol.LOGIN) || p.length < 2) {
                send(Protocol.LOGIN_FAIL + " Hay dang nhap truoc");
                continue;
            }
            String n = p[1].trim();
            if (!Protocol.isValidName(n)) {
                send(Protocol.LOGIN_FAIL + " Ten khong hop le (chu, so, _ , -, toi da 20 ky tu)");
                continue;
            }
            name = n;
            if (server.addClient(n, this)) return true;
            name = null;
            send(Protocol.LOGIN_FAIL + " Ten '" + n + "' da duoc su dung");
        }
        return false;
    }

    /** Xử lý 1 lệnh. Trả về false nếu client muốn thoát. */
    private boolean handle(String line) {
        if (line.isEmpty()) return true;
        String[] p = line.split(" ", 2);
        String cmd = p[0];
        String rest = p.length > 1 ? p[1].trim() : "";

        switch (cmd) {
            case Protocol.CREATE:
            case Protocol.JOIN:
            case Protocol.LEAVE:
                if (!Protocol.isValidName(rest)) {
                    send(Protocol.ERROR + " Ten nhom khong hop le");
                } else if (cmd.equals(Protocol.CREATE)) {
                    server.createGroup(rest, this);
                } else if (cmd.equals(Protocol.JOIN)) {
                    server.joinGroup(rest, this);
                } else {
                    server.leaveGroup(rest, this);
                }
                break;
            case Protocol.MSG_ALL:
                if (!rest.isEmpty()) server.chatAll(this, rest);
                break;
            case Protocol.MSG_GROUP:
            case Protocol.MSG_PRIVATE: {
                String[] q = rest.split(" ", 2);
                if (q.length < 2 || q[1].trim().isEmpty()) {
                    send(Protocol.ERROR + " Cu phap: " + cmd + " <ten> <noi dung>");
                } else if (cmd.equals(Protocol.MSG_GROUP)) {
                    server.chatGroup(this, q[0], q[1].trim());
                } else {
                    server.chatPrivate(this, q[0], q[1].trim());
                }
                break;
            }
            case Protocol.QUIT:
                return false;
            default:
                send(Protocol.ERROR + " Lenh khong hop le: " + cmd);
        }
        return true;
    }
}
