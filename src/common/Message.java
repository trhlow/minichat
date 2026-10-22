package common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Một tin nhắn trong hội thoại. Dùng chung cho server (lưu trữ) và client (hiển thị).
 * Truyền qua mạng bằng {@link #toFields()} / {@link #fromFields(String[], int)}.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String TEXT = "TEXT";
    public static final String IMAGE = "IMAGE";
    public static final String FILE = "FILE";
    /** Thông báo hệ thống trong nhóm: tạo nhóm, thêm thành viên... */
    public static final String SYSTEM = "SYSTEM";

    public long id;
    public String conv;
    public String from;
    public long time;
    public String type = TEXT;
    public String text = "";
    public String fileId = "";
    public String fileName = "";
    public long fileSize;
    /** Tin nhắn được trả lời (0 = không trả lời) và bản tóm tắt của nó. */
    public long replyTo;
    public String replyFrom = "";
    public String replyText = "";
    public boolean recalled;
    public boolean forwarded;
    /** user -> cảm xúc */
    public final Map<String, String> reactions = new LinkedHashMap<>();
    /** Những user đã "xoá ở phía tôi" (chỉ dùng ở server). */
    public final Set<String> hiddenFor = new HashSet<>();

    /** Số trường khi truyền qua mạng. */
    public static final int FIELD_COUNT = 15;

    public Object[] toFields() {
        StringBuilder rs = new StringBuilder();
        for (Map.Entry<String, String> e : reactions.entrySet()) {
            if (rs.length() > 0) rs.append(';');
            rs.append(e.getKey()).append('=').append(e.getValue());
        }
        return new Object[]{id, conv, from, time, type, text, fileId, fileName, fileSize,
                replyTo, replyFrom, replyText, recalled ? 1 : 0, forwarded ? 1 : 0, rs};
    }

    /** Đọc tin nhắn từ mảng trường, bắt đầu tại vị trí off. */
    public static Message fromFields(String[] f, int off) {
        if (f.length < off + FIELD_COUNT) throw new IllegalArgumentException("Thieu truong tin nhan");
        Message m = new Message();
        m.id = Protocol.parseLong(f[off], 0);
        m.conv = f[off + 1];
        m.from = f[off + 2];
        m.time = Protocol.parseLong(f[off + 3], 0);
        m.type = f[off + 4];
        m.text = f[off + 5];
        m.fileId = f[off + 6];
        m.fileName = f[off + 7];
        m.fileSize = Protocol.parseLong(f[off + 8], 0);
        m.replyTo = Protocol.parseLong(f[off + 9], 0);
        m.replyFrom = f[off + 10];
        m.replyText = f[off + 11];
        m.recalled = "1".equals(f[off + 12]);
        m.forwarded = "1".equals(f[off + 13]);
        for (String item : f[off + 14].split(";")) {
            int i = item.indexOf('=');
            if (i > 0) m.reactions.put(item.substring(0, i), item.substring(i + 1));
        }
        return m;
    }

    /** Nội dung rút gọn để hiển thị ở danh sách hội thoại / khung trả lời. */
    public String snippet() {
        if (recalled) return "Tin nhắn đã được thu hồi";
        switch (type) {
            case IMAGE: return "[Hình ảnh]";
            case FILE: return "[File] " + fileName;
            default:
                String t = text.replace('\n', ' ');
                return t.length() > 80 ? t.substring(0, 80) + "…" : t;
        }
    }

    public boolean isFile() {
        return IMAGE.equals(type) || FILE.equals(type);
    }
}
