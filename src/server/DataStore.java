package server;

import common.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Toàn bộ dữ liệu của server: tài khoản, bạn bè, nhóm, tin nhắn, trạng thái đã xem.
 * Được lưu xuống file data/minichat.db (Java serialization) để không mất khi tắt server.
 * Mọi truy cập đều thực hiện trong khối synchronized của ChatServer.
 */
class DataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    static class Account implements Serializable {
        private static final long serialVersionUID = 1L;
        String username;
        String displayName;
        String salt;
        String passHash;
        long lastSeen;
    }

    static class Group implements Serializable {
        private static final long serialVersionUID = 1L;
        String id;
        String name;
        String owner;
        final Set<String> members = new LinkedHashSet<>();
    }

    final Map<String, Account> accounts = new HashMap<>();
    /** user -> tập bạn bè */
    final Map<String, Set<String>> friends = new HashMap<>();
    /** lời mời kết bạn dạng "from>to" */
    final Set<String> requests = new HashSet<>();
    final Map<String, Group> groups = new HashMap<>();
    /** conv -> danh sách tin nhắn (theo thứ tự thời gian) */
    final Map<String, List<Message>> messages = new HashMap<>();
    /** conv -> (user -> id tin nhắn cuối cùng đã xem) */
    final Map<String, Map<String, Long>> reads = new HashMap<>();
    /** fileId -> tên file gốc */
    final Map<String, String> fileNames = new HashMap<>();
    long nextMessageId = 1;
    long nextGroupId = 1;

    /** Chỉ mục id -> tin nhắn, dựng lại sau khi nạp. */
    transient Map<Long, Message> byId;
    /** Chỉ mục fileId -> hội thoại chứa file (để kiểm tra quyền tải). */
    transient Map<String, Set<String>> fileConvs;

    void rebuildIndex() {
        byId = new HashMap<>();
        fileConvs = new HashMap<>();
        for (List<Message> list : messages.values()) for (Message m : list) index(m);
    }

    void index(Message m) {
        byId.put(m.id, m);
        if (m.isFile() && !m.fileId.isEmpty()) fileConvs.computeIfAbsent(m.fileId, k -> new HashSet<>()).add(m.conv);
    }

    List<Message> conv(String conv) {
        return messages.computeIfAbsent(conv, k -> new ArrayList<>());
    }

    Set<String> friendsOf(String user) {
        return friends.computeIfAbsent(user, k -> new HashSet<>());
    }

    // ===================== Lưu / nạp =====================

    static DataStore load(Path file) {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file); ObjectInputStream ois = new ObjectInputStream(in)) {
                DataStore s = (DataStore) ois.readObject();
                s.rebuildIndex();
                return s;
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                ChatServer.log("Khong doc duoc " + file + " (" + e + "), tao du lieu moi");
            }
        }
        DataStore s = new DataStore();
        s.rebuildIndex();
        return s;
    }

    /** Ghi ra file tạm rồi đổi tên để không hỏng dữ liệu khi đang ghi thì tắt máy. */
    void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp); ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(this);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ===================== Mật khẩu =====================

    private static final SecureRandom RANDOM = new SecureRandom();

    static String newSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    static String newFileId() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** SHA-256(salt + mật khẩu), lặp nhiều lần để khó dò hơn. */
    static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = (salt + ":" + password).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 10_000; i++) h = md.digest(h);
            return Base64.getEncoder().encodeToString(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
