package client;

import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.Preferences;

/** Màn hình đăng nhập / đăng ký tài khoản MiniChat. */
final class LoginFrame extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginFrame.class);

    private final JTextField host = new HintField("Địa chỉ máy chủ");
    private final JTextField port = new HintField("Port");
    private final JTextField username = new HintField("Tên đăng nhập");
    private final JPasswordField password = new HintPassword("Mật khẩu");
    private final JPasswordField password2 = new HintPassword("Nhập lại mật khẩu");
    private final JTextField displayName = new HintField("Tên hiển thị (VD: Nguyễn Văn A)");
    private final JLabel error = new JLabel(" ");
    private final JButton submit = Theme.primaryButton("Đăng nhập");
    private final JLabel tabLogin = new JLabel("ĐĂNG NHẬP", SwingConstants.CENTER);
    private final JLabel tabRegister = new JLabel("ĐĂNG KÝ", SwingConstants.CENTER);
    private final JPanel serverRow = new JPanel(new BorderLayout(8, 0));
    private final JPanel form = new JPanel();
    private boolean registerMode;

    LoginFrame() {
        super("MiniChat - Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        host.setText(PREFS.get("host", "localhost"));
        port.setText(PREFS.get("port", String.valueOf(Protocol.DEFAULT_PORT)));
        username.setText(PREFS.get("user", ""));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0xE8F3FF));
        root.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));

        JLabel logo = new JLabel("MiniChat", SwingConstants.CENTER);
        logo.setFont(Theme.font(Font.BOLD, 40));
        logo.setForeground(Theme.PRIMARY);
        JLabel slogan = new JLabel("<html><div style='text-align:center'>Đăng nhập tài khoản MiniChat<br>để kết nối với bạn bè</div></html>",
                SwingConstants.CENTER);
        slogan.setFont(Theme.font(Font.PLAIN, 14));
        slogan.setForeground(Theme.TEXT);
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(logo, BorderLayout.NORTH);
        head.add(slogan, BorderLayout.CENTER);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        // --- thẻ trắng ---
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.hq(g);
                g.setColor(new Color(0, 0, 0, 18));
                g.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 3, 14, 14);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 14, 14);
                g.dispose();
            }
        };
        card.setOpaque(false);

        JPanel tabs = new JPanel(new GridLayout(1, 2));
        tabs.setOpaque(false);
        for (JLabel t : new JLabel[]{tabLogin, tabRegister}) {
            t.setFont(Theme.font(Font.BOLD, 13));
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tabs.add(t);
        }
        tabLogin.addMouseListener(click(() -> setMode(false)));
        tabRegister.addMouseListener(click(() -> setMode(true)));

        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(18, 28, 22, 28));

        serverRow.setOpaque(false);
        port.setColumns(5);
        serverRow.add(host, BorderLayout.CENTER);
        serverRow.add(port, BorderLayout.EAST);
        serverRow.setVisible(false);

        error.setForeground(Theme.BADGE);
        error.setFont(Theme.font(Font.PLAIN, 12.5f));

        submit.addActionListener(e -> submit());
        getRootPane().setDefaultButton(submit);

        JLabel serverLink = new JLabel("Cài đặt máy chủ", Theme.icon("globe", 14, Theme.PRIMARY), SwingConstants.CENTER);
        serverLink.setFont(Theme.font(Font.PLAIN, 12.5f));
        serverLink.setForeground(Theme.PRIMARY);
        serverLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverLink.addMouseListener(click(() -> {
            serverRow.setVisible(!serverRow.isVisible());
            pack();
        }));

        card.add(tabs, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        tabs.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        bottom.add(serverLink, BorderLayout.CENTER);

        root.add(head, BorderLayout.NORTH);
        root.add(card, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        setMode(false);
        setLocationRelativeTo(null);
    }

    private static MouseAdapter click(Runnable r) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                r.run();
            }
        };
    }

    private void setMode(boolean register) {
        registerMode = register;
        tabLogin.setForeground(register ? Theme.TEXT_2 : Theme.TEXT);
        tabRegister.setForeground(register ? Theme.TEXT : Theme.TEXT_2);
        Border active = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24),
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.PRIMARY),
                        BorderFactory.createEmptyBorder(0, 0, 10, 0)));
        Border inactive = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24),
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER),
                        BorderFactory.createEmptyBorder(0, 0, 11, 0)));
        tabLogin.setBorder(register ? inactive : active);
        tabRegister.setBorder(register ? active : inactive);

        form.removeAll();
        addField(serverRow);
        addField(username);
        if (register) addField(displayName);
        addField(password);
        if (register) addField(password2);
        error.setText(" ");
        error.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(error);
        form.add(Box.createVerticalStrut(8));
        submit.setText(register ? "Tạo tài khoản" : "Đăng nhập với mật khẩu");
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        form.add(submit);
        form.revalidate();
        form.repaint();
        pack();
        setSize(Math.max(getWidth(), 430), getHeight());
        (username.getText().isEmpty() ? username : password).requestFocusInWindow();
    }

    private void addField(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        form.add(c);
        form.add(Box.createVerticalStrut(10));
    }

    private void submit() {
        String h = host.getText().trim();
        String user = Protocol.normalizeUsername(username.getText());
        String pass = new String(password.getPassword());
        String name = displayName.getText().trim();
        int p;
        try {
            p = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            showError("Port không hợp lệ");
            serverRow.setVisible(true);
            pack();
            return;
        }
        if (user.isEmpty() || pass.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập và mật khẩu");
            return;
        }
        if (registerMode) {
            if (!Protocol.isValidUsername(user)) {
                showError("Tên đăng nhập 3-20 ký tự: chữ thường không dấu, số, _ . -");
                return;
            }
            if (!Protocol.isValidDisplayName(name)) {
                showError("Vui lòng nhập tên hiển thị (tối đa 40 ký tự)");
                return;
            }
            if (!pass.equals(new String(password2.getPassword()))) {
                showError("Mật khẩu nhập lại không khớp");
                return;
            }
        }
        submit.setEnabled(false);
        submit.setText("Đang kết nối…");
        error.setText(" ");
        boolean reg = registerMode;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                ChatConnection conn = new ChatConnection(h, p);
                try {
                    if (reg) {
                        String err = conn.register(user, pass, name);
                        if (err != null) throw new Exception(err);
                    }
                    return new Object[]{conn, conn.login(user, pass)};
                } catch (Exception e) {
                    conn.close();
                    throw e;
                }
            }

            @Override
            protected void done() {
                submit.setEnabled(true);
                submit.setText(registerMode ? "Tạo tài khoản" : "Đăng nhập với mật khẩu");
                try {
                    Object[] r = get();
                    String[] info = (String[]) r[1];
                    PREFS.put("host", h);
                    PREFS.put("port", String.valueOf(p));
                    PREFS.put("user", user);
                    ChatClientGUI gui = new ChatClientGUI((ChatConnection) r[0], info[0], info[1]);
                    gui.setVisible(true);
                    dispose();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable c = ex.getCause();
                    String msg = c.getMessage();
                    if (c instanceof java.net.ConnectException || c instanceof java.net.SocketTimeoutException
                            || c instanceof java.net.UnknownHostException) {
                        msg = "Không kết nối được máy chủ " + h + ":" + p;
                        serverRow.setVisible(true);
                        pack();
                    }
                    showError(msg == null ? c.toString() : msg);
                } catch (InterruptedException ignored) {
                }
            }
        }.execute();
    }

    private void showError(String msg) {
        error.setText("<html>" + msg.replace("<", "&lt;") + "</html>");
        pack();
    }

    // ===================== Ô nhập có chữ gợi ý =====================

    private static void style(JTextComponent c) {
        c.setFont(Theme.font(Font.PLAIN, 14));
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER), BorderFactory.createEmptyBorder(9, 4, 9, 4)));
        c.setOpaque(false);
    }

    private static void paintHint(JTextComponent c, Graphics g0, String hint) {
        if (c.getDocument().getLength() > 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hq(g);
        g.setColor(new Color(0x9AA8B8));
        g.setFont(c.getFont());
        java.awt.Insets in = c.getInsets();
        g.drawString(hint, in.left, in.top + g.getFontMetrics().getAscent());
        g.dispose();
    }

    static final class HintField extends JTextField {
        private final String hint;

        HintField(String hint) {
            this.hint = hint;
            style(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(this, g, hint);
        }
    }

    static final class HintPassword extends JPasswordField {
        private final String hint;

        HintPassword(String hint) {
            this.hint = hint;
            style(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(this, g, hint);
        }
    }
}
