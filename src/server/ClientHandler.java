package server;

import common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Mỗi client kết nối tới server được phục vụ bởi 1 ClientHandler gồm 2 luồng:
 * - luồng đọc: nhận lệnh từ client và chuyển cho ChatServer xử lý;
 * - luồng ghi: lấy tin từ hàng đợi và gửi xuống socket, nhờ vậy một client mạng chậm
 *   không làm cả server bị nghẽn.
 */
public class ClientHandler implements Runnable {

    /** Dấu hiệu dừng luồng ghi. */
    private static final String STOP = new String("STOP");

    private final Socket socket;
    private final ChatServer server;
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private volatile String user;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    /** Tên đăng nhập (null nếu chưa đăng nhập). */
    public String getUser() {
        return user;
    }

    void setUser(String user) {
        this.user = user;
    }

    /** Quá số dòng này chưa gửi được thì coi như client treo và ngắt kết nối. */
    private static final int MAX_OUTBOX = 20_000;

    /** Đưa 1 dòng vào hàng đợi gửi. */
    public void send(String line) {
        if (outbox.size() >= MAX_OUTBOX) {
            ChatServer.log("Client " + user + " nhan qua cham, ngat ket noi");
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }
        outbox.offer(line);
    }

    public void send(Object... fields) {
        send(Protocol.encode(fields));
    }

    @Override
    public void run() {
        Thread writer = new Thread(this::writeLoop, "writer-" + socket.getPort());
        writer.setDaemon(true);
        writer.start();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = readLine(in, user == null ? Protocol.MAX_LINE_GUEST : Protocol.MAX_LINE)) != null) {
                if (line.isEmpty()) continue;
                String[] f = Protocol.decode(line);
                if (Protocol.QUIT.equals(f[0])) break;
                try {
                    server.handle(this, f);
                } catch (RuntimeException e) {
                    ChatServer.log("Loi xu ly lenh " + f[0] + ": " + e);
                    send(Protocol.ERROR, "Lệnh không hợp lệ: " + f[0]);
                }
            }
        } catch (IOException e) {
            // client mất kết nối
        } finally {
            server.disconnect(this);
            outbox.offer(STOP);
        }
    }

    /** Như BufferedReader.readLine nhưng giới hạn độ dài để client xấu không làm tràn bộ nhớ server. */
    private static String readLine(BufferedReader in, int max) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') return sb.toString();
            if (ch == '\r') continue;
            if (sb.length() >= max) throw new IOException("Dong lenh qua dai");
            sb.append((char) ch);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void writeLoop() {
        try (Writer out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                String line = outbox.take();
                if (line == STOP) break;
                out.write(line);
                out.write('\n');
                if (outbox.isEmpty()) out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // socket đã đóng
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
