import common.Message;
import common.Protocol;
import server.ChatServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Kiểm thử tích hợp MiniChat Server qua socket thật (không cần thư viện ngoài).
 * Chạy: test.bat  (hoặc javac + java MiniChatServerTest)
 */
public class MiniChatServerTest {

    private static int passed;
    private static int port;
    private static Path dataDir;

    public static void main(String[] args) throws Exception {
        dataDir = Files.createTempDirectory("minichat-test");
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        ChatServer server = startServer();
        try {
            run();
            server.stop();
            Thread.sleep(300);
            testPersistence();
        } finally {
            server.stop();
            deleteDir(dataDir);
        }
        System.out.println("\nTAT CA " + passed + " KIEM TRA DEU DAT");
        System.exit(0);
    }

    private static ChatServer startServer() throws InterruptedException {
        ChatServer server = new ChatServer(port, dataDir);
        Thread t = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.setDaemon(true);
        t.start();
        Thread.sleep(400);
        return server;
    }

    private static void run() throws Exception {
        // ---- Chống dòng lệnh quá dài trước khi đăng nhập ----
        Client bad = new Client();
        bad.out.println("x".repeat(Protocol.MAX_LINE_GUEST + 10));
        check(bad.in.readLine() == null, "dong lenh qua dai bi ngat ket noi");

        // ---- Đăng ký / đăng nhập ----
        Client a = new Client();
        check(a.request(Protocol.REGISTER, "alice", "1234", "Alice Nguyễn")[0].equals(Protocol.REGISTER_OK), "dang ky alice");
        check(a.request(Protocol.REGISTER, "ALICE", "1234", "Trùng")[0].equals(Protocol.AUTH_FAIL), "khong cho trung ten (khong phan biet hoa thuong)");
        check(a.request(Protocol.REGISTER, "a b", "1234", "X")[0].equals(Protocol.AUTH_FAIL), "ten dang nhap khong hop le");
        check(a.request(Protocol.REGISTER, "bob", "1234", "Bob Trần")[0].equals(Protocol.REGISTER_OK), "dang ky bob");
        check(a.request(Protocol.REGISTER, "carol", "1234", "Carol Lê")[0].equals(Protocol.REGISTER_OK), "dang ky carol");
        check(a.request(Protocol.LOGIN, "alice", "sai")[0].equals(Protocol.AUTH_FAIL), "sai mat khau");
        check(a.request(Protocol.LOGIN, "alice", "1234")[0].equals(Protocol.AUTH_OK), "dang nhap alice");

        Client b = Client.login("bob");
        a.expect(Protocol.USER, f -> f[1].equals("bob") && f[3].equals("1"), "alice thay bob online");
        Client c = Client.login("carol");

        // ---- Chat chung ----
        a.send(Protocol.SEND, Protocol.CONV_ALL, "Xin chào cả nhà\nDòng 2\twith tab", 0);
        String[] all = b.expect(Protocol.MSG, f -> f[2].equals(Protocol.CONV_ALL), "bob nhan chat chung");
        check(Message.fromFields(all, 1).text.equals("Xin chào cả nhà\nDòng 2\twith tab"), "noi dung nhieu dong + tab giu nguyen");
        c.expect(Protocol.MSG, f -> f[2].equals(Protocol.CONV_ALL), "carol nhan chat chung");

        // ---- Chat riêng ----
        String ab = Protocol.privateConv("alice", "bob");
        a.send(Protocol.SEND, ab, "Chào Bob 👋", 0);
        Message pm = Message.fromFields(b.expect(Protocol.MSG, f -> f[2].equals(ab), "bob nhan chat rieng"), 1);
        a.expect(Protocol.MSG, f -> f[2].equals(ab), "alice nhan lai tin minh gui");
        c.expectNone(Protocol.MSG, f -> f[2].equals(ab), "carol khong nhan chat rieng cua nguoi khac");
        c.send(Protocol.SEND, ab, "chen ngang", 0);
        c.expect(Protocol.ERROR, f -> true, "carol khong gui duoc vao hoi thoai cua nguoi khac");

        // ---- Trả lời, cảm xúc, đã xem, đang soạn ----
        b.send(Protocol.SEEN, ab, pm.id);
        a.expect(Protocol.READ, f -> f[1].equals(ab) && f[2].equals("bob") && f[3].equals(String.valueOf(pm.id)), "alice thay bob da xem");
        b.send(Protocol.SEND, ab, "Chào Alice", pm.id);
        Message reply = Message.fromFields(a.expect(Protocol.MSG, f -> f[2].equals(ab) && f[3].equals("bob"), "alice nhan tra loi"), 1);
        check(reply.replyTo == pm.id && reply.replyFrom.equals("alice") && reply.replyText.startsWith("Chào Bob"), "tin tra loi co trich dan");

        b.send(Protocol.REACT, pm.id, "❤️");
        Message upd = Message.fromFields(a.expect(Protocol.UPDATE, f -> f[1].equals(String.valueOf(pm.id)), "alice nhan cap nhat cam xuc"), 1);
        check("❤️".equals(upd.reactions.get("bob")), "cam xuc cua bob duoc luu");
        b.send(Protocol.REACT, pm.id, "❤️");
        upd = Message.fromFields(a.expect(Protocol.UPDATE, f -> f[1].equals(String.valueOf(pm.id)), "bo cam xuc"), 1);
        check(upd.reactions.isEmpty(), "tha lai cung cam xuc thi bo cam xuc");
        b.send(Protocol.REACT, pm.id, "xyz");
        b.expect(Protocol.ERROR, f -> true, "cam xuc khong hop le bi tu choi");


        b.send(Protocol.SEEN, ab, 99_999_999);
        a.expectNone(Protocol.READ, f -> f[3].equals("99999999"), "SEEN khong vuot qua tin cuoi cung");

        a.send(Protocol.TYPING, ab, 1);
        b.expect(Protocol.TYPING, f -> f[1].equals(ab) && f[2].equals("alice") && f[3].equals("1"), "bob thay alice dang soan tin");

        // ---- Thu hồi ----
        b.send(Protocol.RECALL, pm.id);
        b.expect(Protocol.ERROR, f -> true, "khong thu hoi tin cua nguoi khac");
        a.send(Protocol.RECALL, pm.id);
        upd = Message.fromFields(b.expect(Protocol.UPDATE, f -> f[1].equals(String.valueOf(pm.id)), "bob nhan thu hoi"), 1);
        check(upd.recalled && upd.text.isEmpty(), "noi dung tin thu hoi bi xoa");

        // ---- Xoá ở phía tôi + chuyển tiếp ----
        b.send(Protocol.DELETE_MSG, reply.id);
        b.expect(Protocol.DELETED, f -> f[1].equals(String.valueOf(reply.id)), "xoa o phia toi");
        b.send(Protocol.FORWARD, reply.id, Protocol.CONV_ALL);
        Message fw = Message.fromFields(c.expect(Protocol.MSG, f -> f[2].equals(Protocol.CONV_ALL), "carol nhan tin chuyen tiep"), 1);
        check(fw.forwarded && fw.text.equals("Chào Alice"), "tin chuyen tiep dung noi dung");

        // ---- Bạn bè ----
        a.send(Protocol.FRIEND_REQUEST, "bob");
        a.expect(Protocol.FRIEND, f -> f[1].equals("bob") && f[2].equals(Protocol.FRIEND_SENT), "alice da gui loi moi");
        b.expect(Protocol.FRIEND, f -> f[1].equals("alice") && f[2].equals(Protocol.FRIEND_RECEIVED), "bob nhan loi moi");
        b.send(Protocol.FRIEND_ACCEPT, "alice");
        a.expect(Protocol.FRIEND, f -> f[1].equals("bob") && f[2].equals(Protocol.FRIEND_YES), "alice va bob la ban");
        b.expect(Protocol.FRIEND, f -> f[1].equals("alice") && f[2].equals(Protocol.FRIEND_YES), "bob va alice la ban");
        c.send(Protocol.FRIEND_REQUEST, "alice");
        a.expect(Protocol.FRIEND, f -> f[1].equals("carol") && f[2].equals(Protocol.FRIEND_RECEIVED), "alice nhan loi moi cua carol");
        a.send(Protocol.FRIEND_DECLINE, "carol");
        c.expect(Protocol.FRIEND, f -> f[1].equals("alice") && f[2].equals(Protocol.FRIEND_NONE), "alice tu choi carol");

        // ---- Nhóm ----
        a.send(Protocol.GROUP_CREATE, "Nhóm Lập Trình", "bob");
        String[] g = b.expect(Protocol.GROUP, f -> f[2].equals("Nhóm Lập Trình"), "bob thay nhom moi");
        String gid = g[1];
        String gconv = Protocol.groupConv(gid);
        check(g[3].equals("alice") && g[4].equals("alice,bob"), "alice la truong nhom, co bob");
        a.expect(Protocol.OPEN, f -> f[1].equals(gconv), "alice duoc mo nhom vua tao");
        b.expect(Protocol.HIST, f -> f[2].equals(gconv) && f[5].equals(Message.SYSTEM), "bob nhan tin he thong tao nhom");
        c.send(Protocol.SEND, gconv, "chua vao nhom", 0);
        c.expect(Protocol.ERROR, f -> true, "chua vao nhom thi khong chat duoc");
        a.send(Protocol.SEND, gconv, "Tin cu truoc khi carol vao", 0);
        b.expect(Protocol.MSG, f -> f[2].equals(gconv) && f[6].startsWith("Tin cu"), "bob nhan tin nhom");
        c.send(Protocol.GROUP_JOIN, gid);
        c.expect(Protocol.UNREAD, f -> f[1].equals(gconv) && f[2].equals("0"), "thanh vien moi khong bi tinh tin cu la chua doc");
        c.expect(Protocol.OPEN, f -> f[1].equals(gconv), "carol tham gia nhom");
        a.expect(Protocol.MSG, f -> f[2].equals(gconv) && f[6].contains("đã tham gia"), "thong bao carol tham gia");
        b.send(Protocol.SEND, gconv, "Hello nhóm", 0);
        a.expect(Protocol.MSG, f -> f[2].equals(gconv) && f[6].equals("Hello nhóm"), "alice nhan chat nhom");
        c.expect(Protocol.MSG, f -> f[2].equals(gconv) && f[6].equals("Hello nhóm"), "carol nhan chat nhom");
        b.send(Protocol.GROUP_KICK, gid, "carol");
        b.expect(Protocol.ERROR, f -> true, "chi truong nhom moi kick duoc");
        a.send(Protocol.GROUP_KICK, gid, "carol");
        c.expect(Protocol.GROUP, f -> f[1].equals(gid) && !f[4].contains("carol"), "carol bi moi ra khoi nhom");
        b.send(Protocol.SEND, gconv, "Sau khi kick", 0);
        c.expectNone(Protocol.MSG, f -> f[2].equals(gconv), "carol khong con nhan tin nhom");
        a.send(Protocol.GROUP_RENAME, gid, "Nhóm Java");
        b.expect(Protocol.GROUP, f -> f[1].equals(gid) && f[2].equals("Nhóm Java"), "doi ten nhom");
        a.send(Protocol.GROUP_LEAVE, gid);
        b.expect(Protocol.GROUP, f -> f[1].equals(gid) && f[3].equals("bob"), "truong nhom roi -> bob lam truong nhom");

        // ---- Gửi file / ảnh ----
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        a.send(Protocol.SEND_FILE, ab, "C:\\Users\\x\\anh.png", Base64.getEncoder().encodeToString(png), 0);
        Message img = Message.fromFields(b.expect(Protocol.MSG, f -> f[2].equals(ab) && f[5].equals(Message.IMAGE), "bob nhan anh"), 1);
        check(img.fileName.equals("anh.png") && img.fileSize == png.length, "ten file da bo duong dan, dung kich thuoc");
        b.send(Protocol.GET_FILE, img.fileId);
        String[] file = b.expect(Protocol.FILE, f -> f[1].equals(img.fileId), "bob tai anh");
        check(java.util.Arrays.equals(Base64.getDecoder().decode(file[3]), png), "noi dung file dung");
        c.send(Protocol.GET_FILE, img.fileId);
        c.expect(Protocol.FILE_MISSING, f -> f[1].equals(img.fileId), "carol khong tai duoc file cua hoi thoai khac");
        a.send(Protocol.RECALL, img.id);
        b.expect(Protocol.UPDATE, f -> f[1].equals(String.valueOf(img.id)) && f[13].equals("1"), "thu hoi anh");
        b.send(Protocol.GET_FILE, img.fileId);
        b.expect(Protocol.FILE_MISSING, f -> f[1].equals(img.fileId), "anh da thu hoi khong tai lai duoc");
        b.send(Protocol.HISTORY, Protocol.privateConv("alice", "carol"), 0);
        b.expect(Protocol.HISTORY_END, f -> f[1].equals(Protocol.privateConv("alice", "carol")), "khong co quyen van nhan HISTORY_END");

        // ---- Tin nhắn khi offline + số chưa đọc ----
        c.close();
        a.expect(Protocol.USER, f -> f[1].equals("carol") && f[3].equals("0"), "alice thay carol offline");
        String ac = Protocol.privateConv("alice", "carol");
        a.send(Protocol.SEND, ac, "Carol ơi, online thì trả lời nhé", 0);
        a.send(Protocol.SEND, ac, "Tin thứ 2", 0);
        Thread.sleep(200);
        Client c2 = Client.login("carol");
        c2.expect(Protocol.HIST, f -> f[2].equals(ac) && f[6].startsWith("Carol ơi"), "carol nhan tin luc offline");
        c2.expect(Protocol.UNREAD, f -> f[1].equals(ac) && f[2].equals("2"), "carol co 2 tin chua doc");

        // ---- Lịch sử phân trang ----
        for (int i = 0; i < Protocol.HISTORY_PAGE + 5; i++) a.send(Protocol.SEND, ab, "tin " + i, 0);
        String lastText = "tin " + (Protocol.HISTORY_PAGE + 4);
        a.expect(Protocol.MSG, f -> f[2].equals(ab) && f[6].equals(lastText), "server da xu ly het tin gui lien tiep");
        Client b2 = Client.login("bob");
        List<String[]> hist = new ArrayList<>();
        String[] end = b2.collect(Protocol.HIST, f -> f[2].equals(ab), hist, Protocol.HISTORY_END, f -> f[1].equals(ab));
        check(hist.size() == Protocol.HISTORY_PAGE && end[2].equals("1"),
                "trang lich su dau tien + con tin cu (nhan " + hist.size() + " tin, hasMore=" + end[2] + ")");
        b2.send(Protocol.HISTORY, ab, hist.get(0)[1]);
        hist.clear();
        end = b2.collect(Protocol.HIST, f -> f[2].equals(ab), hist, Protocol.HISTORY_END, f -> f[1].equals(ab));
        check(!hist.isEmpty() && end[2].equals("0"), "tai trang lich su cu hon");
        check(hist.stream().noneMatch(f -> f[1].equals(String.valueOf(reply.id))), "tin da xoa o phia toi khong hien lai");

        a.close();
        b.close();
        b2.close();
        c2.close();
    }

    private static void testPersistence() throws Exception {
        ChatServer server2 = startServer();
        try {
            Client a = Client.login("alice");
            a.expect(Protocol.FRIEND, f -> f[1].equals("bob") && f[2].equals(Protocol.FRIEND_YES), "ban be con sau khi khoi dong lai");
            a.expect(Protocol.HIST, f -> f[2].equals(Protocol.CONV_ALL) && f[6].startsWith("Xin chào"), "du lieu con sau khi khoi dong lai server");
            a.close();
            Thread.sleep(300); // chờ server ghi nhận ngắt kết nối rồi mới dừng
        } finally {
            server2.stop();
        }
    }

    // ===================== Tiện ích =====================

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("THAT BAI: " + name);
        passed++;
        System.out.println("  OK  " + name);
    }

    private static void deleteDir(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    static class Client {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;
        final BlockingQueue<String[]> queue = new LinkedBlockingQueue<>();

        Client() throws IOException {
            socket = new Socket("localhost", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        static Client login(String user) throws IOException {
            Client c = new Client();
            // request() tự bật luồng đọc khi nhận AUTH_OK (không bật lần 2: hai luồng đọc làm đảo thứ tự dòng)
            check(c.request(Protocol.LOGIN, user, "1234")[0].equals(Protocol.AUTH_OK), "dang nhap " + user);
            return c;
        }

        String[] request(Object... f) throws IOException {
            send(f);
            String[] r = Protocol.decode(in.readLine());
            if (r[0].equals(Protocol.AUTH_OK)) startReading();
            return r;
        }

        void startReading() {
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) queue.add(Protocol.decode(line));
                } catch (IOException ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void send(Object... f) {
            out.println(Protocol.encode(f));
        }

        String[] expect(String cmd, Predicate<String[]> p, String name) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 3000;
            while (true) {
                String[] f = queue.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (f == null) throw new AssertionError("THAT BAI (het thoi gian): " + name);
                if (f[0].equals(cmd) && p.test(f)) {
                    check(true, name);
                    return f;
                }
            }
        }

        void expectNone(String cmd, Predicate<String[]> p, String name) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 400;
            String[] f;
            while ((f = queue.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS)) != null) {
                if (f[0].equals(cmd) && p.test(f)) throw new AssertionError("THAT BAI: " + name);
            }
            check(true, name);
        }

        /** Gom các dòng khớp p vào list cho tới khi gặp dòng kết thúc. */
        String[] collect(String cmd, Predicate<String[]> p, List<String[]> into, String endCmd, Predicate<String[]> endP)
                throws InterruptedException {
            while (true) {
                String[] f = queue.poll(3, TimeUnit.SECONDS);
                if (f == null) throw new AssertionError("THAT BAI: khong nhan duoc " + endCmd);
                if (f[0].equals(cmd) && p.test(f)) into.add(f);
                else if (f[0].equals(endCmd) && endP.test(f)) return f;
            }
        }

        void close() throws IOException {
            send(Protocol.QUIT);
            socket.close();
        }
    }
}
