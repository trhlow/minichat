package client;

import common.Protocol;
import server.ChatServer;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Dựng server thật + dữ liệu mẫu rồi vẽ giao diện client ra file PNG (không hiện cửa sổ).
 * Dùng để kiểm tra giao diện trên máy không có người thao tác.
 * Chạy: java -cp out client.GuiSnapshot thuMucAnh
 */
public class GuiSnapshot {

    public static void main(String[] args) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
            }
            System.err.println("Het thoi gian cho");
            System.exit(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            run(args);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        ChatClientGUI.installLookAndFeel();
        File outDir = new File(args.length > 0 ? args[0] : "snapshots");
        outDir.mkdirs();
        Path data = Files.createTempDirectory("minichat-gui");
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        ChatServer server = new ChatServer(port, data);
        Thread st = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        st.setDaemon(true);
        st.start();
        Thread.sleep(400);

        ChatConnection reg = new ChatConnection("localhost", port);
        reg.register("alice", "1234", "Trần Hoàng Long");
        reg.register("bob", "1234", "Nguyễn Thanh Huy");
        reg.register("carol", "1234", "Lê Minh Anh");
        reg.register("dave", "1234", "Phạm Quốc Bảo");
        reg.close();

        ChatConnection bob = login(port, "bob");
        ChatConnection carol = login(port, "carol");
        ChatConnection dave = login(port, "dave");
        ChatConnection aliceConn = new ChatConnection("localhost", port);
        String[] info = aliceConn.login("alice", "1234");

        String ab = Protocol.privateConv("alice", "bob");
        bob.send(Protocol.FRIEND_REQUEST, "alice");
        carol.send(Protocol.FRIEND_REQUEST, "alice");
        dave.send(Protocol.SEND, Protocol.CONV_ALL, "Chào mọi người, mình mới tham gia MiniChat 👋", 0);
        Thread.sleep(150);
        aliceConn.send(Protocol.FRIEND_ACCEPT, "bob");
        bob.send(Protocol.SEND, ab, "Chào Long, chiều nay nhóm mình họp lúc mấy giờ vậy?", 0);
        Thread.sleep(120);
        aliceConn.send(Protocol.SEND, ab, "3 giờ chiều nhé, ở phòng B203 😄", 0);
        Thread.sleep(120);
        bob.send(Protocol.SEND, ab, "Ok, mình sẽ mang slide demo phần socket server. Bạn nhớ chuẩn bị phần giao diện client "
                + "nha, thầy muốn xem cả chức năng gửi file và thu hồi tin nhắn.", 0);
        bob.send(Protocol.SEND_FILE, ab, "giao-dien-demo.png", Base64.getEncoder().encodeToString(sampleImage()), 0);
        bob.send(Protocol.SEND_FILE, ab, "BaoCao_DoAn_MiniChat.pdf", Base64.getEncoder().encodeToString(new byte[245_760]), 0);
        Thread.sleep(250);

        // trả lời + cảm xúc + thu hồi
        ChatClientGUI[] gui = new ChatClientGUI[1];
        SwingUtilities.invokeAndWait(() -> {
            gui[0] = new ChatClientGUI(aliceConn, info[0], info[1]);
            gui[0].addNotify(); // có peer như cửa sổ thật nhưng không hiện lên màn hình
            gui[0].validate();
        });
        Thread.sleep(600);
        Conversation conv = field(gui[0], ab);
        long bobFirst = conv.messages.get(0).id;
        aliceConn.send(Protocol.REACT, conv.messages.get(2).id, "👍");
        bob.send(Protocol.REACT, conv.messages.get(1).id, "❤️");
        aliceConn.send(Protocol.SEND, ab, "Đã nhận file, cảm ơn nhé! Tối nay mình ghép code rồi push lên GitHub.", bobFirst);
        aliceConn.send(Protocol.SEND, ab, "Gửi nhầm 😅", 0);
        Thread.sleep(200);
        aliceConn.send(Protocol.RECALL, field(gui[0], ab).last().id);
        bob.send(Protocol.SEEN, ab, field(gui[0], ab).lastId());

        // nhóm
        aliceConn.send(Protocol.GROUP_CREATE, "Đồ án Lập trình mạng", "bob,carol");
        Thread.sleep(300);
        String gid = gui[0].groupIdByName("Đồ án Lập trình mạng");
        String gconv = Protocol.groupConv(gid);
        carol.send(Protocol.SEND, gconv, "Mình làm phần báo cáo và slide nhé 📄", 0);
        bob.send(Protocol.SEND, gconv, "Ok, server mình xong rồi, đang viết test.", 0);
        Thread.sleep(150);
        carol.send(Protocol.TYPING, gconv, 1);
        bob.send(Protocol.TYPING, ab, 1);
        Thread.sleep(600);

        // ---- ảnh 1: chat riêng ----
        SwingUtilities.invokeAndWait(() -> gui[0].select(field(gui[0], ab)));
        Thread.sleep(700); // chờ tải ảnh
        snap(gui[0], new File(outDir, "1-chat-rieng.png"));

        // ---- ảnh 2: chat nhóm ----
        SwingUtilities.invokeAndWait(() -> gui[0].select(field(gui[0], gconv)));
        Thread.sleep(300);
        snap(gui[0], new File(outDir, "2-chat-nhom.png"));

        // ---- ảnh 3: danh bạ ----
        SwingUtilities.invokeAndWait(() -> gui[0].showSide("contacts"));
        Thread.sleep(200);
        snap(gui[0], new File(outDir, "3-danh-ba.png"));

        // ---- ảnh 4: đăng nhập ----
        LoginFrame[] lf = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> lf[0] = new LoginFrame());
        snap(lf[0], new File(outDir, "4-dang-nhap.png"));

        System.out.println("Da luu anh vao " + outDir.getAbsolutePath());
        server.stop();
        System.exit(0);
    }

    private static ChatConnection login(int port, String user) throws Exception {
        ChatConnection c = new ChatConnection("localhost", port);
        c.login(user, "1234");
        c.listen(f -> { }, () -> { });
        return c;
    }

    private static Conversation field(ChatClientGUI gui, String conv) {
        if (SwingUtilities.isEventDispatchThread()) return gui.conversation(conv);
        Conversation[] r = new Conversation[1];
        try {
            SwingUtilities.invokeAndWait(() -> r[0] = gui.conversation(conv));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r[0];
    }

    private static void snap(JFrame f, File file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (!f.isDisplayable()) f.addNotify();
            f.validate();
            JComponent root = f.getRootPane();
            // vẽ theo đúng tỉ lệ DPI của màn hình (Swing đo chữ theo tỉ lệ này)
            java.awt.geom.AffineTransform tx = f.getGraphicsConfiguration().getDefaultTransform();
            BufferedImage img = new BufferedImage((int) Math.ceil(root.getWidth() * tx.getScaleX()),
                    (int) Math.ceil(root.getHeight() * tx.getScaleY()), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.transform(tx);
            Object hints = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (hints instanceof java.util.Map) g.addRenderingHints((java.util.Map<?, ?>) hints);
            root.paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static byte[] sampleImage() throws Exception {
        BufferedImage img = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Theme.hq(g);
        g.setPaint(new GradientPaint(0, 0, new Color(0x0068FF), 640, 400, new Color(0x7B5CFF)));
        g.fillRect(0, 0, 640, 400);
        g.setColor(Color.WHITE);
        g.setFont(Theme.font(Font.BOLD, 56));
        g.drawString("MiniChat", 180, 210);
        g.setFont(Theme.font(Font.PLAIN, 24));
        g.drawString("Java Socket · Swing", 210, 260);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
