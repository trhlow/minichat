package server;

import common.Message;
import common.Protocol;
import server.DataStore.Account;
import server.DataStore.Group;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MiniChat Server.
 * - Luôn sẵn sàng chờ và chấp nhận kết nối từ client (mỗi client 1 ClientHandler).
 * - Quản lí tài khoản, trạng thái online, bạn bè, nhóm và lịch sử tin nhắn.
 * - Phân phối tin nhắn: chat chung, chat nhóm, chat riêng; thu hồi, cảm xúc, đã xem, đang soạn tin...
 * - Lưu dữ liệu xuống thư mục data/ để khởi động lại không bị mất.
 *
 * Mọi thao tác trên dữ liệu đều chạy trong synchronized(this) nên an toàn đa luồng.
 *
 * Chạy: java server.ChatServer [port] [thuMucDuLieu]   (mặc định 5000, data)
 */
public class ChatServer {

    private final int port;
    private final Path dbFile;
    private final Path filesDir;
    private final DataStore db;
    /** user -> các phiên đang đăng nhập (một tài khoản có thể đăng nhập trên nhiều máy) */
    private final Map<String, Set<ClientHandler>> online = new HashMap<>();
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "saver");
        t.setDaemon(true);
        return t;
    });
    private boolean dirty;
    private volatile ServerSocket serverSocket;

    public ChatServer(int port, Path dataDir) {
        this.port = port;
        this.dbFile = dataDir.resolve("minichat.db");
        this.filesDir = dataDir.resolve("files");
        this.db = DataStore.load(dbFile);
    }

    public void start() throws IOException {
        saver.scheduleWithFixedDelay(this::saveIfDirty, 2, 2, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveIfDirty));
        try (ServerSocket ss = new ServerSocket(port)) {
            serverSocket = ss;
            log("MiniChat Server dang chay tai port " + port + " (" + db.accounts.size() + " tai khoan)");
            while (!ss.isClosed()) {
                Socket socket;
                try {
                    socket = ss.accept();
                } catch (IOException e) {
                    if (ss.isClosed()) break;
                    throw e;
                }
                log("Ket noi moi tu " + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket, this), "client-" + socket.getPort()).start();
            }
        } finally {
            saveIfDirty();
        }
    }

    /** Dừng server (dùng cho kiểm thử). */
    public void stop() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        saveIfDirty();
    }

    private synchronized void saveIfDirty() {
        if (!dirty) return;
        try {
            db.save(dbFile);
            dirty = false;
        } catch (IOException e) {
            log("Loi luu du lieu: " + e);
        }
    }

    // ===================== Điều phối lệnh =====================

    /**
     * Xử lý 1 lệnh. Gửi / tải file đọc ghi đĩa nên chạy phần nặng ngoài khoá
     * để 1 người gửi file lớn không làm treo cả phòng chat.
     */
    void handle(ClientHandler c, String[] f) {
        if (c.getUser() != null && Protocol.SEND_FILE.equals(f[0])) {
            sendFile(c, arg(f, 1), arg(f, 2), arg(f, 3), num(f, 4));
        } else if (c.getUser() != null && Protocol.GET_FILE.equals(f[0])) {
            getFile(c, arg(f, 1));
        } else {
            handleLocked(c, f);
        }
    }

    private synchronized void handleLocked(ClientHandler c, String[] f) {
        String cmd = f[0];
        if (c.getUser() == null) {
            if (Protocol.REGISTER.equals(cmd)) register(c, arg(f, 1), arg(f, 2), arg(f, 3));
            else if (Protocol.LOGIN.equals(cmd)) login(c, arg(f, 1), arg(f, 2));
            else c.send(Protocol.AUTH_FAIL, "Hãy đăng nhập trước");
            return;
        }
        String me = c.getUser();
        switch (cmd) {
            case Protocol.SEND: sendText(c, arg(f, 1), arg(f, 2), num(f, 3)); break;
            case Protocol.FORWARD: forward(c, num(f, 1), arg(f, 2)); break;
            case Protocol.RECALL: recall(c, num(f, 1)); break;
            case Protocol.REACT: react(c, num(f, 1), arg(f, 2)); break;
            case Protocol.DELETE_MSG: deleteForMe(c, num(f, 1)); break;
            case Protocol.SEEN: seen(c, arg(f, 1), num(f, 2)); break;
            case Protocol.TYPING: typing(c, arg(f, 1), "1".equals(arg(f, 2))); break;
            case Protocol.HISTORY:
                if (canAccess(me, arg(f, 1))) sendHistory(c, arg(f, 1), num(f, 2));
                else c.send(Protocol.HISTORY_END, arg(f, 1), 0); // để client không chờ mãi
                break;
            case Protocol.PROFILE: profile(c, arg(f, 1)); break;
            case Protocol.FRIEND_REQUEST: friendRequest(c, Protocol.normalizeUsername(arg(f, 1))); break;
            case Protocol.FRIEND_ACCEPT: friendAccept(c, Protocol.normalizeUsername(arg(f, 1))); break;
            case Protocol.FRIEND_DECLINE: friendDecline(me, Protocol.normalizeUsername(arg(f, 1))); break;
            case Protocol.FRIEND_REMOVE: friendRemove(me, Protocol.normalizeUsername(arg(f, 1))); break;
            case Protocol.GROUP_CREATE: groupCreate(c, arg(f, 1), arg(f, 2)); break;
            case Protocol.GROUP_JOIN: groupJoin(c, arg(f, 1)); break;
            case Protocol.GROUP_ADD: groupAdd(c, arg(f, 1), arg(f, 2)); break;
            case Protocol.GROUP_KICK: groupKick(c, arg(f, 1), Protocol.normalizeUsername(arg(f, 2))); break;
            case Protocol.GROUP_LEAVE: groupLeave(c, arg(f, 1)); break;
            case Protocol.GROUP_RENAME: groupRename(c, arg(f, 1), arg(f, 2)); break;
            case Protocol.GROUP_DISBAND: groupDisband(c, arg(f, 1)); break;
            default: c.send(Protocol.ERROR, "Lệnh không hợp lệ: " + cmd);
        }
    }

    private static String arg(String[] f, int i) {
        return i < f.length ? f[i] : "";
    }

    private static long num(String[] f, int i) {
        return Protocol.parseLong(arg(f, i), 0);
    }

    // ===================== Tài khoản & phiên =====================

    private void register(ClientHandler c, String username, String pass, String displayName) {
        username = Protocol.normalizeUsername(username);
        displayName = displayName.trim();
        if (!Protocol.isValidUsername(username)) {
            c.send(Protocol.AUTH_FAIL, "Tên đăng nhập 3-20 ký tự: chữ thường không dấu, số, dấu _ . -");
        } else if (!Protocol.isValidPassword(pass)) {
            c.send(Protocol.AUTH_FAIL, "Mật khẩu phải từ 4 đến 64 ký tự");
        } else if (!Protocol.isValidDisplayName(displayName)) {
            c.send(Protocol.AUTH_FAIL, "Tên hiển thị không hợp lệ (1-40 ký tự)");
        } else if (db.accounts.containsKey(username)) {
            c.send(Protocol.AUTH_FAIL, "Tên đăng nhập '" + username + "' đã được sử dụng");
        } else {
            Account a = new Account();
            a.username = username;
            a.displayName = displayName;
            a.salt = DataStore.newSalt();
            a.passHash = DataStore.hash(a.salt, pass);
            db.accounts.put(username, a);
            // tài khoản mới không bị tính các tin cũ của phòng chat chung là chưa đọc
            db.reads.computeIfAbsent(Protocol.CONV_ALL, k -> new HashMap<>()).put(username, db.nextMessageId - 1);
            dirty = true;
            log("Dang ky tai khoan moi: " + username);
            c.send(Protocol.REGISTER_OK, username);
            broadcastAll(userLine(a));
        }
    }

    private void login(ClientHandler c, String username, String pass) {
        username = Protocol.normalizeUsername(username);
        Account a = db.accounts.get(username);
        if (a == null || !MessageDigest.isEqual(
                DataStore.hash(a.salt, pass).getBytes(StandardCharsets.UTF_8),
                a.passHash.getBytes(StandardCharsets.UTF_8))) {
            c.send(Protocol.AUTH_FAIL, "Sai tên đăng nhập hoặc mật khẩu");
            return;
        }
        c.setUser(username);
        boolean firstSession = !online.containsKey(username);
        online.computeIfAbsent(username, k -> new LinkedHashSet<>()).add(c);
        log(username + " dang nhap. Online: " + online.size());

        c.send(Protocol.AUTH_OK, username, a.displayName);
        for (Account x : db.accounts.values()) c.send(userLine(x));
        for (String other : relatedUsers(username)) c.send(Protocol.FRIEND, other, friendStatus(username, other));
        for (Group g : db.groups.values()) c.send(groupLine(g));
        for (String conv : convsOf(username)) sendBootstrap(c, conv);
        if (firstSession) broadcastAll(userLine(a));
    }

    synchronized void disconnect(ClientHandler c) {
        String user = c.getUser();
        if (user == null) return;
        Set<ClientHandler> sessions = online.get(user);
        if (sessions == null || !sessions.remove(c)) return;
        if (sessions.isEmpty()) {
            online.remove(user);
            Account a = db.accounts.get(user);
            a.lastSeen = System.currentTimeMillis();
            dirty = true;
            broadcastAll(userLine(a));
            // tắt trạng thái "đang soạn tin" ở các hội thoại
            for (String conv : convsOf(user)) {
                for (String u : participants(conv)) if (!u.equals(user)) toUser(u, Protocol.encode(Protocol.TYPING, conv, user, 0));
            }
        }
        log(user + " ngat ket noi. Online: " + online.size());
    }

    private void profile(ClientHandler c, String displayName) {
        if (!Protocol.isValidDisplayName(displayName)) {
            c.send(Protocol.ERROR, "Tên hiển thị không hợp lệ (1-40 ký tự)");
            return;
        }
        Account a = db.accounts.get(c.getUser());
        a.displayName = displayName.trim();
        dirty = true;
        broadcastAll(userLine(a));
    }

    private String userLine(Account a) {
        return Protocol.encode(Protocol.USER, a.username, a.displayName, online.containsKey(a.username) ? 1 : 0, a.lastSeen);
    }

    private String display(String user) {
        Account a = db.accounts.get(user);
        return a == null ? user : a.displayName;
    }

    // ===================== Hội thoại =====================

    /** Các hội thoại mà user tham gia. */
    private List<String> convsOf(String user) {
        List<String> res = new ArrayList<>();
        res.add(Protocol.CONV_ALL);
        for (Group g : db.groups.values()) if (g.members.contains(user)) res.add(Protocol.groupConv(g.id));
        for (Map.Entry<String, List<Message>> e : db.messages.entrySet()) {
            String conv = e.getKey();
            if (Protocol.isPrivate(conv) && !e.getValue().isEmpty()
                    && List.of(Protocol.membersOfPrivate(conv)).contains(user)) res.add(conv);
        }
        return res;
    }

    private boolean canAccess(String user, String conv) {
        if (Protocol.CONV_ALL.equals(conv)) return true;
        if (Protocol.isGroup(conv)) {
            Group g = db.groups.get(Protocol.groupIdOf(conv));
            return g != null && g.members.contains(user);
        }
        if (Protocol.isPrivate(conv)) {
            String[] m = Protocol.membersOfPrivate(conv);
            return !m[0].equals(m[1]) && (m[0].equals(user) || m[1].equals(user))
                    && db.accounts.containsKey(m[0]) && db.accounts.containsKey(m[1])
                    && conv.equals(Protocol.privateConv(m[0], m[1]));
        }
        return false;
    }

    /** Những user nhận tin của hội thoại. */
    private Collection<String> participants(String conv) {
        if (Protocol.CONV_ALL.equals(conv)) return new ArrayList<>(online.keySet());
        if (Protocol.isGroup(conv)) {
            Group g = db.groups.get(Protocol.groupIdOf(conv));
            return g == null ? Collections.emptyList() : new ArrayList<>(g.members);
        }
        if (Protocol.isPrivate(conv)) return List.of(Protocol.membersOfPrivate(conv));
        return Collections.emptyList();
    }

    /** Gửi lịch sử gần nhất + trạng thái đã xem + số tin chưa đọc của 1 hội thoại. */
    private void sendBootstrap(ClientHandler c, String conv) {
        String me = c.getUser();
        sendHistory(c, conv, 0);
        if (!Protocol.CONV_ALL.equals(conv)) {
            Map<String, Long> r = db.reads.get(conv);
            if (r != null) for (Map.Entry<String, Long> e : r.entrySet()) {
                if (!e.getKey().equals(me)) c.send(Protocol.READ, conv, e.getKey(), e.getValue());
            }
        }
        c.send(Protocol.UNREAD, conv, unread(me, conv));
    }

    /** Gửi tối đa HISTORY_PAGE tin có id nhỏ hơn before (before = 0: tin mới nhất). */
    private void sendHistory(ClientHandler c, String conv, long before) {
        String me = c.getUser();
        List<Message> list = db.messages.getOrDefault(conv, Collections.emptyList());
        List<Message> page = new ArrayList<>();
        boolean more = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            Message m = list.get(i);
            if ((before > 0 && m.id >= before) || m.hiddenFor.contains(me)) continue;
            if (page.size() == Protocol.HISTORY_PAGE) {
                more = true;
                break;
            }
            page.add(m);
        }
        Collections.reverse(page);
        for (Message m : page) c.send(msgLine(Protocol.HIST, m));
        c.send(Protocol.HISTORY_END, conv, more ? 1 : 0);
    }

    private int unread(String user, String conv) {
        long last = db.reads.getOrDefault(conv, Collections.emptyMap()).getOrDefault(user, 0L);
        int n = 0;
        for (Message m : db.messages.getOrDefault(conv, Collections.emptyList())) {
            if (m.id > last && !user.equals(m.from) && !Message.SYSTEM.equals(m.type) && !m.hiddenFor.contains(user)) n++;
        }
        return n;
    }

    private Message newMessage(String conv, String from, String type) {
        Message m = new Message();
        m.id = db.nextMessageId++;
        m.conv = conv;
        m.from = from;
        m.time = System.currentTimeMillis();
        m.type = type;
        return m;
    }

    /** Lưu tin và gửi tới mọi người trong hội thoại. */
    private void store(Message m) {
        db.conv(m.conv).add(m);
        db.index(m);
        if (!Message.SYSTEM.equals(m.type)) {
            db.reads.computeIfAbsent(m.conv, k -> new HashMap<>()).put(m.from, m.id);
        }
        dirty = true;
        deliver(m.conv, msgLine(Protocol.MSG, m));
    }

    private void applyReply(Message m, long replyTo) {
        Message r = replyTo > 0 ? db.byId.get(replyTo) : null;
        if (r != null && r.conv.equals(m.conv) && !Message.SYSTEM.equals(r.type)) {
            m.replyTo = r.id;
            m.replyFrom = r.from;
            m.replyText = r.snippet();
        }
    }

    private void sendText(ClientHandler c, String conv, String text, long replyTo) {
        String me = c.getUser();
        if (!canAccess(me, conv)) {
            c.send(Protocol.ERROR, "Bạn không thể gửi tin vào hội thoại này");
            return;
        }
        if (text.trim().isEmpty()) return;
        if (text.length() > 5000) {
            c.send(Protocol.ERROR, "Tin nhắn quá dài (tối đa 5000 ký tự)");
            return;
        }
        Message m = newMessage(conv, me, Message.TEXT);
        m.text = text;
        applyReply(m, replyTo);
        store(m);
    }

    /** Chạy ngoài khoá: giải mã + ghi đĩa trước, chỉ khoá khi lưu tin nhắn. */
    private void sendFile(ClientHandler c, String conv, String fileName, String base64, long replyTo) {
        String me = c.getUser();
        synchronized (this) {
            if (!canAccess(me, conv)) {
                c.send(Protocol.ERROR, "Bạn không thể gửi file vào hội thoại này");
                return;
            }
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            c.send(Protocol.ERROR, "Dữ liệu file không hợp lệ");
            return;
        }
        if (data.length == 0 || data.length > Protocol.MAX_FILE_SIZE) {
            c.send(Protocol.ERROR, "File phải nhỏ hơn " + Protocol.humanSize(Protocol.MAX_FILE_SIZE));
            return;
        }
        String clean = fileName.replace('\\', '/').replaceAll("\\p{Cntrl}", "");
        String name = clean.substring(clean.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "file";
        if (name.length() > 100) name = name.substring(name.length() - 100);
        String fileId = DataStore.newFileId();
        try {
            Files.createDirectories(filesDir);
            Files.write(filesDir.resolve(fileId), data);
        } catch (IOException e) {
            c.send(Protocol.ERROR, "Server không lưu được file: " + e.getMessage());
            return;
        }
        synchronized (this) {
            if (!canAccess(me, conv)) { // có thể vừa bị mời ra khỏi nhóm trong lúc ghi file
                deleteQuietly(fileId);
                c.send(Protocol.ERROR, "Bạn không thể gửi file vào hội thoại này");
                return;
            }
            db.fileNames.put(fileId, name);
            Message m = newMessage(conv, me, Protocol.isImageFile(name) ? Message.IMAGE : Message.FILE);
            m.fileId = fileId;
            m.fileName = name;
            m.fileSize = data.length;
            applyReply(m, replyTo);
            store(m);
        }
        log(me + " gui file " + name + " (" + Protocol.humanSize(data.length) + ") vao " + conv);
    }

    /**
     * Hội thoại conv không còn tin nào dùng file này nữa: bỏ quyền tải qua conv đó,
     * và xoá hẳn file khỏi đĩa nếu không còn hội thoại nào dùng (file chuyển tiếp dùng chung id).
     */
    private void releaseFile(String fileId, String conv) {
        if (fileId == null || fileId.isEmpty()) return;
        for (Message x : db.messages.getOrDefault(conv, Collections.emptyList())) {
            if (fileId.equals(x.fileId)) return;
        }
        Set<String> convs = db.fileConvs.get(fileId);
        if (convs != null) convs.remove(conv);
        if (convs == null || convs.isEmpty()) {
            db.fileConvs.remove(fileId);
            db.fileNames.remove(fileId);
            deleteQuietly(fileId);
        }
    }

    private void deleteQuietly(String fileId) {
        try {
            Files.deleteIfExists(filesDir.resolve(fileId));
        } catch (IOException e) {
            log("Khong xoa duoc file " + fileId + ": " + e);
        }
    }

    private void forward(ClientHandler c, long msgId, String target) {
        String me = c.getUser();
        Message src = db.byId.get(msgId);
        if (src == null || !canAccess(me, src.conv) || src.recalled || Message.SYSTEM.equals(src.type)) {
            c.send(Protocol.ERROR, "Không thể chuyển tiếp tin nhắn này");
            return;
        }
        if (!canAccess(me, target)) {
            c.send(Protocol.ERROR, "Bạn không thể gửi tin vào hội thoại này");
            return;
        }
        Message m = newMessage(target, me, src.type);
        m.text = src.text;
        m.fileId = src.fileId;
        m.fileName = src.fileName;
        m.fileSize = src.fileSize;
        m.forwarded = true;
        store(m);
    }

    private Message editable(ClientHandler c, long msgId) {
        Message m = db.byId.get(msgId);
        if (m == null || !canAccess(c.getUser(), m.conv) || Message.SYSTEM.equals(m.type)) {
            c.send(Protocol.ERROR, "Tin nhắn không tồn tại");
            return null;
        }
        return m;
    }

    private void recall(ClientHandler c, long msgId) {
        Message m = editable(c, msgId);
        if (m == null || m.recalled) return;
        if (!m.from.equals(c.getUser())) {
            c.send(Protocol.ERROR, "Bạn chỉ có thể thu hồi tin nhắn của mình");
            return;
        }
        if (System.currentTimeMillis() - m.time > Protocol.RECALL_WINDOW_MS) {
            c.send(Protocol.ERROR, "Chỉ có thể thu hồi tin nhắn trong vòng 24 giờ");
            return;
        }
        String fileId = m.fileId;
        m.recalled = true;
        m.text = "";
        m.fileId = "";
        m.fileName = "";
        m.fileSize = 0;
        m.reactions.clear();
        releaseFile(fileId, m.conv);
        dirty = true;
        deliver(m.conv, msgLine(Protocol.UPDATE, m));
        // các tin trả lời không còn trích dẫn nội dung đã thu hồi
        for (Message x : db.messages.getOrDefault(m.conv, Collections.emptyList())) {
            if (x.replyTo == m.id) {
                x.replyText = m.snippet();
                deliver(x.conv, msgLine(Protocol.UPDATE, x));
            }
        }
    }

    private void react(ClientHandler c, long msgId, String emoji) {
        Message m = editable(c, msgId);
        if (m == null || m.recalled) return;
        String me = c.getUser();
        if (emoji.isEmpty() || emoji.equals(m.reactions.get(me))) {
            if (m.reactions.remove(me) == null) return;
        } else if (Protocol.isReaction(emoji)) {
            m.reactions.remove(me);
            m.reactions.put(me, emoji);
        } else {
            c.send(Protocol.ERROR, "Cảm xúc không hợp lệ");
            return;
        }
        dirty = true;
        deliver(m.conv, msgLine(Protocol.UPDATE, m));
    }

    private void deleteForMe(ClientHandler c, long msgId) {
        Message m = editable(c, msgId);
        if (m == null) return;
        m.hiddenFor.add(c.getUser());
        dirty = true;
        toUser(c.getUser(), Protocol.encode(Protocol.DELETED, m.id));
    }

    private void seen(ClientHandler c, String conv, long msgId) {
        String me = c.getUser();
        if (!canAccess(me, conv) || msgId <= 0) return;
        List<Message> list = db.messages.getOrDefault(conv, Collections.emptyList());
        if (list.isEmpty()) return;
        msgId = Math.min(msgId, list.get(list.size() - 1).id);
        Map<String, Long> r = db.reads.computeIfAbsent(conv, k -> new HashMap<>());
        if (r.getOrDefault(me, 0L) >= msgId) return;
        r.put(me, msgId);
        dirty = true;
        String line = Protocol.encode(Protocol.READ, conv, me, msgId);
        if (Protocol.CONV_ALL.equals(conv)) toUser(me, line);
        else deliver(conv, line);
    }

    private void typing(ClientHandler c, String conv, boolean on) {
        String me = c.getUser();
        if (!canAccess(me, conv)) return;
        String line = Protocol.encode(Protocol.TYPING, conv, me, on ? 1 : 0);
        for (String u : participants(conv)) if (!u.equals(me)) toUser(u, line);
    }

    /** Kiểm tra quyền trong khoá, đọc đĩa + mã hoá Base64 ngoài khoá. */
    private void getFile(ClientHandler c, String fileId) {
        String me = c.getUser();
        String name;
        synchronized (this) {
            Set<String> convs = db.fileConvs.get(fileId);
            boolean ok = false;
            if (convs != null) for (String conv : convs) ok |= canAccess(me, conv);
            if (!ok) {
                c.send(Protocol.FILE_MISSING, fileId);
                return;
            }
            name = db.fileNames.getOrDefault(fileId, "file");
        }
        try {
            byte[] data = Files.readAllBytes(filesDir.resolve(fileId));
            c.send(Protocol.FILE, fileId, name, Base64.getEncoder().encodeToString(data));
        } catch (IOException e) {
            c.send(Protocol.FILE_MISSING, fileId);
        }
    }

    private static String msgLine(String cmd, Message m) {
        Object[] fields = m.toFields();
        Object[] all = new Object[fields.length + 1];
        all[0] = cmd;
        System.arraycopy(fields, 0, all, 1, fields.length);
        return Protocol.encode(all);
    }

    // ===================== Bạn bè =====================

    private String friendStatus(String a, String b) {
        if (db.friendsOf(a).contains(b)) return Protocol.FRIEND_YES;
        if (db.requests.contains(a + ">" + b)) return Protocol.FRIEND_SENT;
        if (db.requests.contains(b + ">" + a)) return Protocol.FRIEND_RECEIVED;
        return Protocol.FRIEND_NONE;
    }

    private Set<String> relatedUsers(String user) {
        Set<String> res = new LinkedHashSet<>(db.friendsOf(user));
        for (String r : db.requests) {
            String[] p = r.split(">");
            if (p[0].equals(user)) res.add(p[1]);
            if (p[1].equals(user)) res.add(p[0]);
        }
        return res;
    }

    private void notifyFriendStatus(String a, String b) {
        toUser(a, Protocol.encode(Protocol.FRIEND, b, friendStatus(a, b)));
        toUser(b, Protocol.encode(Protocol.FRIEND, a, friendStatus(b, a)));
    }

    private void friendRequest(ClientHandler c, String target) {
        String me = c.getUser();
        if (!db.accounts.containsKey(target) || target.equals(me)) {
            c.send(Protocol.ERROR, "Không tìm thấy người dùng " + target);
            return;
        }
        if (db.friendsOf(me).contains(target)) {
            c.send(Protocol.ERROR, display(target) + " đã là bạn bè của bạn");
            return;
        }
        if (db.requests.contains(target + ">" + me)) {
            friendAccept(c, target);
            return;
        }
        if (!db.requests.add(me + ">" + target)) return;
        dirty = true;
        notifyFriendStatus(me, target);
        toUser(target, Protocol.encode(Protocol.INFO, display(me) + " đã gửi lời mời kết bạn"));
    }

    private void friendAccept(ClientHandler c, String from) {
        String me = c.getUser();
        if (!db.requests.remove(from + ">" + me)) {
            c.send(Protocol.ERROR, "Không có lời mời kết bạn từ " + from);
            return;
        }
        db.requests.remove(me + ">" + from);
        db.friendsOf(me).add(from);
        db.friendsOf(from).add(me);
        dirty = true;
        notifyFriendStatus(me, from);
        toUser(from, Protocol.encode(Protocol.INFO, display(me) + " đã đồng ý kết bạn"));
    }

    /** Từ chối lời mời nhận được, hoặc huỷ lời mời mình đã gửi. */
    private void friendDecline(String me, String other) {
        boolean a = db.requests.remove(other + ">" + me);
        boolean b = db.requests.remove(me + ">" + other);
        if (!a && !b) return;
        dirty = true;
        notifyFriendStatus(me, other);
    }

    private void friendRemove(String me, String other) {
        boolean changed = db.friendsOf(me).remove(other) | db.friendsOf(other).remove(me);
        changed |= db.requests.remove(me + ">" + other) | db.requests.remove(other + ">" + me);
        if (!changed) return;
        dirty = true;
        notifyFriendStatus(me, other);
    }

    // ===================== Nhóm =====================

    private String groupLine(Group g) {
        return Protocol.encode(Protocol.GROUP, g.id, g.name, g.owner, String.join(",", g.members));
    }

    private Group memberGroup(ClientHandler c, String gid) {
        Group g = db.groups.get(gid);
        if (g == null || !g.members.contains(c.getUser())) {
            c.send(Protocol.ERROR, "Bạn không ở trong nhóm này");
            return null;
        }
        return g;
    }

    private Message systemMessage(Group g, String actor, String text) {
        Message m = newMessage(Protocol.groupConv(g.id), actor, Message.SYSTEM);
        m.text = text;
        return m;
    }

    /** Sau khi đổi thành viên: cập nhật danh sách nhóm, gửi thông báo, gửi lịch sử cho thành viên mới. */
    private void membershipChanged(Group g, Message sys, Collection<String> newMembers) {
        // thành viên mới không bị tính các tin cũ trước khi vào nhóm là chưa đọc
        List<Message> old = db.messages.getOrDefault(Protocol.groupConv(g.id), Collections.emptyList());
        if (!old.isEmpty()) {
            Map<String, Long> r = db.reads.computeIfAbsent(Protocol.groupConv(g.id), k -> new HashMap<>());
            for (String u : newMembers) r.put(u, old.get(old.size() - 1).id);
        }
        broadcastAll(groupLine(g));
        if (sys != null) store(sys);
        String conv = Protocol.groupConv(g.id);
        for (String u : newMembers) {
            for (ClientHandler s : online.getOrDefault(u, Collections.emptySet())) sendBootstrap(s, conv);
        }
    }

    private List<String> parseUsers(String csv, Group g) {
        List<String> res = new ArrayList<>();
        for (String u : csv.split(",")) {
            u = Protocol.normalizeUsername(u);
            if (db.accounts.containsKey(u) && (g == null || !g.members.contains(u)) && !res.contains(u)) res.add(u);
        }
        return res;
    }

    private String names(List<String> users) {
        List<String> res = new ArrayList<>();
        for (String u : users) res.add(display(u));
        return String.join(", ", res);
    }

    private void groupCreate(ClientHandler c, String name, String membersCsv) {
        String me = c.getUser();
        name = name.trim();
        if (!Protocol.isValidDisplayName(name)) {
            c.send(Protocol.ERROR, "Tên nhóm không hợp lệ (1-40 ký tự)");
            return;
        }
        Group g = new Group();
        g.id = String.valueOf(db.nextGroupId++);
        g.name = name;
        g.owner = me;
        g.members.add(me);
        List<String> added = parseUsers(membersCsv, g);
        g.members.addAll(added);
        db.groups.put(g.id, g);
        log(me + " tao nhom #" + g.id + " " + name);
        String text = display(me) + " đã tạo nhóm “" + name + "”"
                + (added.isEmpty() ? "" : " và thêm " + names(added));
        // tạo tin hệ thống trước rồi gửi lịch sử cho toàn bộ thành viên (đều là thành viên mới)
        Message sys = systemMessage(g, me, text);
        db.conv(sys.conv).add(sys);
        db.index(sys);
        dirty = true;
        membershipChanged(g, null, g.members);
        c.send(Protocol.OPEN, sys.conv);
    }

    private void groupJoin(ClientHandler c, String gid) {
        String me = c.getUser();
        Group g = db.groups.get(gid);
        if (g == null) {
            c.send(Protocol.ERROR, "Nhóm không tồn tại");
            return;
        }
        if (g.members.contains(me)) {
            c.send(Protocol.OPEN, Protocol.groupConv(gid));
            return;
        }
        g.members.add(me);
        membershipChanged(g, systemMessage(g, me, display(me) + " đã tham gia nhóm"), List.of(me));
        c.send(Protocol.OPEN, Protocol.groupConv(gid));
    }

    private void groupAdd(ClientHandler c, String gid, String csv) {
        Group g = memberGroup(c, gid);
        if (g == null) return;
        List<String> added = parseUsers(csv, g);
        if (added.isEmpty()) {
            c.send(Protocol.ERROR, "Không có thành viên mới hợp lệ để thêm");
            return;
        }
        g.members.addAll(added);
        membershipChanged(g, systemMessage(g, c.getUser(), display(c.getUser()) + " đã thêm " + names(added) + " vào nhóm"), added);
    }

    private void groupKick(ClientHandler c, String gid, String target) {
        Group g = memberGroup(c, gid);
        if (g == null) return;
        if (!g.owner.equals(c.getUser())) {
            c.send(Protocol.ERROR, "Chỉ trưởng nhóm mới có thể mời thành viên ra khỏi nhóm");
            return;
        }
        if (target.equals(g.owner) || !g.members.remove(target)) {
            c.send(Protocol.ERROR, "Thành viên không hợp lệ");
            return;
        }
        toUser(target, Protocol.encode(Protocol.INFO, "Bạn đã bị mời ra khỏi nhóm “" + g.name + "”"));
        membershipChanged(g, systemMessage(g, c.getUser(), display(c.getUser()) + " đã mời " + display(target) + " ra khỏi nhóm"), List.of());
    }

    private void groupLeave(ClientHandler c, String gid) {
        String me = c.getUser();
        Group g = memberGroup(c, gid);
        if (g == null) return;
        g.members.remove(me);
        if (g.members.isEmpty()) {
            removeGroup(g);
            return;
        }
        String text = display(me) + " đã rời nhóm";
        if (g.owner.equals(me)) {
            g.owner = g.members.iterator().next();
            text += ". " + display(g.owner) + " trở thành trưởng nhóm";
        }
        membershipChanged(g, systemMessage(g, me, text), List.of());
    }

    private void groupRename(ClientHandler c, String gid, String name) {
        Group g = memberGroup(c, gid);
        if (g == null) return;
        name = name.trim();
        if (!Protocol.isValidDisplayName(name)) {
            c.send(Protocol.ERROR, "Tên nhóm không hợp lệ (1-40 ký tự)");
            return;
        }
        g.name = name;
        membershipChanged(g, systemMessage(g, c.getUser(), display(c.getUser()) + " đã đổi tên nhóm thành “" + name + "”"), List.of());
    }

    private void groupDisband(ClientHandler c, String gid) {
        Group g = memberGroup(c, gid);
        if (g == null) return;
        if (!g.owner.equals(c.getUser())) {
            c.send(Protocol.ERROR, "Chỉ trưởng nhóm mới có thể giải tán nhóm");
            return;
        }
        for (String u : g.members) {
            if (!u.equals(c.getUser())) toUser(u, Protocol.encode(Protocol.INFO, "Nhóm “" + g.name + "” đã bị giải tán"));
        }
        removeGroup(g);
    }

    private void removeGroup(Group g) {
        String conv = Protocol.groupConv(g.id);
        db.groups.remove(g.id);
        List<Message> list = db.messages.remove(conv);
        if (list != null) {
            for (Message m : list) db.byId.remove(m.id);
            for (Message m : list) releaseFile(m.fileId, conv);
        }
        db.reads.remove(conv);
        dirty = true;
        log("Xoa nhom #" + g.id + " " + g.name);
        broadcastAll(Protocol.encode(Protocol.GROUP_REMOVED, g.id));
    }

    // ===================== Gửi tin =====================

    private void toUser(String user, String line) {
        for (ClientHandler s : online.getOrDefault(user, Collections.emptySet())) s.send(line);
    }

    private void deliver(String conv, String line) {
        for (String u : participants(conv)) toUser(u, line);
    }

    private void broadcastAll(String line) {
        for (Set<ClientHandler> sessions : online.values()) for (ClientHandler s : sessions) s.send(line);
    }

    static void log(String msg) {
        System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + msg);
    }

    public static void main(String[] args) throws IOException {
        int port = Protocol.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Port khong hop le, dung mac dinh " + Protocol.DEFAULT_PORT);
            }
        }
        Path dataDir = Paths.get(args.length > 1 ? args[1] : "data");
        new ChatServer(port, dataDir).start();
    }
}
