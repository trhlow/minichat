package common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Giao thức MiniChat: mỗi lệnh là 1 dòng UTF-8, các trường cách nhau bởi TAB.
 * Trong mỗi trường, ký tự \ , TAB, xuống dòng được escape thành \\ , \t , \n , \r
 * nên nội dung tin nhắn có thể chứa nhiều dòng.
 *
 * Mã hội thoại (conv):
 *   all              phòng chat chung (tất cả user)
 *   g:&lt;id&gt;          nhóm
 *   u:&lt;a&gt;:&lt;b&gt;       chat riêng giữa a và b (a &lt; b theo thứ tự chữ cái)
 *
 * Client -> Server
 *   REGISTER user pass displayName      | LOGIN user pass        | QUIT
 *   SEND conv text replyTo              | SEND_FILE conv fileName base64 replyTo
 *   FORWARD msgId conv                  | RECALL msgId           | REACT msgId emoji
 *   DELETE_MSG msgId                    | SEEN conv msgId        | TYPING conv 1|0
 *   HISTORY conv beforeId               | GET_FILE fileId        | PROFILE displayName
 *   FRIEND_REQUEST user | FRIEND_ACCEPT user | FRIEND_DECLINE user | FRIEND_REMOVE user
 *   GROUP_CREATE name u1,u2,..          | GROUP_JOIN gid         | GROUP_ADD gid u1,u2,..
 *   GROUP_KICK gid user                 | GROUP_LEAVE gid        | GROUP_RENAME gid name
 *   GROUP_DISBAND gid
 *
 * Server -> Client
 *   AUTH_OK user displayName            | AUTH_FAIL reason       | REGISTER_OK user
 *   USER user displayName online lastSeen
 *   FRIEND user status (friend | sent | received | none)
 *   GROUP gid name owner u1,u2,..       | GROUP_REMOVED gid
 *   MSG &lt;message&gt;  (tin mới)            | UPDATE &lt;message&gt;  (thu hồi / cảm xúc)
 *   HIST &lt;message&gt; (lịch sử)            | HISTORY_END conv hasMore(1|0)
 *   UNREAD conv count                   | READ conv user msgId   | DELETED msgId
 *   TYPING conv user 1|0                | FILE fileId fileName base64
 *   FILE_MISSING fileId                 | OPEN conv  (mở hội thoại vừa tạo / tham gia)
 *   INFO text                           | ERROR text
 */
public final class Protocol {
    private Protocol() {}

    public static final int DEFAULT_PORT = 5000;
    /** Dung lượng file tối đa (byte). */
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    /** Chỉ thu hồi được tin nhắn trong vòng 24 giờ (giống Zalo). */
    public static final long RECALL_WINDOW_MS = 24L * 60 * 60 * 1000;
    /** Độ dài tối đa 1 dòng lệnh (đủ cho file 10 MB dạng Base64). */
    public static final int MAX_LINE = (int) (MAX_FILE_SIZE / 3 * 4) + 64 * 1024;
    /** Độ dài tối đa 1 dòng lệnh trước khi đăng nhập. */
    public static final int MAX_LINE_GUEST = 4096;
    /** Số tin nhắn mỗi lần tải lịch sử. */
    public static final int HISTORY_PAGE = 40;

    public static final String CONV_ALL = "all";

    /** Các cảm xúc được phép thả vào tin nhắn. */
    public static final String[] REACTIONS = {"❤️", "👍", "😆", "😮", "😭", "😡"};

    // Client -> Server
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String QUIT = "QUIT";
    public static final String SEND = "SEND";
    public static final String SEND_FILE = "SEND_FILE";
    public static final String FORWARD = "FORWARD";
    public static final String RECALL = "RECALL";
    public static final String REACT = "REACT";
    public static final String DELETE_MSG = "DELETE_MSG";
    public static final String SEEN = "SEEN";
    public static final String HISTORY = "HISTORY";
    public static final String GET_FILE = "GET_FILE";
    public static final String PROFILE = "PROFILE";
    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String FRIEND_DECLINE = "FRIEND_DECLINE";
    public static final String FRIEND_REMOVE = "FRIEND_REMOVE";
    public static final String GROUP_CREATE = "GROUP_CREATE";
    public static final String GROUP_JOIN = "GROUP_JOIN";
    public static final String GROUP_ADD = "GROUP_ADD";
    public static final String GROUP_KICK = "GROUP_KICK";
    public static final String GROUP_LEAVE = "GROUP_LEAVE";
    public static final String GROUP_RENAME = "GROUP_RENAME";
    public static final String GROUP_DISBAND = "GROUP_DISBAND";

    // Hai chiều
    public static final String TYPING = "TYPING";

    // Server -> Client
    public static final String AUTH_OK = "AUTH_OK";
    public static final String AUTH_FAIL = "AUTH_FAIL";
    public static final String REGISTER_OK = "REGISTER_OK";
    public static final String USER = "USER";
    public static final String FRIEND = "FRIEND";
    public static final String GROUP = "GROUP";
    public static final String GROUP_REMOVED = "GROUP_REMOVED";
    public static final String MSG = "MSG";
    public static final String UPDATE = "UPDATE";
    public static final String HIST = "HIST";
    public static final String HISTORY_END = "HISTORY_END";
    public static final String UNREAD = "UNREAD";
    public static final String READ = "READ";
    public static final String DELETED = "DELETED";
    public static final String FILE = "FILE";
    /** File không còn / không có quyền tải. */
    public static final String FILE_MISSING = "FILE_MISSING";
    /** Yêu cầu client mở hội thoại (sau khi tạo / tham gia nhóm). */
    public static final String OPEN = "OPEN";
    public static final String INFO = "INFO";
    public static final String ERROR = "ERROR";

    // Trạng thái bạn bè
    public static final String FRIEND_YES = "friend";
    public static final String FRIEND_SENT = "sent";
    public static final String FRIEND_RECEIVED = "received";
    public static final String FRIEND_NONE = "none";

    // ===================== Mã hoá dòng lệnh =====================

    /** Ghép các trường thành 1 dòng lệnh. */
    public static String encode(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(escape(fields[i] == null ? "" : String.valueOf(fields[i])));
        }
        return sb.toString();
    }

    /** Tách 1 dòng lệnh thành các trường (đã bỏ escape). */
    public static String[] decode(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                switch (n) {
                    case 't': cur.append('\t'); break;
                    case 'n': cur.append('\n'); break;
                    case 'r': cur.append('\r'); break;
                    default: cur.append(n);
                }
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // ===================== Kiểm tra dữ liệu =====================

    /** Tên đăng nhập: 3-20 ký tự chữ thường không dấu, số, dấu _ . - */
    public static boolean isValidUsername(String s) {
        return s != null && s.matches("[a-z0-9_.-]{3,20}");
    }

    public static String normalizeUsername(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidPassword(String s) {
        return s != null && s.length() >= 4 && s.length() <= 64;
    }

    /** Tên hiển thị / tên nhóm: 1-40 ký tự, không rỗng. */
    public static boolean isValidDisplayName(String s) {
        return s != null && !s.trim().isEmpty() && s.trim().length() <= 40 && !s.matches(".*[\\p{Cntrl}].*");
    }

    public static boolean isReaction(String s) {
        for (String r : REACTIONS) if (r.equals(s)) return true;
        return false;
    }

    // ===================== Mã hội thoại =====================

    public static String privateConv(String a, String b) {
        return a.compareTo(b) <= 0 ? "u:" + a + ":" + b : "u:" + b + ":" + a;
    }

    public static String groupConv(String gid) {
        return "g:" + gid;
    }

    public static boolean isPrivate(String conv) {
        return conv != null && conv.startsWith("u:") && conv.indexOf(':', 2) > 2;
    }

    public static boolean isGroup(String conv) {
        return conv != null && conv.startsWith("g:") && conv.length() > 2;
    }

    public static String groupIdOf(String conv) {
        return conv.substring(2);
    }

    /** Hai thành viên của hội thoại riêng. */
    public static String[] membersOfPrivate(String conv) {
        int i = conv.indexOf(':', 2);
        return new String[]{conv.substring(2, i), conv.substring(i + 1)};
    }

    /** Người còn lại trong hội thoại riêng. */
    public static String peerOf(String conv, String me) {
        String[] m = membersOfPrivate(conv);
        return m[0].equals(me) ? m[1] : m[0];
    }

    public static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** Dung lượng dễ đọc: 1.2 MB, 350 KB... */
    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    public static boolean isImageFile(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }
}
