package client;

import common.Protocol;

import java.io.IOException;
import java.util.Scanner;

/**
 * MiniChat Client chạy trên cmd (không giao diện).
 *
 * Chạy: java client.ConsoleClient [host] [port]
 *
 * Lệnh:
 *   /create <nhom>          tạo nhóm
 *   /join <nhom>            vào nhóm
 *   /leave <nhom>           rời nhóm
 *   /g <nhom> <noi dung>    chat nhóm
 *   /w <user> <noi dung>    chat riêng
 *   /quit                   thoát
 *   <noi dung>              chat chung
 */
public class ConsoleClient {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        ChatConnection conn = new ChatConnection(host, port);
        Scanner sc = new Scanner(System.in, "UTF-8");

        while (true) {
            System.out.print("Nhap ten: ");
            if (!sc.hasNextLine()) return;
            String err = conn.login(sc.nextLine().trim());
            if (err == null) break;
            System.out.println("Loi: " + err);
        }
        System.out.println("Dang nhap thanh cong! Go /help de xem lenh.");

        conn.listen(ConsoleClient::print, () -> {
            System.out.println("Mat ket noi toi server.");
            System.exit(0);
        });

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("/")) {
                conn.send(Protocol.MSG_ALL + " " + line);
                continue;
            }
            String[] p = line.split(" ", 2);
            String arg = p.length > 1 ? p[1] : "";
            switch (p[0]) {
                case "/create": conn.send(Protocol.CREATE + " " + arg); break;
                case "/join":   conn.send(Protocol.JOIN + " " + arg); break;
                case "/leave":  conn.send(Protocol.LEAVE + " " + arg); break;
                case "/g":      conn.send(Protocol.MSG_GROUP + " " + arg); break;
                case "/w":      conn.send(Protocol.MSG_PRIVATE + " " + arg); break;
                case "/quit":   conn.close(); return;
                default:
                    System.out.println("/create <nhom> | /join <nhom> | /leave <nhom> | /g <nhom> <msg> | /w <user> <msg> | /quit");
            }
        }
        conn.close();
    }

    private static void print(String line) {
        String[] p = line.split(" ", 2);
        String rest = p.length > 1 ? p[1] : "";
        switch (p[0]) {
            case Protocol.ALL: {
                String[] q = rest.split(" ", 2);
                System.out.println("[Chung] " + q[0] + ": " + (q.length > 1 ? q[1] : ""));
                break;
            }
            case Protocol.GROUP: {
                String[] q = rest.split(" ", 3);
                System.out.println("[#" + q[0] + "] " + q[1] + ": " + (q.length > 2 ? q[2] : ""));
                break;
            }
            case Protocol.PRIVATE: {
                String[] q = rest.split(" ", 3);
                System.out.println("[Rieng " + q[0] + " -> " + q[1] + "] " + (q.length > 2 ? q[2] : ""));
                break;
            }
            case Protocol.USERS:  System.out.println("* Online: " + rest); break;
            case Protocol.GROUPS: System.out.println("* Cac nhom: " + (rest.isEmpty() ? "(chua co)" : rest)); break;
            case Protocol.JOINED: System.out.println("* Da vao nhom #" + rest); break;
            case Protocol.LEFT:   System.out.println("* Da roi nhom #" + rest); break;
            case Protocol.INFO:   System.out.println("* " + rest); break;
            case Protocol.ERROR:  System.out.println("! " + rest); break;
            default: System.out.println(line);
        }
    }
}
