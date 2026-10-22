package server;

import common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniChat Server.
 * - Luôn sẵn sàng chờ và chấp nhận kết nối từ client.
 * - Quản lí danh sách kết nối (user) và danh sách nhóm.
 * - Phân phối tin nhắn: chat chung, chat nhóm, chat riêng.
 *
 * Chạy: java server.ChatServer [port]   (mặc định 5000)
 */
public class ChatServer {

    public static final int DEFAULT_PORT = 5000;

    /** Danh sách kết nối: username -> handler */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    /** Danh sách nhóm: tên nhóm -> tập thành viên */
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    private final int port;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("MiniChat Server dang chay tai port " + port + " ...");
            while (true) {
                Socket socket = serverSocket.accept();
                log("Ket noi moi tu " + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket, this)).start();
            }
        } catch (IOException e) {
            log("Loi server: " + e.getMessage());
        }
    }

    // ===================== Quản lí user =====================

    /** Đăng ký user mới, trả về false nếu tên đã tồn tại. */
    boolean addClient(String name, ClientHandler handler) {
        if (clients.putIfAbsent(name, handler) != null) return false;
        handler.send(Protocol.LOGIN_OK + " " + name);
        log(name + " da ket noi. Online: " + clients.size());
        broadcastAll(Protocol.INFO + " " + name + " da tham gia MiniChat");
        broadcastUserList();
        sendGroupList(handler);
        return true;
    }

    void removeClient(String name) {
        if (name == null || clients.remove(name) == null) return;
        for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
            if (e.getValue().remove(name)) {
                broadcastGroup(e.getKey(), Protocol.INFO + " " + name + " da roi nhom #" + e.getKey());
            }
        }
        log(name + " da ngat ket noi. Online: " + clients.size());
        broadcastAll(Protocol.INFO + " " + name + " da roi MiniChat");
        broadcastUserList();
        broadcastGroupList();
    }

    // ===================== Quản lí nhóm =====================

    void createGroup(String group, ClientHandler requester) {
        Set<String> members = ConcurrentHashMap.newKeySet();
        if (groups.putIfAbsent(group, members) != null) {
            requester.send(Protocol.ERROR + " Nhom #" + group + " da ton tai");
            return;
        }
        members.add(requester.getName());
        log(requester.getName() + " tao nhom #" + group);
        requester.send(Protocol.JOINED + " " + group);
        requester.send(Protocol.INFO + " Ban da tao va tham gia nhom #" + group);
        broadcastGroupList();
    }

    void joinGroup(String group, ClientHandler requester) {
        Set<String> members = groups.get(group);
        if (members == null) {
            requester.send(Protocol.ERROR + " Nhom #" + group + " khong ton tai");
            return;
        }
        if (!members.add(requester.getName())) {
            requester.send(Protocol.ERROR + " Ban da o trong nhom #" + group);
            return;
        }
        log(requester.getName() + " tham gia nhom #" + group);
        requester.send(Protocol.JOINED + " " + group);
        broadcastGroup(group, Protocol.INFO + " " + requester.getName() + " da tham gia nhom #" + group);
        broadcastGroupList();
    }

    void leaveGroup(String group, ClientHandler requester) {
        Set<String> members = groups.get(group);
        if (members == null || !members.remove(requester.getName())) {
            requester.send(Protocol.ERROR + " Ban khong o trong nhom #" + group);
            return;
        }
        log(requester.getName() + " roi nhom #" + group);
        requester.send(Protocol.LEFT + " " + group);
        broadcastGroup(group, Protocol.INFO + " " + requester.getName() + " da roi nhom #" + group);
        broadcastGroupList();
    }

    // ===================== Gửi tin =====================

    void chatAll(ClientHandler from, String text) {
        broadcastAll(Protocol.ALL + " " + from.getName() + " " + text);
    }

    void chatGroup(ClientHandler from, String group, String text) {
        Set<String> members = groups.get(group);
        if (members == null || !members.contains(from.getName())) {
            from.send(Protocol.ERROR + " Ban phai tham gia nhom #" + group + " truoc khi chat");
            return;
        }
        broadcastGroup(group, Protocol.GROUP + " " + group + " " + from.getName() + " " + text);
    }

    void chatPrivate(ClientHandler from, String to, String text) {
        ClientHandler target = clients.get(to);
        if (target == null) {
            from.send(Protocol.ERROR + " User " + to + " khong online");
            return;
        }
        String msg = Protocol.PRIVATE + " " + from.getName() + " " + to + " " + text;
        target.send(msg);
        if (target != from) from.send(msg);
    }

    void sendGroupList(ClientHandler handler) {
        handler.send(buildGroupList());
    }

    // ===================== Tiện ích =====================

    private void broadcastAll(String msg) {
        for (ClientHandler c : clients.values()) c.send(msg);
    }

    private void broadcastGroup(String group, String msg) {
        Set<String> members = groups.get(group);
        if (members == null) return;
        for (String name : members) {
            ClientHandler c = clients.get(name);
            if (c != null) c.send(msg);
        }
    }

    private void broadcastUserList() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        broadcastAll(Protocol.USERS + " " + String.join(",", names));
    }

    private void broadcastGroupList() {
        broadcastAll(buildGroupList());
    }

    /** GROUPS ten1:soThanhVien,ten2:soThanhVien,... */
    private String buildGroupList() {
        List<String> names = new ArrayList<>(groups.keySet());
        Collections.sort(names);
        List<String> items = new ArrayList<>();
        for (String g : names) items.add(g + ":" + groups.get(g).size());
        return Protocol.GROUPS + " " + String.join(",", items);
    }

    static void log(String msg) {
        System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + msg);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Port khong hop le, dung mac dinh " + DEFAULT_PORT);
            }
        }
        new ChatServer(port).start();
    }
}
