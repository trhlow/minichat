package client;

import common.Message;
import common.Protocol;

import java.io.Console;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniChat Client chạy trên cmd (không giao diện).
 *
 * Chạy: java client.ConsoleClient [host] [port]
 * Gõ /help sau khi đăng nhập để xem danh sách lệnh.
 */
public class ConsoleClient {

    private static final String HELP = String.join("\n",
            "  <noi dung>              chat chung",
            "  /w <user> <noi dung>    chat rieng",
            "  /g <maNhom> <noi dung>  chat nhom",
            "  /reply <id> <noi dung>  tra loi tin nhan",
            "  /recall <id>            thu hoi tin nhan cua minh",
            "  /react <id> <1-6>       tha cam xuc (1=tim 2=like 3=haha 4=wow 5=khoc 6=gian)",
            "  /create <ten nhom>      tao nhom",
            "  /join <maNhom>          tham gia nhom    /leave <maNhom>  roi nhom",
            "  /add <maNhom> <u1,u2>   them thanh vien vao nhom",
            "  /groups                 danh sach nhom   /users  danh sach nguoi dung",
            "  /friend <user>          gui loi moi ket ban   /accept <user>  dong y ket ban",
            "  /quit                   thoat");

    private final ChatConnection conn;
    private final Map<String, String[]> users = new ConcurrentHashMap<>();   // user -> [displayName, online]
    private final Map<String, String[]> groups = new ConcurrentHashMap<>();  // gid -> [name, members]
    private final Map<Long, Message> messages = new ConcurrentHashMap<>();
    private String me;

    private ConsoleClient(ChatConnection conn) {
        this.conn = conn;
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        ConsoleClient c = new ConsoleClient(new ChatConnection(host, port));
        c.run(new Scanner(System.in, "UTF-8"));
    }

    private void run(Scanner sc) throws IOException {
        if (!auth(sc)) return;
        System.out.println("Dang nhap thanh cong! Go /help de xem lenh.");
        conn.listen(this::print, () -> {
            System.out.println("Mat ket noi toi server.");
            System.exit(0);
        });
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("/")) {
                conn.send(Protocol.SEND, Protocol.CONV_ALL, line, 0);
                continue;
            }
            String[] p = line.split(" ", 3);
            String a1 = p.length > 1 ? p[1] : "";
            String a2 = p.length > 2 ? p[2] : "";
            switch (p[0]) {
                case "/w": conn.send(Protocol.SEND, Protocol.privateConv(me, Protocol.normalizeUsername(a1)), a2, 0); break;
                case "/g": conn.send(Protocol.SEND, Protocol.groupConv(a1), a2, 0); break;
                case "/reply": {
                    Message m = messages.get(Protocol.parseLong(a1, 0));
                    if (m == null) System.out.println("! Khong tim thay tin nhan #" + a1);
                    else conn.send(Protocol.SEND, m.conv, a2, m.id);
                    break;
                }
                case "/recall": conn.send(Protocol.RECALL, a1); break;
                case "/react": {
                    int i = (int) Protocol.parseLong(a2, 0) - 1;
                    if (i < 0 || i >= Protocol.REACTIONS.length) System.out.println("! Chon cam xuc tu 1 den 6");
                    else conn.send(Protocol.REACT, a1, Protocol.REACTIONS[i]);
                    break;
                }
                case "/create": conn.send(Protocol.GROUP_CREATE, line.substring(7).trim(), ""); break;
                case "/join": conn.send(Protocol.GROUP_JOIN, a1); break;
                case "/leave": conn.send(Protocol.GROUP_LEAVE, a1); break;
                case "/add": conn.send(Protocol.GROUP_ADD, a1, a2); break;
                case "/friend": conn.send(Protocol.FRIEND_REQUEST, a1); break;
                case "/accept": conn.send(Protocol.FRIEND_ACCEPT, a1); break;
                case "/groups":
                    if (groups.isEmpty()) System.out.println("* Chua co nhom nao");
                    new TreeMap<>(groups).forEach((id, g) -> System.out.println("* [" + id + "] " + g[0] + " - thanh vien: " + g[1]));
                    break;
                case "/users":
                    new TreeMap<>(users).forEach((u, x) -> System.out.println("* " + u + " (" + x[0] + ")" + ("1".equals(x[1]) ? " - online" : "")));
                    break;
                case "/quit": conn.close(); return;
                default: System.out.println(HELP);
            }
        }
        conn.close();
    }

    /** Đăng nhập hoặc đăng ký. */
    private boolean auth(Scanner sc) throws IOException {
        while (true) {
            System.out.print("1. Dang nhap   2. Dang ky   > ");
            if (!sc.hasNextLine()) return false;
            boolean reg = sc.nextLine().trim().equals("2");
            String user = ask(sc, "Ten dang nhap: ");
            String pass = askPassword(sc);
            if (user == null || pass == null) return false;
            if (reg) {
                String display = ask(sc, "Ten hien thi: ");
                if (display == null) return false;
                String err = conn.register(user, pass, display);
                if (err != null) {
                    System.out.println("Loi: " + err);
                    continue;
                }
                System.out.println("Dang ky thanh cong!");
            }
            try {
                me = conn.login(user, pass)[0];
                return true;
            } catch (IOException e) {
                System.out.println("Loi: " + e.getMessage());
            }
        }
    }

    private static String ask(Scanner sc, String prompt) {
        System.out.print(prompt);
        return sc.hasNextLine() ? sc.nextLine().trim() : null;
    }

    private static String askPassword(Scanner sc) {
        Console console = System.console();
        if (console != null) {
            char[] p = console.readPassword("Mat khau: ");
            return p == null ? null : new String(p);
        }
        return ask(sc, "Mat khau: ");
    }

    private String name(String user) {
        String[] u = users.get(user);
        return u == null ? user : u[0];
    }

    private String where(String conv) {
        if (Protocol.CONV_ALL.equals(conv)) return "Chung";
        if (Protocol.isGroup(conv)) {
            String[] g = groups.get(Protocol.groupIdOf(conv));
            return "#" + Protocol.groupIdOf(conv) + " " + (g == null ? "" : g[0]);
        }
        return "Rieng voi " + Protocol.peerOf(conv, me);
    }

    private void print(String[] f) {
        switch (f[0]) {
            case Protocol.MSG:
            case Protocol.UPDATE: {
                Message m = Message.fromFields(f, 1);
                messages.put(m.id, m);
                if (Message.SYSTEM.equals(m.type)) {
                    System.out.println("* [" + where(m.conv) + "] " + m.text);
                    break;
                }
                String body = m.recalled ? "(tin nhan da duoc thu hoi)"
                        : m.isFile() ? "[File] " + m.fileName + " (" + Protocol.humanSize(m.fileSize) + ")" : m.text;
                String reply = m.replyTo > 0 ? " (tra loi " + name(m.replyFrom) + ": " + m.replyText + ")" : "";
                String react = m.reactions.isEmpty() ? "" : "  " + String.join("", m.reactions.values());
                System.out.println((Protocol.UPDATE.equals(f[0]) ? "~ " : "") + "#" + m.id + " [" + where(m.conv) + "] "
                        + name(m.from) + reply + ": " + body + react);
                break;
            }
            case Protocol.HIST: {
                Message m = Message.fromFields(f, 1);
                messages.put(m.id, m);
                break;
            }
            case Protocol.USER: {
                String[] old = users.put(f[1], new String[]{f[2], f[3]});
                if (old != null && !old[1].equals(f[3]) && !f[1].equals(me)) {
                    System.out.println("* " + f[2] + ("1".equals(f[3]) ? " vua online" : " da offline"));
                }
                break;
            }
            case Protocol.GROUP: groups.put(f[1], new String[]{f[2], f[4]}); break;
            case Protocol.GROUP_REMOVED: groups.remove(f[1]); break;
            case Protocol.FRIEND:
                if (Protocol.FRIEND_RECEIVED.equals(f[2])) System.out.println("* " + name(f[1]) + " muon ket ban. Go /accept " + f[1]);
                else if (Protocol.FRIEND_YES.equals(f[2])) System.out.println("* Ban va " + name(f[1]) + " da la ban be");
                break;
            case Protocol.UNREAD:
                if (!"0".equals(f[2])) System.out.println("* " + where(f[1]) + ": " + f[2] + " tin chua doc");
                break;
            case Protocol.OPEN: System.out.println("* Da vao " + where(f[1])); break;
            case Protocol.FILE_MISSING: System.out.println("! File khong con tren server"); break;
            case Protocol.INFO: System.out.println("* " + f[1]); break;
            case Protocol.ERROR: System.out.println("! " + f[1]); break;
            default:
                // READ, TYPING, HISTORY_END, FILE...: không hiển thị trên cmd
        }
    }
}
