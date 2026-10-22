package client;

import common.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MiniChat Client có giao diện (Swing).
 * - Tab "Chat chung": gửi tới tất cả user online.
 * - Tab "#nhom": chat nhóm (phải tham gia nhóm).
 * - Tab "@user": chat riêng (double-click vào user để mở).
 *
 * Chạy: java client.ChatClientGUI
 */
public class ChatClientGUI extends JFrame {

    private static final String TAB_ALL = "Chat chung";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatConnection conn;
    private final String myName;

    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final DefaultListModel<String> groupModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userModel);
    private final JList<String> groupList = new JList<>(groupModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField input = new JTextField();

    /** tiêu đề tab -> vùng hiển thị tin nhắn */
    private final Map<String, JTextArea> chatAreas = new HashMap<>();
    /** các nhóm mình đang tham gia */
    private final Set<String> myGroups = new HashSet<>();

    public ChatClientGUI(ChatConnection conn, String myName) {
        super("MiniChat - " + myName);
        this.conn = conn;
        this.myName = myName;
        buildUI();
        openTab(TAB_ALL);
        conn.listen(line -> SwingUtilities.invokeLater(() -> handle(line)),
                () -> SwingUtilities.invokeLater(() -> {
                    append(TAB_ALL, "*** Mat ket noi toi server ***");
                    input.setEnabled(false);
                }));
    }

    // ===================== Giao diện =====================

    private void buildUI() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 580);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                conn.close();
                System.exit(0);
            }
        });

        // --- Cột trái: user online + nhóm ---
        userList.setToolTipText("Double-click de chat rieng");
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openPrivate();
            }
        });
        groupList.setToolTipText("Double-click de tham gia / mo nhom");
        groupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) joinSelectedGroup();
            }
        });

        JButton btnPrivate = new JButton("Chat rieng");
        btnPrivate.addActionListener(e -> openPrivate());
        JPanel usersPanel = titled("User online", new JScrollPane(userList), row(btnPrivate));

        JButton btnCreate = new JButton("Tao");
        JButton btnJoin = new JButton("Vao");
        JButton btnLeave = new JButton("Roi");
        btnCreate.addActionListener(e -> createGroup());
        btnJoin.addActionListener(e -> joinSelectedGroup());
        btnLeave.addActionListener(e -> leaveGroup());
        JPanel groupsPanel = titled("Nhom", new JScrollPane(groupList), row(btnCreate, btnJoin, btnLeave));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 8));
        left.add(usersPanel);
        left.add(groupsPanel);
        left.setPreferredSize(new Dimension(230, 0));

        // bỏ đánh dấu "tin mới" khi chuyển sang tab
        tabs.addChangeListener(e -> {
            int i = tabs.getSelectedIndex();
            if (i >= 0) tabs.setForegroundAt(i, null);
        });

        // --- Ô nhập tin ---
        JButton btnSend = new JButton("Gui");
        btnSend.addActionListener(e -> sendMessage());
        input.addActionListener(e -> sendMessage());
        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(tabs, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(8, 0));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(left, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);
    }

    private static JPanel titled(String title, JComponent body, JComponent footer) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(body, BorderLayout.CENTER);
        p.add(footer, BorderLayout.SOUTH);
        return p;
    }

    private static JPanel row(JButton... buttons) {
        JPanel p = new JPanel(new GridLayout(1, buttons.length, 4, 0));
        for (JButton b : buttons) p.add(b);
        return p;
    }

    /** Mở (hoặc chuyển tới) tab có tiêu đề cho trước. */
    private JTextArea openTab(String title) {
        JTextArea area = chatAreas.get(title);
        if (area == null) {
            area = new JTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            chatAreas.put(title, area);
            tabs.addTab(title, new JScrollPane(area));
        }
        return area;
    }

    private void selectTab(String title) {
        openTab(title);
        tabs.setSelectedIndex(tabs.indexOfTab(title));
        input.requestFocus();
    }

    private void closeTab(String title) {
        int i = tabs.indexOfTab(title);
        if (i >= 0) tabs.removeTabAt(i);
        chatAreas.remove(title);
    }

    private void append(String title, String text) {
        JTextArea area = openTab(title);
        area.append("[" + LocalTime.now().format(TIME) + "] " + text + "\n");
        area.setCaretPosition(area.getDocument().getLength());
        int i = tabs.indexOfTab(title);
        if (i >= 0 && i != tabs.getSelectedIndex()) tabs.setForegroundAt(i, Color.RED);
    }

    // ===================== Hành động người dùng =====================

    private void sendMessage() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        String tab = tabs.getTitleAt(tabs.getSelectedIndex());
        if (tab.startsWith("#")) {
            conn.send(Protocol.MSG_GROUP + " " + tab.substring(1) + " " + text);
        } else if (tab.startsWith("@")) {
            conn.send(Protocol.MSG_PRIVATE + " " + tab.substring(1) + " " + text);
        } else {
            conn.send(Protocol.MSG_ALL + " " + text);
        }
        input.setText("");
    }

    private void openPrivate() {
        String user = userList.getSelectedValue();
        if (user == null) {
            info("Chon mot user trong danh sach");
        } else if (user.equals(myName)) {
            info("Khong the chat rieng voi chinh minh");
        } else {
            selectTab("@" + user);
        }
    }

    private void createGroup() {
        String g = JOptionPane.showInputDialog(this, "Ten nhom moi:", "Tao nhom", JOptionPane.PLAIN_MESSAGE);
        if (g == null) return;
        g = g.trim();
        if (!Protocol.isValidName(g)) {
            info("Ten nhom khong hop le (chu, so, _ , -, toi da 20 ky tu, khong khoang trang)");
            return;
        }
        conn.send(Protocol.CREATE + " " + g);
    }

    private void joinSelectedGroup() {
        String g = selectedGroup();
        if (g == null) {
            info("Chon mot nhom trong danh sach");
        } else if (myGroups.contains(g)) {
            selectTab("#" + g);
        } else {
            conn.send(Protocol.JOIN + " " + g);
        }
    }

    private void leaveGroup() {
        String g = selectedGroup();
        if (g == null) {
            String tab = tabs.getTitleAt(tabs.getSelectedIndex());
            if (tab.startsWith("#")) g = tab.substring(1);
        }
        if (g == null || !myGroups.contains(g)) {
            info("Chon nhom ban dang tham gia de roi");
            return;
        }
        conn.send(Protocol.LEAVE + " " + g);
    }

    /** Mục trong list nhóm có dạng "ten (n)" hoặc "ten (n) ✓". */
    private String selectedGroup() {
        String v = groupList.getSelectedValue();
        return v == null ? null : v.substring(0, v.indexOf(' '));
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "MiniChat", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===================== Xử lý tin từ server =====================

    private void handle(String line) {
        String[] p = line.split(" ", 2);
        String rest = p.length > 1 ? p[1] : "";
        switch (p[0]) {
            case Protocol.ALL: {
                String[] q = rest.split(" ", 2);
                append(TAB_ALL, q[0] + ": " + (q.length > 1 ? q[1] : ""));
                break;
            }
            case Protocol.GROUP: {
                String[] q = rest.split(" ", 3);
                append("#" + q[0], q[1] + ": " + (q.length > 2 ? q[2] : ""));
                break;
            }
            case Protocol.PRIVATE: {
                String[] q = rest.split(" ", 3);
                String other = q[0].equals(myName) ? q[1] : q[0];
                append("@" + other, q[0] + ": " + (q.length > 2 ? q[2] : ""));
                break;
            }
            case Protocol.USERS:
                userModel.clear();
                for (String u : rest.split(",")) if (!u.isEmpty()) userModel.addElement(u);
                break;
            case Protocol.GROUPS:
                groupModel.clear();
                for (String item : rest.split(",")) {
                    if (item.isEmpty()) continue;
                    String[] gi = item.split(":");
                    groupModel.addElement(gi[0] + " (" + gi[1] + ")" + (myGroups.contains(gi[0]) ? " ✓" : ""));
                }
                break;
            case Protocol.JOINED:
                myGroups.add(rest);
                selectTab("#" + rest);
                append("#" + rest, "* Ban da tham gia nhom #" + rest);
                break;
            case Protocol.LEFT:
                myGroups.remove(rest);
                closeTab("#" + rest);
                append(TAB_ALL, "* Ban da roi nhom #" + rest);
                break;
            case Protocol.INFO: {
                // thông báo nhóm hiển thị trong tab nhóm, còn lại vào chat chung
                String target = TAB_ALL;
                int i = rest.lastIndexOf('#');
                if (i >= 0 && myGroups.contains(rest.substring(i + 1))) target = "#" + rest.substring(i + 1);
                append(target, "* " + rest);
                break;
            }
            case Protocol.ERROR:
                info(rest);
                break;
            default:
                append(TAB_ALL, line);
        }
    }

    // ===================== Đăng nhập =====================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(ChatClientGUI::showLogin);
    }

    private static void showLogin() {
        JTextField host = new JTextField("localhost");
        JTextField port = new JTextField("5000");
        JTextField name = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Server:"));
        form.add(host);
        form.add(new JLabel("Port:"));
        form.add(port);
        form.add(new JLabel("Ten cua ban:"));
        form.add(name);

        while (true) {
            int r = JOptionPane.showConfirmDialog(null, form, "MiniChat - Dang nhap",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) System.exit(0);
            try {
                ChatConnection conn = new ChatConnection(host.getText().trim(), Integer.parseInt(port.getText().trim()));
                String err = conn.login(name.getText().trim());
                if (err == null) {
                    new ChatClientGUI(conn, name.getText().trim()).setVisible(true);
                    return;
                }
                conn.close();
                JOptionPane.showMessageDialog(null, err, "Dang nhap that bai", JOptionPane.ERROR_MESSAGE);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Port khong hop le", "Loi", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Khong ket noi duoc server: " + e.getMessage(),
                        "Loi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
