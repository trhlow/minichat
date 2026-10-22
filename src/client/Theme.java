package client;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Màu sắc, font, avatar và icon theo phong cách Zalo (vẽ bằng Java2D, không cần file ảnh). */
final class Theme {
    private Theme() {}

    static final Color PRIMARY = new Color(0x0068FF);
    static final Color PRIMARY_DARK = new Color(0x0050C8);
    static final Color NAV_BG = new Color(0x005AE0);
    static final Color NAV_ACTIVE = new Color(0x0047B3);
    static final Color SIDEBAR_BG = Color.WHITE;
    static final Color SELECTED = new Color(0xE5EFFF);
    static final Color HOVER = new Color(0xF3F5F6);
    static final Color CHAT_BG = new Color(0xEBEEF2);
    static final Color MY_BUBBLE = new Color(0xDBEBFF);
    static final Color OTHER_BUBBLE = Color.WHITE;
    static final Color BUBBLE_BORDER = new Color(0xD5DCE4);
    static final Color TEXT = new Color(0x081C36);
    static final Color TEXT_2 = new Color(0x7589A3);
    static final Color DIVIDER = new Color(0xDFE2E7);
    static final Color BADGE = new Color(0xE53935);
    static final Color ONLINE = new Color(0x2DC937);
    static final Color QUOTE_BAR = new Color(0x0068FF);

    private static final Color[] AVATAR_COLORS = {
            new Color(0x4E8DF5), new Color(0xF26B5B), new Color(0x22B573), new Color(0xA56CF0),
            new Color(0xF59E2C), new Color(0x17A2B8), new Color(0xE0569A), new Color(0x5C6BC0)
    };

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Chặn Swing hiểu chuỗi do người dùng nhập là HTML (JLabel coi chuỗi bắt đầu bằng "&lt;html&gt;" là HTML,
     * có thể bị lợi dụng để chèn ảnh từ máy lạ). Thêm ký tự rộng 0 phía trước là đủ vô hiệu hoá.
     */
    static String safe(String s) {
        return s != null && s.startsWith("<") ? "\u200B" + s : s;
    }

    static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 12).deriveFont(style, size);
    }

    static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ===================== Thời gian =====================

    private static java.time.LocalDateTime local(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    static String hm(long t) {
        return local(t).format(HM);
    }

    /** Nhãn thời gian ở danh sách hội thoại. */
    static String shortTime(long t) {
        if (t <= 0) return "";
        long diff = System.currentTimeMillis() - t;
        if (diff < 60_000) return "Vài giây";
        if (diff < 3_600_000) return diff / 60_000 + " phút";
        LocalDate d = local(t).toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return hm(t);
        if (d.equals(today.minusDays(1))) return "Hôm qua";
        return d.getYear() == today.getYear() ? local(t).format(DM) : local(t).format(DMY);
    }

    /** Nhãn ngăn cách theo ngày trong khung chat. */
    static String dayLabel(long t) {
        LocalDate d = local(t).toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Hôm nay";
        if (d.equals(today.minusDays(1))) return "Hôm qua";
        return local(t).format(DMY);
    }

    static LocalDate day(long t) {
        return local(t).toLocalDate();
    }

    /** "Vừa truy cập", "Truy cập 5 phút trước"... */
    static String lastSeen(boolean online, long lastSeen) {
        if (online) return "Vừa truy cập";
        if (lastSeen <= 0) return "Không hoạt động";
        long diff = Math.max(0, System.currentTimeMillis() - lastSeen);
        if (diff < 60_000) return "Truy cập vài giây trước";
        if (diff < 3_600_000) return "Truy cập " + diff / 60_000 + " phút trước";
        if (diff < 86_400_000) return "Truy cập " + diff / 3_600_000 + " giờ trước";
        return "Truy cập " + diff / 86_400_000 + " ngày trước";
    }

    // ===================== Avatar =====================

    static Color avatarColor(String key) {
        return AVATAR_COLORS[Math.floorMod(key == null ? 0 : key.hashCode(), AVATAR_COLORS.length)];
    }

    /** Lấy chữ cái đầu của 2 từ cuối: "Trần Hoàng Long" -> "HL". */
    static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] w = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        if (w.length >= 2) sb.appendCodePoint(w[w.length - 2].codePointAt(0));
        sb.appendCodePoint(w[w.length - 1].codePointAt(0));
        return sb.toString().toUpperCase();
    }

    static void paintAvatar(Graphics2D g, int x, int y, int size, String name, String key, boolean group) {
        hq(g);
        Color c = avatarColor(key);
        g.setPaint(new java.awt.GradientPaint(x, y, c.brighter(), x, y + size, c));
        g.fill(new Ellipse2D.Double(x, y, size, size));
        g.setColor(Color.WHITE);
        if (group) {
            // biểu tượng 2 người cho nhóm
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.scale(size / 24.0, size / 24.0);
            g2.fill(new Ellipse2D.Double(6.5, 6, 5, 5));
            g2.fill(new Ellipse2D.Double(12.5, 6, 5, 5));
            g2.fill(new RoundRectangle2D.Double(4.5, 12, 8.5, 6.5, 5, 5));
            g2.fill(new RoundRectangle2D.Double(11, 12, 8.5, 6.5, 5, 5));
            g2.dispose();
            return;
        }
        String s = initials(name);
        g.setFont(font(Font.BOLD, size * 0.38f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x + (size - fm.stringWidth(s)) / 2f, y + (size - fm.getHeight()) / 2f + fm.getAscent());
    }

    /** Icon avatar tròn, kèm chấm xanh nếu đang online. */
    static Icon avatar(String name, String key, int size, boolean group, boolean online) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                paintAvatar(g, x, y, size, name, key, group);
                if (online) {
                    int d = Math.max(9, size / 4);
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(x + size - d - 1, y + size - d - 1, d + 2, d + 2));
                    g.setColor(ONLINE);
                    g.fill(new Ellipse2D.Double(x + size - d, y + size - d, d, d));
                }
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** Avatar phòng chat chung: quả địa cầu trên nền xanh. */
    static Icon globeAvatar(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                hq(g);
                g.setPaint(new java.awt.GradientPaint(x, y, new Color(0x3C8CFF), x, y + size, PRIMARY_DARK));
                g.fill(new Ellipse2D.Double(x, y, size, size));
                int s = (int) (size * 0.6);
                icon("globe", s, Color.WHITE).paintIcon(c, g, x + (size - s) / 2, y + (size - s) / 2);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    // ===================== Icon vẽ tay =====================

    static Icon icon(String kind, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                hq(g);
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawIcon(g, kind);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    private static void drawIcon(Graphics2D g, String kind) {
        Path2D p = new Path2D.Double();
        switch (kind) {
            case "chat":
                p.moveTo(4, 5);
                p.lineTo(20, 5);
                p.quadTo(21, 5, 21, 6);
                p.lineTo(21, 16);
                p.quadTo(21, 17, 20, 17);
                p.lineTo(10, 17);
                p.lineTo(6, 20.5);
                p.lineTo(6, 17);
                p.lineTo(4, 17);
                p.quadTo(3, 17, 3, 16);
                p.lineTo(3, 6);
                p.quadTo(3, 5, 4, 5);
                g.draw(p);
                g.drawLine(7, 10, 17, 10);
                g.drawLine(7, 13, 13, 13);
                break;
            case "contacts":
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                p.moveTo(4, 20.5);
                p.curveTo(4, 15, 8, 13.5, 12, 13.5);
                p.curveTo(16, 13.5, 20, 15, 20, 20.5);
                p.closePath();
                g.draw(p);
                break;
            case "search":
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.drawLine(15, 15, 20, 20);
                break;
            case "user-add":
                g.draw(new Ellipse2D.Double(5, 4, 8, 8));
                p.moveTo(2.5, 20);
                p.curveTo(2.5, 15.5, 5.5, 14, 9, 14);
                p.curveTo(12.5, 14, 15.5, 15.5, 15.5, 20);
                g.draw(p);
                g.drawLine(19, 8, 19, 14);
                g.drawLine(16, 11, 22, 11);
                break;
            case "group-add":
                g.draw(new Ellipse2D.Double(3.5, 5, 6.5, 6.5));
                g.draw(new Ellipse2D.Double(11, 5, 6.5, 6.5));
                p.moveTo(1.5, 19);
                p.curveTo(1.5, 15, 4, 14, 6.8, 14);
                p.curveTo(9.5, 14, 12, 15, 12, 19);
                p.moveTo(10.5, 14.5);
                p.curveTo(11.5, 14, 13, 14, 14.2, 14);
                g.draw(p);
                g.drawLine(19, 13, 19, 19);
                g.drawLine(16, 16, 22, 16);
                break;
            case "image":
                g.draw(new RoundRectangle2D.Double(3, 4.5, 18, 15, 3, 3));
                g.draw(new Ellipse2D.Double(7, 8, 3, 3));
                p.moveTo(3.5, 17.5);
                p.lineTo(9, 12.5);
                p.lineTo(13, 16);
                p.lineTo(16, 13);
                p.lineTo(20.5, 17);
                g.draw(p);
                break;
            case "file":
                p.moveTo(16.5, 7);
                p.lineTo(8.5, 15);
                p.quadTo(7, 17, 8.8, 18.4);
                p.quadTo(10.5, 19.5, 12, 18);
                p.lineTo(19.5, 10.5);
                p.quadTo(22, 7.5, 19.3, 4.8);
                p.quadTo(16.5, 2.5, 13.8, 5);
                p.lineTo(5.5, 13.3);
                p.quadTo(2, 17.5, 5.2, 20.5);
                p.quadTo(8.5, 23, 12, 20);
                p.lineTo(17.5, 14.5);
                g.draw(p);
                break;
            case "emoji":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(new Ellipse2D.Double(8, 8.5, 2.2, 2.2));
                g.fill(new Ellipse2D.Double(13.8, 8.5, 2.2, 2.2));
                p.moveTo(7.5, 14);
                p.quadTo(12, 18.5, 16.5, 14);
                g.draw(p);
                break;
            case "send":
                p.moveTo(3, 4);
                p.lineTo(21, 12);
                p.lineTo(3, 20);
                p.lineTo(5.5, 12);
                p.closePath();
                g.fill(p);
                break;
            case "info":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.drawLine(12, 11, 12, 16);
                g.fill(new Ellipse2D.Double(11, 6.8, 2, 2));
                break;
            case "close":
                g.drawLine(6, 6, 18, 18);
                g.drawLine(18, 6, 6, 18);
                break;
            case "reply":
                p.moveTo(9.5, 6);
                p.lineTo(4, 11.5);
                p.lineTo(9.5, 17);
                p.moveTo(4.5, 11.5);
                p.lineTo(14, 11.5);
                p.quadTo(20, 11.5, 20, 18);
                g.draw(p);
                break;
            case "logout":
                p.moveTo(14, 4);
                p.lineTo(6, 4);
                p.quadTo(4, 4, 4, 6);
                p.lineTo(4, 18);
                p.quadTo(4, 20, 6, 20);
                p.lineTo(14, 20);
                p.moveTo(10, 12);
                p.lineTo(21, 12);
                p.moveTo(17, 8);
                p.lineTo(21, 12);
                p.lineTo(17, 16);
                g.draw(p);
                break;
            case "globe":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Ellipse2D.Double(8, 3, 8, 18));
                g.drawLine(3, 12, 21, 12);
                break;
            case "download":
                g.drawLine(12, 4, 12, 15);
                p.moveTo(7.5, 10.5);
                p.lineTo(12, 15);
                p.lineTo(16.5, 10.5);
                p.moveTo(4.5, 19.5);
                p.lineTo(19.5, 19.5);
                g.draw(p);
                break;
            case "doc":
                p.moveTo(6, 2.5);
                p.lineTo(14, 2.5);
                p.lineTo(19, 7.5);
                p.lineTo(19, 21.5);
                p.lineTo(6, 21.5);
                p.closePath();
                p.moveTo(14, 2.5);
                p.lineTo(14, 7.5);
                p.lineTo(19, 7.5);
                g.draw(p);
                break;
            default:
                g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        }
    }

    // ===================== Nút =====================

    /** Nút chỉ có icon, nền trong suốt, đổi màu khi rê chuột. */
    static JButton iconButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g0) {
                if (getModel().isRollover() || getModel().isPressed()) {
                    Graphics2D g = (Graphics2D) g0.create();
                    hq(g);
                    g.setColor(getModel().isPressed() ? DIVIDER : HOVER);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g.dispose();
                }
                super.paintComponent(g0);
            }
        };
        b.setToolTipText(tooltip);
        flat(b);
        b.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return b;
    }

    /** Nút chính màu xanh. */
    static JButton primaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                hq(g);
                g.setColor(!isEnabled() ? DIVIDER : getModel().isPressed() || getModel().isRollover() ? PRIMARY_DARK : PRIMARY);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.dispose();
                super.paintComponent(g0);
            }
        };
        flat(b);
        b.setForeground(Color.WHITE);
        b.setFont(font(Font.BOLD, 14));
        b.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        return b;
    }

    /** Nút phụ nền xám nhạt. */
    static JButton secondaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                hq(g);
                g.setColor(getModel().isPressed() || getModel().isRollover() ? DIVIDER : new Color(0xEAEDF0));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.dispose();
                super.paintComponent(g0);
            }
        };
        flat(b);
        b.setForeground(TEXT);
        b.setFont(font(Font.BOLD, 13));
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        return b;
    }

    private static void flat(JButton b) {
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    static <T extends JComponent> T pad(T c, int t, int l, int b, int r) {
        c.setBorder(BorderFactory.createEmptyBorder(t, l, b, r));
        return c;
    }
}
