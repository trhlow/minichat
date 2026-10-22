package client;

import common.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trạng thái 1 hội thoại phía client: tin nhắn đã tải, số chưa đọc, ai đã xem, ai đang soạn tin. */
final class Conversation {
    final String id;
    /** Tin nhắn đã tải, sắp xếp theo id tăng dần. */
    final List<Message> messages = new ArrayList<>();
    private final Map<Long, Message> byId = new HashMap<>();
    /** user khác -> id tin cuối cùng họ đã xem */
    final Map<String, Long> reads = new HashMap<>();
    /** user -> thời điểm hết hạn trạng thái "đang soạn tin" */
    final Map<String, Long> typing = new HashMap<>();
    /** Lịch sử đang nhận (HIST), gộp vào khi nhận HISTORY_END. */
    final List<Message> pendingHistory = new ArrayList<>();
    final long created = System.currentTimeMillis();
    int unread;
    boolean hasMore;
    boolean loadingMore;
    /** id tin mới nhất mình đã báo SEEN. */
    long seenSent;
    /** Nội dung đang soạn dở khi chuyển sang hội thoại khác. */
    String draft = "";

    Conversation(String id) {
        this.id = id;
    }

    /** Thêm hoặc cập nhật tin. Trả về true nếu là tin mới. */
    boolean put(Message m) {
        Message old = byId.put(m.id, m);
        if (old != null) {
            messages.set(messages.indexOf(old), m);
            return false;
        }
        int i = messages.size();
        while (i > 0 && messages.get(i - 1).id > m.id) i--;
        messages.add(i, m);
        return true;
    }

    boolean remove(long id) {
        Message m = byId.remove(id);
        return m != null && messages.remove(m);
    }

    Message get(long id) {
        return byId.get(id);
    }

    Message last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    long lastId() {
        Message m = last();
        return m == null ? 0 : m.id;
    }

    /** Thời điểm để sắp xếp danh sách hội thoại. */
    long activity() {
        Message m = last();
        return m == null ? created : m.time;
    }
}
