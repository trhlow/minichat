package client;

import common.Message;
import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.View;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Khung hiển thị tin nhắn dạng bong bóng (giống Zalo):
 * tin của mình bên phải nền xanh nhạt, tin người khác bên trái nền trắng kèm avatar;
 * hỗ trợ trích dẫn trả lời, ảnh, file, cảm xúc, thu hồi, ngăn cách theo ngày, trạng thái "Đã xem".
 * Rê chuột vào tin để hiện nút Trả lời / Thả cảm xúc / Thêm; chuột phải để mở menu.
 */
final class MessagePanel extends JPanel implements Scrollable {

    /** Cửa sổ chính cung cấp dữ liệu và xử lý hành động của người dùng. */
    interface Host {
        String me();

        String name(String user);

        /** Ảnh đã tải (null nếu chưa có - host sẽ tự yêu cầu server gửi). */
        BufferedImage image(Message m);

        /** Dòng trạng thái dưới tin cuối cùng của mình ("Đã gửi", "Đã xem"...), null nếu không hiện. */
        String receipt(Conversation c, Message lastMine);

        void reply(Message m);

        void react(Message m, String emoji);

        void showMenu(Message m, Component invoker, int x, int y);

        void openAttachment(Message m);

        void loadMore();
    }

    private static final long RUN_GAP_MS = 5 * 60 * 1000;
    private static final int AVATAR = 34;

    private final Host host;
    private final JScrollPane scroll;
    private final Map<Long, Row> rows = new HashMap<>();
    private final Map<String, ImageIcon> thumbs = new HashMap<>();
    private final Timer resizeTimer;
    private Conversation conv;
    private boolean groupLike;
    private Row hovered;
    private long flashId;
    private Timer flashTimer;

    /** Theo dõi chuột trên toàn cửa sổ để biết đang rê vào tin nào (con của bong bóng nuốt sự kiện chuột). */
    private final AWTEventListener hoverTracker = e -> {
        if (!(e instanceof MouseEvent) || !isShowing()) return;
        MouseEvent me = (MouseEvent) e;
        if (me.getID() != MouseEvent.MOUSE_MOVED && me.getID() != MouseEvent.MOUSE_EXITED
                && me.getID() != MouseEvent.MOUSE_ENTERED) return;
        Component src = me.getComponent();
        Row target = null;
        if (src != null && SwingUtilities.isDescendingFrom(src, this) && me.getID() != MouseEvent.MOUSE_EXITED) {
            Point p = SwingUtilities.convertPoint(src, me.getPoint(), this);
            Component c = SwingUtilities.getDeepestComponentAt(this, p.x, p.y);
            while (c != null && c != this && !(c instanceof Row)) c = c.getParent();
            if (c instanceof Row) target = (Row) c;
        }
        setHovered(target);
    };

    MessagePanel(Host host) {
        this.host = host;
        setLayout(new GridBagLayout());
        setBackground(Theme.CHAT_BG);
        scroll = new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CHAT_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting() && e.getValue() == 0 && conv != null && conv.hasMore && !conv.loadingMore
                    && getHeight() > scroll.getViewport().getHeight()) {
                host.loadMore();
            }
        });
        resizeTimer = new Timer(80, e -> {
            invalidateTree(this);
            revalidate();
            repaint();
        });
        resizeTimer.setRepeats(false);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
    }

    JScrollPane scrollPane() {
        return scroll;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Toolkit.getDefaultToolkit().addAWTEventListener(hoverTracker, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(hoverTracker);
        super.removeNotify();
    }

    /** Hiển thị hội thoại khác và cuộn xuống cuối. */
    void show(Conversation c, boolean groupLike) {
        this.conv = c;
        this.groupLike = groupLike;
        rebuild();
        scrollToBottom();
    }

    Conversation conversation() {
        return conv;
    }

    /** Vẽ lại sau khi dữ liệu đổi, giữ nguyên vị trí cuộn (nếu đang ở cuối thì vẫn ở cuối). */
    void refresh() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;
        int fromBottom = bar.getMaximum() - bar.getValue();
        rebuild();
        Runnable restore = () -> bar.setValue(atBottom ? bar.getMaximum() : bar.getMaximum() - fromBottom);
        scroll.validate();
        restore.run();
        SwingUtilities.invokeLater(restore);
    }

    void scrollToBottom() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        scroll.validate();
        bar.setValue(bar.getMaximum());
        SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    /** Cuộn tới tin nhắn và nháy sáng nó. Trả về false nếu tin chưa được tải. */
    boolean scrollTo(long id) {
        Row r = rows.get(id);
        if (r == null) return false;
        Rectangle b = r.getBounds();
        b.y = Math.max(0, b.y - 60);
        b.height += 120;
        scrollRectToVisible(b);
        flashId = id;
        r.repaint();
        if (flashTimer != null) flashTimer.stop();
        flashTimer = new Timer(1600, e -> {
            flashId = 0;
            repaint();
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
        return true;
    }

    private void setHovered(Row r) {
        if (r == hovered) return;
        Row old = hovered;
        hovered = r;
        if (old != null) old.repaint();
        if (r != null) r.repaint();
    }

    private static void invalidateTree(Component c) {
        c.invalidate();
        if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) invalidateTree(ch);
    }

    /** Độ rộng tối đa của nội dung trong bong bóng, theo độ rộng khung chat. */
    private int maxContentWidth() {
        int w = scroll.getViewport().getWidth();
        if (w <= 0) w = 700;
        return Math.max(160, Math.min(480, (int) (w * 0.62)));
    }

    // ===================== Dựng danh sách =====================

    private void rebuild() {
        removeAll();
        rows.clear();
        hovered = null;
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), gc); // đẩy tin nhắn xuống đáy khi hội thoại ngắn
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        if (conv == null) return;

        if (conv.hasMore) {
            JButton more = Theme.secondaryButton(conv.loadingMore ? "Đang tải…" : "Xem tin nhắn cũ hơn");
            more.addActionListener(e -> host.loadMore());
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
            p.setOpaque(false);
            p.add(more);
            gc.gridy++;
            gc.insets = new Insets(8, 0, 4, 0);
            add(p, gc);
        }

        List<Message> list = conv.messages;
        String me = host.me();
        for (int i = 0; i < list.size(); i++) {
            Message m = list.get(i);
            Message prev = i > 0 ? list.get(i - 1) : null;
            Message next = i + 1 < list.size() ? list.get(i + 1) : null;
            boolean newDay = prev == null || !Theme.day(prev.time).equals(Theme.day(m.time));
            if (newDay) {
                gc.gridy++;
                gc.insets = new Insets(14, 0, 6, 0);
                add(pill(Theme.dayLabel(m.time), true), gc);
            }
            gc.gridy++;
            if (Message.SYSTEM.equals(m.type)) {
                gc.insets = new Insets(6, 0, 6, 0);
                add(pill(m.text, false), gc);
                continue;
            }
            boolean first = newDay || !sameRun(prev, m);
            boolean last = next == null || !sameRun(m, next) || !Theme.day(m.time).equals(Theme.day(next.time));
            Row row = new Row(m, m.from.equals(me), first, last);
            rows.put(m.id, row);
            gc.insets = new Insets(first ? 8 : 2, 12, 0, 12);
            add(row, gc);
        }

        Message last = conv.last();
        if (last != null && last.from.equals(me) && !Message.SYSTEM.equals(last.type)) {
            String rc = host.receipt(conv, last);
            if (rc != null) {
                JLabel l = new JLabel(rc);
                l.setFont(Theme.font(Font.PLAIN, 11.5f));
                l.setForeground(Theme.TEXT_2);
                JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                p.setOpaque(false);
                p.add(new PillLabel(l, new Color(0xC9D1DB)));
                gc.gridy++;
                gc.insets = new Insets(4, 12, 0, 14);
                add(p, gc);
            }
        }
        gc.gridy++;
        gc.insets = new Insets(0, 0, 0, 0);
        add(Box.createVerticalStrut(14), gc);
        revalidate();
        repaint();
    }

    private static boolean sameRun(Message a, Message b) {
        return a != null && b != null && !Message.SYSTEM.equals(a.type) && !Message.SYSTEM.equals(b.type)
                && a.from.equals(b.from) && Math.abs(b.time - a.time) <= RUN_GAP_MS;
    }

    /** Nhãn bo tròn ở giữa: ngày hoặc thông báo hệ thống. */
    private JComponent pill(String text, boolean day) {
        JLabel l = new JLabel(Theme.safe(text));
        l.setFont(Theme.font(day ? Font.BOLD : Font.PLAIN, 12));
        l.setForeground(day ? Color.WHITE : Theme.TEXT_2);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        p.add(new PillLabel(l, day ? new Color(0xA2ADBB) : new Color(255, 255, 255, 200)));
        return p;
    }

    // ===================== Một dòng tin nhắn =====================

    private final class Row extends JPanel {
        final Message m;
        final boolean mine;
        final Bubble bubble;
        final HoverActions actions;

        Row(Message m, boolean mine, boolean first, boolean last) {
            this.m = m;
            this.mine = mine;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

            Fit column = new Fit();
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            column.setOpaque(false);
            float align = mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
            if (!mine && groupLike && first) {
                JLabel name = new JLabel(host.name(m.from));
                name.setFont(Theme.font(Font.PLAIN, 12));
                name.setForeground(Theme.TEXT_2);
                name.setBorder(BorderFactory.createEmptyBorder(0, 4, 3, 6));
                name.setAlignmentX(align);
                column.add(name);
            }
            bubble = new Bubble(m, mine, last);
            bubble.setAlignmentX(align);
            column.add(bubble);
            if (!m.reactions.isEmpty()) {
                JComponent rc = reactionPill(m);
                rc.setAlignmentX(align);
                column.add(rc);
            }
            column.setAlignmentY(Component.TOP_ALIGNMENT);

            actions = new HoverActions(this);
            actions.setAlignmentY(Component.TOP_ALIGNMENT);
            if (mine) {
                add(Box.createHorizontalGlue());
                add(actions);
                add(Box.createHorizontalStrut(6));
                add(column);
            } else {
                JComponent av;
                if (first) {
                    av = new JLabel(Theme.avatar(host.name(m.from), m.from, AVATAR, false, false));
                } else {
                    av = (JComponent) Box.createRigidArea(new Dimension(AVATAR, 1));
                }
                av.setAlignmentY(Component.TOP_ALIGNMENT);
                add(av);
                add(Box.createHorizontalStrut(8));
                add(column);
                add(Box.createHorizontalStrut(6));
                add(actions);
                add(Box.createHorizontalGlue());
            }
            installMenu(bubble);
        }

        private void installMenu(Component c) {
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    popup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    popup(e);
                }

                private void popup(MouseEvent e) {
                    if (e.isPopupTrigger()) host.showMenu(m, e.getComponent(), e.getX(), e.getY());
                }
            };
            addMouseListenerTree(c, ma);
        }

        private void addMouseListenerTree(Component c, MouseAdapter ma) {
            c.addMouseListener(ma);
            if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) addMouseListenerTree(ch, ma);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (flashId == m.id) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.hq(g);
                g.setColor(new Color(0xFFF3C4));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.dispose();
            }
            super.paintComponent(g0);
        }
    }

    /** 3 nút nhỏ (Trả lời, Cảm xúc, Thêm) chỉ hiện khi rê chuột vào tin; luôn giữ chỗ để không bị giật bố cục. */
    private final class HoverActions extends JComponent {
        private static final int S = 26;
        private final Row row;
        private final String[] kinds;

        HoverActions(Row row) {
            this.row = row;
            kinds = row.m.recalled ? new String[]{"more"} : new String[]{"reply", "emoji", "more"};
            Dimension d = new Dimension(S * 3 + 4, S);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i < 0) return;
                    switch (kinds[i]) {
                        case "reply": host.reply(row.m); break;
                        case "emoji": reactionPopup(row.m, HoverActions.this, x0(i), S); break;
                        default: host.showMenu(row.m, HoverActions.this, x0(i), S);
                    }
                }
            });
        }

        private int x0(int i) {
            int start = row.mine ? getWidth() - kinds.length * S : 0;
            return start + i * S;
        }

        private int indexAt(int x) {
            for (int i = 0; i < kinds.length; i++) if (x >= x0(i) && x < x0(i) + S) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (hovered != row) return;
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            for (int i = 0; i < kinds.length; i++) {
                int x = x0(i);
                g.setColor(Color.WHITE);
                g.fillOval(x + 1, 1, S - 2, S - 2);
                g.setColor(Theme.DIVIDER);
                g.drawOval(x + 1, 1, S - 2, S - 2);
                String k = "more".equals(kinds[i]) ? "info" : kinds[i];
                Theme.icon(k, 16, Theme.TEXT_2).paintIcon(this, g, x + 5, 5);
            }
            g.dispose();
        }
    }

    /** Hộp chọn 6 cảm xúc. */
    void reactionPopup(Message m, Component invoker, int x, int y) {
        JPopupMenu pop = new JPopupMenu();
        pop.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
        String mineReaction = m.reactions.get(host.me());
        for (String r : Protocol.REACTIONS) {
            JButton b = Theme.iconButton(null, null);
            b.setText(r);
            b.setFont(Theme.font(Font.PLAIN, 22));
            if (r.equals(mineReaction)) {
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.PRIMARY, 1, true), BorderFactory.createEmptyBorder(3, 5, 3, 5)));
            } else {
                b.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
            b.addActionListener(e -> {
                pop.setVisible(false);
                host.react(m, r);
            });
            pop.add(b);
        }
        pop.show(invoker, x, y);
    }

    private JComponent reactionPill(Message m) {
        Map<String, Integer> count = new LinkedHashMap<>();
        List<String> who = new ArrayList<>();
        for (Map.Entry<String, String> e : m.reactions.entrySet()) {
            count.merge(e.getValue(), 1, Integer::sum);
            who.add(host.name(e.getKey()) + " " + e.getValue());
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String r : count.keySet()) if (n++ < 3) sb.append(r);
        if (m.reactions.size() > 1) sb.append(' ').append(m.reactions.size());
        JLabel l = new JLabel(sb.toString());
        l.setFont(Theme.font(Font.PLAIN, 12));
        l.setForeground(Theme.TEXT_2);
        PillLabel p = new PillLabel(l, Color.WHITE);
        p.setToolTipText(String.join(", ", who));
        p.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 6));
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        p.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                reactionPopup(m, p, 0, p.getHeight());
            }
        });
        return p;
    }

    // ===================== Bong bóng tin nhắn =====================

    private final class Bubble extends Fit {
        private final boolean mine;
        private final boolean media;

        Bubble(Message m, boolean mine, boolean showTime) {
            this.mine = mine;
            this.media = Message.IMAGE.equals(m.type) && !m.recalled;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(media ? BorderFactory.createEmptyBorder(4, 4, 6, 4) : BorderFactory.createEmptyBorder(8, 12, 7, 12));

            if (m.forwarded && !m.recalled) {
                JLabel f = new JLabel("Tin nhắn chuyển tiếp", Theme.icon("reply", 12, Theme.TEXT_2), SwingConstants.LEFT);
                f.setFont(Theme.font(Font.ITALIC, 11.5f));
                f.setForeground(Theme.TEXT_2);
                f.setBorder(BorderFactory.createEmptyBorder(media ? 2 : 0, media ? 6 : 0, 4, 0));
                addLeft(f);
            }
            if (m.replyTo > 0 && !m.recalled) addLeft(new Quote(m));

            if (m.recalled) {
                JLabel l = new JLabel("Tin nhắn đã được thu hồi");
                l.setFont(Theme.font(Font.ITALIC, 14));
                l.setForeground(Theme.TEXT_2);
                addLeft(l);
            } else if (Message.IMAGE.equals(m.type)) {
                addLeft(imageView(m));
            } else if (Message.FILE.equals(m.type)) {
                addLeft(fileCard(m));
            } else {
                boolean bigEmoji = isOnlyEmoji(m.text);
                addLeft(new WrapText(m.text, Theme.font(Font.PLAIN, bigEmoji ? 30 : 14.5f)));
            }
            if (showTime) {
                JLabel t = new JLabel(Theme.hm(m.time));
                t.setFont(Theme.font(Font.PLAIN, 11));
                t.setForeground(Theme.TEXT_2);
                t.setBorder(BorderFactory.createEmptyBorder(4, media ? 6 : 0, 0, 0));
                addLeft(t);
            }
        }

        private void addLeft(JComponent c) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(c);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            g.setColor(mine ? Theme.MY_BUBBLE : Theme.OTHER_BUBBLE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(mine ? new Color(0xC2DAFB) : Theme.BUBBLE_BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.dispose();
        }
    }

    private static boolean isOnlyEmoji(String s) {
        String t = s.strip();
        if (t.isEmpty() || t.codePointCount(0, t.length()) > 6) return false;
        return t.codePoints().noneMatch(cp -> Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) || cp < 0x2000);
    }

    /** Khung trích dẫn tin được trả lời; bấm vào để cuộn tới tin gốc. */
    private final class Quote extends JPanel {
        Quote(Message m) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(4, 10, 5, 8));
            JLabel who = new JLabel(host.name(m.replyFrom));
            who.setFont(Theme.font(Font.BOLD, 12.5f));
            who.setForeground(Theme.TEXT);
            JLabel what = new JLabel(Theme.safe(m.replyText));
            what.setFont(Theme.font(Font.PLAIN, 12.5f));
            what.setForeground(Theme.TEXT_2);
            add(who);
            add(what);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Xem tin nhắn gốc");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!scrollTo(m.replyTo)) Toolkit.getDefaultToolkit().beep();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.min(d.width, maxContentWidth()), d.height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            g.setColor(new Color(0, 0, 0, 14));
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 3, 8, 8);
            g.setColor(Theme.QUOTE_BAR);
            g.fillRoundRect(0, 0, 3, getHeight() - 3, 3, 3);
            g.dispose();
        }
    }

    private JComponent imageView(Message m) {
        JLabel l = new JLabel();
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText(Theme.safe(m.fileName + " (" + Protocol.humanSize(m.fileSize) + ") - bấm để xem"));
        ImageIcon icon = thumbs.get(m.fileId);
        if (icon == null) {
            BufferedImage img = host.image(m);
            if (img != null) {
                double s = Math.min(1.0, Math.min(260.0 / img.getWidth(), 260.0 / img.getHeight()));
                int w = Math.max(1, (int) (img.getWidth() * s));
                int h = Math.max(1, (int) (img.getHeight() * s));
                icon = new ImageIcon(roundedImage(img.getScaledInstance(w, h, Image.SCALE_SMOOTH), w, h));
                thumbs.put(m.fileId, icon);
            }
        }
        if (icon != null) {
            l.setIcon(icon);
        } else {
            l.setText("Đang tải ảnh…");
            l.setIcon(Theme.icon("image", 22, Theme.TEXT_2));
            l.setForeground(Theme.TEXT_2);
            l.setPreferredSize(new Dimension(200, 120));
            l.setHorizontalAlignment(SwingConstants.CENTER);
        }
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) host.openAttachment(m);
            }
        });
        return l;
    }

    private static BufferedImage roundedImage(Image src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Theme.hq(g);
        g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 14, 14));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private JComponent fileCard(Message m) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        String ext = m.fileName.contains(".") ? m.fileName.substring(m.fileName.lastIndexOf('.') + 1).toUpperCase() : "FILE";
        JLabel icon = new JLabel(new FileTypeIcon(ext));
        JLabel name = new JLabel(Theme.safe(shorten(m.fileName, 34)));
        name.setFont(Theme.font(Font.BOLD, 13.5f));
        name.setForeground(Theme.TEXT);
        JLabel size = new JLabel(Protocol.humanSize(m.fileSize));
        size.setFont(Theme.font(Font.PLAIN, 12));
        size.setForeground(Theme.TEXT_2);
        JPanel text = new JPanel(new java.awt.GridLayout(2, 1));
        text.setOpaque(false);
        text.add(name);
        text.add(size);
        JLabel dl = new JLabel(Theme.icon("download", 20, Theme.PRIMARY));
        p.add(icon, BorderLayout.WEST);
        p.add(text, BorderLayout.CENTER);
        p.add(dl, BorderLayout.EAST);
        p.setToolTipText("Bấm để lưu " + m.fileName);
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter open = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) host.openAttachment(m);
            }
        };
        p.addMouseListener(open);
        for (Component c : p.getComponents()) c.addMouseListener(open);
        Dimension d = p.getPreferredSize();
        p.setPreferredSize(new Dimension(Math.max(240, Math.min(d.width, 300)), Math.max(44, d.height)));
        return p;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max / 2 - 1) + "…" + s.substring(s.length() - max / 2 + 1);
    }

    /** Biểu tượng file có màu theo loại (PDF đỏ, Word xanh, Excel xanh lá...). */
    private static final class FileTypeIcon implements javax.swing.Icon {
        private final String ext;

        FileTypeIcon(String ext) {
            this.ext = ext.length() > 4 ? ext.substring(0, 4) : ext;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            Color col;
            switch (ext) {
                case "PDF": col = new Color(0xE5452F); break;
                case "DOC": case "DOCX": col = new Color(0x2B6FD6); break;
                case "XLS": case "XLSX": case "CSV": col = new Color(0x1E9E5A); break;
                case "PPT": case "PPTX": col = new Color(0xE8772E); break;
                case "ZIP": case "RAR": case "7Z": col = new Color(0x8C6BC8); break;
                default: col = new Color(0x6B7C93);
            }
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            p.moveTo(x + 4, y);
            p.lineTo(x + 26, y);
            p.lineTo(x + 36, y + 10);
            p.lineTo(x + 36, y + 44);
            p.lineTo(x + 4, y + 44);
            p.closePath();
            g.setColor(col);
            g.fill(p);
            g.setColor(new Color(255, 255, 255, 90));
            java.awt.geom.Path2D fold = new java.awt.geom.Path2D.Double();
            fold.moveTo(x + 26, y);
            fold.lineTo(x + 26, y + 10);
            fold.lineTo(x + 36, y + 10);
            fold.closePath();
            g.fill(fold);
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, ext.length() > 3 ? 8.5f : 10));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ext, x + 20 - fm.stringWidth(ext) / 2f, y + 32);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 40;
        }

        @Override
        public int getIconHeight() {
            return 44;
        }
    }

    // ===================== Thành phần phụ =====================

    /** Panel không bao giờ bị giãn quá kích thước ưa thích (cần cho BoxLayout căn trái/phải). */
    private static class Fit extends JPanel {
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    /** Nhãn có nền bo tròn. */
    private static final class PillLabel extends JPanel {
        private final Color bg;

        PillLabel(JLabel label, Color bg) {
            super(new BorderLayout());
            this.bg = bg;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
            add(label);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.dispose();
        }
    }

    /** Văn bản tự xuống dòng, rộng vừa nội dung nhưng không vượt quá maxContentWidth(). */
    private final class WrapText extends JTextArea {
        WrapText(String text, Font font) {
            super(text);
            setFont(font);
            setForeground(Theme.TEXT);
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(null);
            setMargin(new Insets(0, 0, 0, 0));
            setSelectionColor(new Color(0xB3D4FF));
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            int max = maxContentWidth();
            FontMetrics fm = getFontMetrics(getFont());
            int natural = 0;
            for (String line : getText().split("\n", -1)) natural = Math.max(natural, fm.stringWidth(line));
            int w = Math.max(8, Math.min(natural + 3, max));
            View root = getUI().getRootView(this);
            root.setSize(w, Integer.MAX_VALUE / 2);
            int h = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            return new Dimension(w, Math.max(h, fm.getHeight()));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }

    // ===================== Scrollable =====================

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(24, visibleRect.height - 48);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }
}
