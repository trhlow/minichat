package client;

import common.Message;
import common.Protocol;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultEditorKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * MiniChat Client giao diện Swing theo phong cách Zalo.
 * - Thanh bên trái: Tin nhắn / Danh bạ, avatar của mình (đổi tên, đăng xuất).
 * - Danh sách hội thoại: avatar, trạng thái online, tin cuối, thời gian, số tin chưa đọc, "đang soạn tin".
 * - Khung chat: bong bóng tin nhắn, trả lời, thả cảm xúc, thu hồi, xoá phía tôi, chuyển tiếp,
 *   gửi ảnh / file (chọn file, kéo thả, dán ảnh Ctrl+V), emoji, "Đã xem", tìm tin nhắn.
 * - Bạn bè: gửi / đồng ý / từ chối lời mời, huỷ kết bạn. Nhóm: tạo, thêm / mời ra, đổi tên, rời, giải tán.
 *
 * Chạy: java client.ChatClientGUI
 */
public class ChatClientGUI extends JFrame implements MessagePanel.Host {

    private static final String[] EMOJIS = {
            "😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "😉", "😋",
            "🤔", "😅", "😆", "😮", "😴", "🥲", "😭", "😡", "😱", "😇", "🙃", "🤗",
            "👍", "👎", "👏", "🙏", "💪", "🤝", "👌", "✌️", "❤️", "💔", "🔥", "🎉",
            "🎂", "🌹", "☕", "🍀", "⭐", "✅", "❌", "💯", "🙈", "🐶", "🐱", "😤"};

    /** Thông tin 1 người dùng. */
    static final class UserInfo {
        final String username;
        String display;
        boolean online;
        long lastSeen;
        String friend = Protocol.FRIEND_NONE;

        UserInfo(String username) {
            this.username = username;
            this.display = username;
        }
    }

    /** Thông tin 1 nhóm. */
    static final class GroupInfo {
        String id;
        String name;
        String owner;
        List<String> members = new ArrayList<>();
    }

    private final ChatConnection conn;
    private final String me;
    private final Map<String, UserInfo> users = new HashMap<>();
    private final Map<String, GroupInfo> groups = new TreeMap<>();
    private final Map<String, Conversation> convs = new HashMap<>();
    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Set<String> requestedFiles = new HashSet<>();
    private final Map<String, List<Consumer<byte[]>>> fileWaiters = new HashMap<>();
    private Conversation current;
    private Message replyingTo;
    private boolean connected = true;
    private boolean closing;
    private boolean refreshQueued;
    private boolean listDirty, contactsDirty, chatDirty, chatToBottom;
    private boolean updatingList;
    private boolean typingSent;
    private String typingConv;
    private int ticks;
    private TrayIcon trayIcon;

    // ---- giao diện ----
    private final NavButton navChat = new NavButton("chat", "Tin nhắn");
    private final NavButton navContacts = new NavButton("contacts", "Danh bạ");
    private final JLabel myAvatar = new JLabel();
    private final CardLayout sideCards = new CardLayout();
    private final JPanel side = new JPanel(sideCards);
    private final JTextField search = new LoginFrame.HintField("Tìm kiếm");
    private final DefaultListModel<Conversation> listModel = new DefaultListModel<>();
    private final JList<Conversation> convList = new JList<>(listModel);
    private final JPanel contactsBox = new JPanel();
    private final CardLayout contentCards = new CardLayout();
    private final JPanel content = new JPanel(contentCards);
    private final JLabel headerAvatar = new JLabel();
    private final JLabel headerTitle = new JLabel();
    private final JLabel headerSub = new JLabel();
    private final JButton btnAddPeople = Theme.iconButton(Theme.icon("group-add", 22, Theme.TEXT), "Thêm bạn vào nhóm");
    private final JPanel friendBar = new JPanel(new BorderLayout(10, 0));
    private final MessagePanel messages = new MessagePanel(this);
    private final JLabel typingLabel = new JLabel(" ");
    private final JPanel replyBar = new JPanel(new BorderLayout(8, 0));
    private final JLabel replyTitle = new JLabel();
    private final JLabel replyText = new JLabel();
    private final JTextArea input = new JTextArea() {
        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (getDocument().getLength() == 0 && current != null) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.hq(g);
                g.setColor(new Color(0x9AA8B8));
                g.setFont(getFont());
                g.drawString("Nhập tin nhắn tới " + title(current) + " (Shift+Enter để xuống dòng)",
                        getInsets().left, getInsets().top + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    };
    private final JScrollPane inputScroll = new JScrollPane(input);
    private final JButton sendBtn = Theme.iconButton(null, "Gửi");
    private final Timer typingStop = new Timer(3000, e -> stopTyping());
    private final JLabel toast = new JLabel("", SwingConstants.CENTER);
    private final Timer toastTimer = new Timer(3500, e -> toast.setVisible(false));
    private final Timer ticker = new Timer(1000, e -> tick());
    /** File server báo không còn (hoặc không có quyền tải). */
    private final Set<String> missingFiles = new HashSet<>();

    public ChatClientGUI(ChatConnection conn, String me, String myDisplay) {
        super("MiniChat");
        this.conn = conn;
        this.me = me;
        user(me).display = myDisplay;
        ensureConv(Protocol.CONV_ALL);
        buildUI();
        updateMyAvatar();
        updateTitle();
        conn.listen(this::onLine, () -> SwingUtilities.invokeLater(this::onDisconnected));
        ticker.start();
    }

    /** Chạy trên luồng đọc socket: file (có thể ~13 MB Base64) được giải mã ở đây để không đơ giao diện. */
    private void onLine(String[] f) {
        if (Protocol.FILE.equals(f[0]) && f.length >= 4) {
            byte[] data;
            try {
                data = Base64.getDecoder().decode(f[3]);
            } catch (IllegalArgumentException e) {
                SwingUtilities.invokeLater(() -> handle(new String[]{Protocol.FILE_MISSING, f[1]}));
                return;
            }
            BufferedImage img = null;
            if (Protocol.isImageFile(f[2])) {
                try {
                    img = ImageIO.read(new ByteArrayInputStream(data));
                } catch (IOException ignored) {
                }
                if (img == null) img = brokenImage();
            }
            BufferedImage image = img;
            SwingUtilities.invokeLater(() -> {
                onFile(f[1], data, image);
                queueRefresh();
            });
        } else {
            SwingUtilities.invokeLater(() -> handle(f));
        }
    }

    @Override
    public void dispose() {
        ticker.stop();
        typingStop.stop();
        toastTimer.stop();
        super.dispose();
    }

    // =====================================================================
    //                              GIAO DIỆN
    // =====================================================================

    private void buildUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        setIconImage(appIcon(64));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closing = true;
                conn.close();
                dispose();
                System.exit(0);
            }

            @Override
            public void windowActivated(WindowEvent e) {
                markSeen();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildNav(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(buildSidebar(), BorderLayout.WEST);
        main.add(buildContent(), BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        toast.setOpaque(false);
        toast.setVisible(false);
        toast.setFont(Theme.font(Font.PLAIN, 13.5f));
        toast.setForeground(Color.WHITE);
        getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
        toastTimer.setRepeats(false);
        typingStop.setRepeats(false);
    }

    private JComponent buildNav() {
        JPanel nav = new JPanel();
        nav.setBackground(Theme.NAV_BG);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setPreferredSize(new Dimension(64, 0));
        nav.setBorder(BorderFactory.createEmptyBorder(16, 0, 12, 0));

        myAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        myAvatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        myAvatar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPopupMenu pm = new JPopupMenu();
                JLabel who = new JLabel(user(me).display + "  (@" + me + ")");
                who.setFont(Theme.font(Font.BOLD, 14));
                who.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                pm.add(who);
                pm.addSeparator();
                pm.add(menuItem("Hồ sơ của bạn / Đổi tên hiển thị", () -> showUserProfile(me)));
                pm.add(menuItem("Đăng xuất", ChatClientGUI.this::logout));
                pm.show(myAvatar, myAvatar.getWidth() + 4, 0);
            }
        });
        nav.add(myAvatar);
        nav.add(Box.createVerticalStrut(22));
        for (NavButton b : new NavButton[]{navChat, navContacts}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            nav.add(b);
        }
        navChat.addMouseListener(onClick(() -> showSide("chats")));
        navContacts.addMouseListener(onClick(() -> showSide("contacts")));
        navChat.selected = true;
        nav.add(Box.createVerticalGlue());
        JButton logout = Theme.iconButton(Theme.icon("logout", 22, Color.WHITE), "Đăng xuất");
        logout.setAlignmentX(Component.CENTER_ALIGNMENT);
        logout.addActionListener(e -> logout());
        nav.add(logout);
        return nav;
    }

    private JComponent buildSidebar() {
        JPanel box = new JPanel(new BorderLayout());
        box.setPreferredSize(new Dimension(340, 0));
        box.setBackground(Theme.SIDEBAR_BG);
        box.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.DIVIDER));

        // --- ô tìm kiếm + nút kết bạn / tạo nhóm ---
        search.setFont(Theme.font(Font.PLAIN, 13.5f));
        search.setBorder(BorderFactory.createEmptyBorder(7, 30, 7, 8));
        JPanel searchBox = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.hq(g);
                g.setColor(new Color(0xEAEDF0));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                Theme.icon("search", 16, Theme.TEXT_2).paintIcon(this, g, 9, (getHeight() - 16) / 2);
                g.dispose();
            }
        };
        searchBox.setOpaque(false);
        searchBox.add(search);
        search.getDocument().addDocumentListener(onChange(() -> {
            listDirty = true;
            contactsDirty = true;
            queueRefresh();
        }));
        JButton addFriend = Theme.iconButton(Theme.icon("user-add", 20, Theme.TEXT), "Thêm bạn");
        addFriend.addActionListener(e -> addFriendDialog());
        JButton newGroup = Theme.iconButton(Theme.icon("group-add", 20, Theme.TEXT), "Tạo nhóm chat");
        newGroup.addActionListener(e -> createGroupDialog(List.of()));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        tools.setOpaque(false);
        tools.add(addFriend);
        tools.add(newGroup);
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 8));
        top.add(searchBox, BorderLayout.CENTER);
        top.add(tools, BorderLayout.EAST);

        // --- danh sách hội thoại ---
        convList.setCellRenderer(new ConvCell());
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        convList.setFixedCellHeight(74);
        convList.setFixedCellWidth(100); // ô luôn rộng bằng danh sách, không sinh thanh cuộn ngang
        convList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList && convList.getSelectedValue() != null) {
                select(convList.getSelectedValue());
            }
        });
        convList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                convMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                convMenu(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ConvCell.hoverIndex = -1;
                convList.repaint();
            }
        });
        convList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = convList.locationToIndex(e.getPoint());
                if (i != ConvCell.hoverIndex) {
                    ConvCell.hoverIndex = i;
                    convList.repaint();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(convList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER));
        listScroll.getVerticalScrollBar().setUnitIncrement(20);

        contactsBox.setLayout(new BoxLayout(contactsBox, BoxLayout.Y_AXIS));
        contactsBox.setBackground(Color.WHITE);
        JPanel contactsWrap = new WidthTrackingPanel();
        contactsWrap.setBackground(Color.WHITE);
        contactsWrap.add(contactsBox, BorderLayout.NORTH);
        JScrollPane contactsScroll = new JScrollPane(contactsWrap, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contactsScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER));
        contactsScroll.getVerticalScrollBar().setUnitIncrement(20);
        contactsScroll.getViewport().setBackground(Color.WHITE);

        side.add(listScroll, "chats");
        side.add(contactsScroll, "contacts");
        box.add(top, BorderLayout.NORTH);
        box.add(side, BorderLayout.CENTER);
        return box;
    }

    private JComponent buildContent() {
        // --- màn hình chào ---
        JPanel welcome = new JPanel(new BorderLayout());
        welcome.setBackground(Color.WHITE);
        JLabel w = new JLabel("<html><div style='text-align:center'>"
                + "<div style='font-size:20px'>Chào mừng đến với <b>MiniChat!</b></div><br>"
                + "<div style='color:#7589A3;font-size:12px'>Khám phá những tiện ích hỗ trợ làm việc và trò chuyện<br>"
                + "cùng người thân, bạn bè được tối ưu hoá cho máy tính của bạn.</div><br><br>"
                + "<div style='color:#0068FF;font-size:12px'>Chọn một hội thoại bên trái để bắt đầu nhắn tin</div></div></html>",
                SwingConstants.CENTER);
        w.setFont(Theme.font(Font.PLAIN, 14));
        w.setForeground(Theme.TEXT);
        welcome.add(w);

        // --- header ---
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 10)));
        headerTitle.setFont(Theme.font(Font.BOLD, 16.5f));
        headerTitle.setForeground(Theme.TEXT);
        headerSub.setFont(Theme.font(Font.PLAIN, 12.5f));
        headerSub.setForeground(Theme.TEXT_2);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 1));
        titles.setOpaque(false);
        titles.add(headerTitle);
        titles.add(headerSub);
        headerAvatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerAvatar.addMouseListener(onClick(this::showConvInfo));
        titles.addMouseListener(onClick(this::showConvInfo));
        JButton btnSearch = Theme.iconButton(Theme.icon("search", 22, Theme.TEXT), "Tìm tin nhắn");
        btnSearch.addActionListener(e -> searchMessages());
        btnAddPeople.addActionListener(e -> addPeople());
        JButton btnInfo = Theme.iconButton(Theme.icon("info", 22, Theme.TEXT), "Thông tin hội thoại");
        btnInfo.addActionListener(e -> showConvInfo());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        actions.setOpaque(false);
        actions.add(btnAddPeople);
        actions.add(btnSearch);
        actions.add(btnInfo);
        header.add(headerAvatar, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);

        friendBar.setBackground(Color.WHITE);
        friendBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        JPanel north = new JPanel(new BorderLayout());
        north.add(header, BorderLayout.NORTH);
        north.add(friendBar, BorderLayout.SOUTH);

        // --- khung soạn tin ---
        typingLabel.setFont(Theme.font(Font.ITALIC, 12));
        typingLabel.setForeground(Theme.TEXT_2);
        typingLabel.setBorder(BorderFactory.createEmptyBorder(2, 16, 4, 16));
        JPanel typingRow = new JPanel(new BorderLayout());
        typingRow.setBackground(Theme.CHAT_BG);
        typingRow.add(typingLabel);

        replyTitle.setFont(Theme.font(Font.BOLD, 12.5f));
        replyTitle.setForeground(Theme.TEXT);
        replyTitle.setIcon(Theme.icon("reply", 14, Theme.TEXT_2));
        replyText.setFont(Theme.font(Font.PLAIN, 12.5f));
        replyText.setForeground(Theme.TEXT_2);
        JPanel replyTexts = new JPanel(new GridLayout(2, 1));
        replyTexts.setOpaque(false);
        replyTexts.add(replyTitle);
        replyTexts.add(replyText);
        JButton cancelReply = Theme.iconButton(Theme.icon("close", 16, Theme.TEXT_2), "Huỷ trả lời");
        cancelReply.addActionListener(e -> setReply(null));
        replyBar.setBackground(new Color(0xF3F6FA));
        replyBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12),
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.PRIMARY),
                        BorderFactory.createEmptyBorder(4, 10, 4, 4))));
        replyBar.add(replyTexts, BorderLayout.CENTER);
        replyBar.add(cancelReply, BorderLayout.EAST);
        replyBar.setVisible(false);

        JButton btnEmoji = Theme.iconButton(Theme.icon("emoji", 22, Theme.TEXT), "Biểu tượng cảm xúc");
        btnEmoji.addActionListener(e -> emojiPicker(btnEmoji));
        JButton btnImage = Theme.iconButton(Theme.icon("image", 22, Theme.TEXT), "Gửi hình ảnh");
        btnImage.addActionListener(e -> chooseFiles(true));
        JButton btnFile = Theme.iconButton(Theme.icon("file", 22, Theme.TEXT), "Đính kèm file (tối đa 10 MB)");
        btnFile.addActionListener(e -> chooseFiles(false));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);
        toolbar.add(btnEmoji);
        toolbar.add(btnImage);
        toolbar.add(btnFile);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.DIVIDER));

        input.setFont(Theme.font(Font.PLAIN, 14.5f));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 8));
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send-message");
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), DefaultEditorKit.insertBreakAction);
        input.getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendText();
            }
        });
        input.getDocument().addDocumentListener(onChange(this::onInputChanged));
        installTransfer();
        inputScroll.setBorder(BorderFactory.createEmptyBorder());
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sendBtn.addActionListener(e -> sendText());
        sendBtn.setFont(Theme.font(Font.PLAIN, 22));
        JPanel inputRow = new JPanel(new BorderLayout());
        inputRow.setOpaque(false);
        inputRow.add(inputScroll, BorderLayout.CENTER);
        JPanel sendWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        sendWrap.setOpaque(false);
        sendWrap.add(sendBtn);
        inputRow.add(sendWrap, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Color.WHITE);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER));
        composer.add(replyBar, BorderLayout.NORTH);
        JPanel composerBody = new JPanel(new BorderLayout());
        composerBody.setOpaque(false);
        composerBody.add(toolbar, BorderLayout.NORTH);
        composerBody.add(inputRow, BorderLayout.CENTER);
        composer.add(composerBody, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(typingRow, BorderLayout.NORTH);
        south.add(composer, BorderLayout.CENTER);

        JPanel chat = new JPanel(new BorderLayout());
        chat.add(north, BorderLayout.NORTH);
        chat.add(messages.scrollPane(), BorderLayout.CENTER);
        chat.add(south, BorderLayout.SOUTH);
        messages.setTransferHandler(fileDropHandler(null));

        content.add(welcome, "welcome");
        content.add(chat, "chat");
        updateSendButton();
        resizeInput();
        return content;
    }

    void showSide(String card) {
        sideCards.show(side, card);
        navChat.selected = card.equals("chats");
        navContacts.selected = !navChat.selected;
        navChat.repaint();
        navContacts.repaint();
        search.setText("");
        if (!navChat.selected) refreshContacts();
    }

    // =====================================================================
    //                          XỬ LÝ TIN TỪ SERVER
    // =====================================================================

    private void handle(String[] f) {
        try {
            dispatch(f);
        } catch (RuntimeException e) {
            System.err.println("Loi xu ly " + f[0] + ": " + e);
        }
    }

    private void dispatch(String[] f) {
        switch (f[0]) {
            case Protocol.USER: {
                UserInfo u = user(f[1]);
                u.display = f[2];
                u.online = "1".equals(f[3]);
                u.lastSeen = Protocol.parseLong(f[4], 0);
                if (u.username.equals(me)) updateMyAvatar();
                listDirty = contactsDirty = chatDirty = true;
                break;
            }
            case Protocol.FRIEND:
                user(f[1]).friend = f[2];
                contactsDirty = true;
                if (current != null && Protocol.isPrivate(current.id)) updateHeader();
                navContacts.badge = (int) users.values().stream().filter(x -> Protocol.FRIEND_RECEIVED.equals(x.friend)).count();
                navContacts.repaint();
                break;
            case Protocol.GROUP: {
                GroupInfo g = groups.computeIfAbsent(f[1], k -> new GroupInfo());
                g.id = f[1];
                g.name = f[2];
                g.owner = f[3];
                g.members = f[4].isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(f[4].split(",")));
                String conv = Protocol.groupConv(g.id);
                if (g.members.contains(me)) ensureConv(conv);
                else removeConv(conv);
                listDirty = contactsDirty = true;
                if (current != null && current.id.equals(conv)) updateHeader();
                break;
            }
            case Protocol.GROUP_REMOVED:
                groups.remove(f[1]);
                removeConv(Protocol.groupConv(f[1]));
                listDirty = contactsDirty = true;
                break;
            case Protocol.MSG: onMessage(Message.fromFields(f, 1)); break;
            case Protocol.UPDATE: {
                Message m = Message.fromFields(f, 1);
                Conversation c = convs.get(m.conv);
                if (c != null && c.get(m.id) != null) {
                    c.put(m);
                    if (replyingTo != null && replyingTo.id == m.id && m.recalled) setReply(null);
                    markDirty(c);
                }
                break;
            }
            case Protocol.HIST: {
                Message m = Message.fromFields(f, 1);
                ensureConv(m.conv).pendingHistory.add(m);
                break;
            }
            case Protocol.HISTORY_END: {
                Conversation c = ensureConv(f[1]);
                for (Message m : c.pendingHistory) c.put(m);
                c.pendingHistory.clear();
                c.hasMore = "1".equals(f[2]);
                c.loadingMore = false;
                markDirty(c);
                break;
            }
            case Protocol.UNREAD: {
                Conversation c = ensureConv(f[1]);
                c.unread = (int) Protocol.parseLong(f[2], 0);
                if (c == current) markSeen();
                listDirty = true;
                break;
            }
            case Protocol.READ: {
                Conversation c = convs.get(f[1]);
                if (c == null) break;
                long id = Protocol.parseLong(f[3], 0);
                if (f[2].equals(me)) {
                    c.seenSent = Math.max(c.seenSent, id);
                    if (id >= c.lastId()) c.unread = 0;
                } else {
                    c.reads.merge(f[2], id, Math::max);
                }
                markDirty(c);
                break;
            }
            case Protocol.DELETED: {
                long id = Protocol.parseLong(f[1], 0);
                for (Conversation c : convs.values()) if (c.remove(id)) markDirty(c);
                break;
            }
            case Protocol.TYPING: {
                Conversation c = convs.get(f[1]);
                if (c == null || f[2].equals(me)) break;
                if ("1".equals(f[3])) c.typing.put(f[2], System.currentTimeMillis() + 6000);
                else c.typing.remove(f[2]);
                listDirty = true;
                if (c == current) updateTyping();
                break;
            }
            case Protocol.FILE_MISSING: {
                missingFiles.add(f[1]);
                images.put(f[1], brokenImage());
                if (fileWaiters.remove(f[1]) != null) showToast("File không còn trên máy chủ", true);
                chatDirty = true;
                break;
            }
            case Protocol.OPEN: {
                Conversation c = ensureConv(f[1]);
                showSide("chats");
                select(c);
                break;
            }
            case Protocol.INFO:
                showToast(f[1], false);
                notifyDesktop("MiniChat", f[1]);
                break;
            case Protocol.ERROR: showToast(f[1], true); break;
            default:
                // lệnh không biết: bỏ qua để tương thích phiên bản server mới hơn
        }
        queueRefresh();
    }

    private void onMessage(Message m) {
        Conversation c = ensureConv(m.conv);
        boolean isNew = c.put(m);
        c.typing.remove(m.from);
        if (isNew && !m.from.equals(me) && !Message.SYSTEM.equals(m.type)) {
            if (c == current && isActive()) {
                markSeen();
            } else {
                c.unread++;
                notifyIncoming(c, m);
            }
        }
        if (c == current) {
            chatDirty = true;
            if (m.from.equals(me)) chatToBottom = true;
            updateTyping();
        }
        listDirty = true;
        updateTitle();
    }

    private void onFile(String fileId, byte[] data, BufferedImage img) {
        files.put(fileId, data);
        if (img != null) {
            images.put(fileId, img);
            chatDirty = true;
        }
        List<Consumer<byte[]>> waiters = fileWaiters.remove(fileId);
        if (waiters != null) for (Consumer<byte[]> w : waiters) w.accept(data);
    }

    private void markDirty(Conversation c) {
        listDirty = true;
        if (c == current) chatDirty = true;
    }

    /** Gom nhiều thay đổi liên tiếp (VD: lúc đăng nhập nhận hàng trăm dòng) thành 1 lần vẽ lại. */
    private void queueRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        SwingUtilities.invokeLater(() -> {
            refreshQueued = false;
            if (listDirty) refreshList();
            if (contactsDirty) refreshContacts();
            if (chatDirty && current != null) {
                updateHeader();
                if (chatToBottom) {
                    messages.refresh();
                    messages.scrollToBottom();
                } else {
                    messages.refresh();
                }
            }
            listDirty = contactsDirty = chatDirty = chatToBottom = false;
            updateTitle();
        });
    }

    private void onDisconnected() {
        if (closing) return;
        connected = false;
        input.setEnabled(false);
        sendBtn.setEnabled(false);
        headerSub.setText("Mất kết nối tới máy chủ");
        headerSub.setForeground(Theme.BADGE);
        setTitle("MiniChat - mất kết nối");
        int r = JOptionPane.showConfirmDialog(this, "Mất kết nối tới máy chủ MiniChat.\nBạn có muốn đăng nhập lại không?",
                "MiniChat", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            closing = true;
            dispose();
            new LoginFrame().setVisible(true);
        }
    }

    private void tick() {
        ticks++;
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Conversation c : convs.values()) changed |= c.typing.values().removeIf(t -> t < now);
        if (changed) {
            listDirty = true;
            updateTyping();
        }
        if (ticks % 30 == 0) {
            listDirty = true;
            if (current != null) updateHeader();
        }
        if (listDirty) queueRefresh();
    }

    // =====================================================================
    //                           HỘI THOẠI
    // =====================================================================

    /** Hội thoại theo mã (null nếu chưa có). */
    Conversation conversation(String id) {
        return convs.get(id);
    }

    /** Mã nhóm theo tên (null nếu không có). */
    String groupIdByName(String name) {
        for (GroupInfo g : groups.values()) if (g.name.equals(name)) return g.id;
        return null;
    }

    private UserInfo user(String username) {
        return users.computeIfAbsent(username, UserInfo::new);
    }

    @Override
    public String me() {
        return me;
    }

    @Override
    public String name(String username) {
        if (username == null || username.isEmpty()) return "";
        UserInfo u = users.get(username);
        return Theme.safe(u == null ? username : u.display);
    }

    private Conversation ensureConv(String id) {
        Conversation c = convs.get(id);
        if (c == null) {
            c = new Conversation(id);
            convs.put(id, c);
            listDirty = true;
        }
        return c;
    }

    private void removeConv(String id) {
        Conversation c = convs.remove(id);
        if (c != null && c == current) {
            current = null;
            setReply(null);
            contentCards.show(content, "welcome");
        }
        listDirty = true;
    }

    String title(Conversation c) {
        if (Protocol.CONV_ALL.equals(c.id)) return "Chat chung";
        if (Protocol.isGroup(c.id)) {
            GroupInfo g = groups.get(Protocol.groupIdOf(c.id));
            return g == null ? "Nhóm" : Theme.safe(g.name);
        }
        if (Protocol.isPrivate(c.id)) return name(Protocol.peerOf(c.id, me));
        return c.id;
    }

    private boolean isGroupLike(Conversation c) {
        return !Protocol.isPrivate(c.id);
    }

    private Icon convAvatar(Conversation c, int size) {
        if (Protocol.isPrivate(c.id)) {
            String peer = Protocol.peerOf(c.id, me);
            UserInfo u = users.get(peer);
            return Theme.avatar(name(peer), peer, size, false, u != null && u.online);
        }
        if (Protocol.CONV_ALL.equals(c.id)) return Theme.globeAvatar(size);
        return Theme.avatar(title(c), c.id, size, true, false);
    }

    /** Dòng xem trước ở danh sách hội thoại. */
    private String preview(Conversation c) {
        return Theme.safe(previewText(c));
    }

    private String previewText(Conversation c) {
        if (!c.typing.isEmpty()) {
            return (isGroupLike(c) ? name(c.typing.keySet().iterator().next()) + " " : "") + "đang soạn tin…";
        }
        Message m = c.last();
        if (m == null) return Protocol.CONV_ALL.equals(c.id) ? "Trò chuyện cùng mọi người" : "Bắt đầu trò chuyện";
        if (Message.SYSTEM.equals(m.type)) return m.text;
        String who = m.from.equals(me) ? "Bạn: " : isGroupLike(c) ? lastWord(name(m.from)) + ": " : "";
        return who + m.snippet();
    }

    private static String lastWord(String s) {
        String[] w = s.trim().split("\\s+");
        return w[w.length - 1];
    }

    void select(Conversation c) {
        if (c == null) return;
        if (c == current) {
            input.requestFocusInWindow();
            return;
        }
        stopTyping();
        if (current != null) current.draft = input.getText();
        if (!convs.containsKey(c.id)) convs.put(c.id, c);
        current = c;
        contentCards.show(content, "chat");
        setReply(null);
        input.setText(c.draft);
        c.draft = "";
        updateHeader();
        updateTyping();
        messages.show(c, isGroupLike(c));
        markSeen();
        listDirty = true;
        queueRefresh();
        input.requestFocusInWindow();
    }

    /** Báo server mình đã xem tới tin mới nhất (chỉ khi cửa sổ đang được dùng). */
    private void markSeen() {
        Conversation c = current;
        if (c == null || !connected || !isActive()) return;
        long last = c.lastId();
        if (c.unread > 0) {
            c.unread = 0;
            listDirty = true;
            queueRefresh();
        }
        if (last > c.seenSent) {
            c.seenSent = last;
            conn.send(Protocol.SEEN, c.id, last);
        }
    }

    private void updateTitle() {
        int total = 0;
        for (Conversation c : convs.values()) total += c.unread;
        navChat.badge = total;
        navChat.repaint();
        String base = "MiniChat - " + name(me);
        setTitle(total > 0 ? "(" + total + ") " + base : base);
    }

    private void updateMyAvatar() {
        myAvatar.setIcon(Theme.avatar(name(me), me, 44, false, false));
        myAvatar.setToolTipText(name(me) + " (@" + me + ")");
        updateTitle();
    }

    private void updateHeader() {
        Conversation c = current;
        if (c == null) return;
        headerAvatar.setIcon(convAvatar(c, 44));
        headerTitle.setText(title(c));
        headerSub.setForeground(connected ? Theme.TEXT_2 : Theme.BADGE);
        friendBar.removeAll();
        friendBar.setVisible(false);
        if (!connected) {
            headerSub.setText("Mất kết nối tới máy chủ");
        } else if (Protocol.CONV_ALL.equals(c.id)) {
            long online = users.values().stream().filter(u -> u.online).count();
            headerSub.setText("Cộng đồng · " + users.size() + " thành viên · " + online + " đang online");
            btnAddPeople.setVisible(false);
        } else if (Protocol.isGroup(c.id)) {
            GroupInfo g = groups.get(Protocol.groupIdOf(c.id));
            int n = g == null ? 0 : g.members.size();
            long online = g == null ? 0 : g.members.stream().filter(u -> user(u).online).count();
            headerSub.setText(n + " thành viên" + (online > 0 ? " · " + online + " đang online" : ""));
            btnAddPeople.setToolTipText("Thêm thành viên");
            btnAddPeople.setVisible(true);
        } else {
            UserInfo u = user(Protocol.peerOf(c.id, me));
            headerSub.setText(Theme.lastSeen(u.online, u.lastSeen));
            if (u.online) headerSub.setForeground(new Color(0x1E9E5A));
            btnAddPeople.setToolTipText("Tạo nhóm với " + u.display);
            btnAddPeople.setVisible(true);
            buildFriendBar(u);
        }
        friendBar.revalidate();
        friendBar.repaint();
    }

    /** Thanh gợi ý kết bạn khi chat với người lạ (giống Zalo). */
    private void buildFriendBar(UserInfo u) {
        if (Protocol.FRIEND_YES.equals(u.friend)) return;
        JLabel text = new JLabel();
        text.setFont(Theme.font(Font.PLAIN, 13));
        text.setForeground(Theme.TEXT);
        text.setIcon(Theme.icon("user-add", 18, Theme.TEXT_2));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        switch (u.friend) {
            case Protocol.FRIEND_RECEIVED: {
                text.setText(u.display + " đã gửi lời mời kết bạn");
                JButton ok = Theme.primaryButton("Đồng ý");
                ok.addActionListener(e -> conn.send(Protocol.FRIEND_ACCEPT, u.username));
                JButton no = Theme.secondaryButton("Từ chối");
                no.addActionListener(e -> conn.send(Protocol.FRIEND_DECLINE, u.username));
                buttons.add(no);
                buttons.add(ok);
                break;
            }
            case Protocol.FRIEND_SENT: {
                text.setText("Đã gửi lời mời kết bạn tới " + u.display);
                JButton cancel = Theme.secondaryButton("Thu hồi");
                cancel.addActionListener(e -> conn.send(Protocol.FRIEND_DECLINE, u.username));
                buttons.add(cancel);
                break;
            }
            default: {
                text.setText("Gửi yêu cầu kết bạn tới người này");
                JButton add = Theme.secondaryButton("Kết bạn");
                add.addActionListener(e -> conn.send(Protocol.FRIEND_REQUEST, u.username));
                buttons.add(add);
            }
        }
        friendBar.add(text, BorderLayout.CENTER);
        friendBar.add(buttons, BorderLayout.EAST);
        friendBar.setVisible(true);
    }

    private void updateTyping() {
        Conversation c = current;
        if (c == null || c.typing.isEmpty()) {
            typingLabel.setText(" ");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String u : c.typing.keySet()) names.add(name(u));
        typingLabel.setText(String.join(", ", names) + " đang soạn tin…");
    }

    /** Dựng lại danh sách hội thoại: sắp theo tin mới nhất, lọc theo ô tìm kiếm. */
    private void refreshList() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Conversation> list = new ArrayList<>();
        for (Conversation c : convs.values()) {
            if (!Protocol.isPrivate(c.id) || !c.messages.isEmpty() || c == current) {
                if (q.isEmpty() || matches(c, q)) list.add(c);
            }
        }
        list.sort(Comparator.comparingLong(Conversation::activity).reversed());
        if (!q.isEmpty()) {
            // gợi ý những người chưa từng nhắn tin
            for (UserInfo u : users.values()) {
                String conv = Protocol.privateConv(me, u.username);
                if (!u.username.equals(me) && !list.contains(convs.get(conv))
                        && (u.display.toLowerCase(Locale.ROOT).contains(q) || u.username.contains(q))) {
                    list.add(convs.containsKey(conv) ? convs.get(conv) : new Conversation(conv));
                }
            }
        }
        updatingList = true;
        try {
            listModel.clear();
            for (Conversation c : list) listModel.addElement(c);
            if (current != null) {
                int i = list.indexOf(current);
                if (i >= 0) convList.setSelectedIndex(i);
                else convList.clearSelection();
            }
        } finally {
            updatingList = false;
        }
        updateTitle();
    }

    private boolean matches(Conversation c, String q) {
        if (title(c).toLowerCase(Locale.ROOT).contains(q)) return true;
        return Protocol.isPrivate(c.id) && Protocol.peerOf(c.id, me).contains(q);
    }

    private void convMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int i = convList.locationToIndex(e.getPoint());
        if (i < 0) return;
        Conversation c = listModel.get(i);
        JPopupMenu pm = new JPopupMenu();
        pm.add(menuItem("Mở hội thoại", () -> select(c)));
        if (c.unread > 0) pm.add(menuItem("Đánh dấu đã đọc", () -> {
            c.unread = 0;
            conn.send(Protocol.SEEN, c.id, c.lastId());
            listDirty = true;
            queueRefresh();
        }));
        if (Protocol.isGroup(c.id)) {
            pm.add(menuItem("Thông tin nhóm", () -> showGroupInfo(Protocol.groupIdOf(c.id))));
            pm.add(menuItem("Rời nhóm", () -> leaveGroup(Protocol.groupIdOf(c.id))));
        } else if (Protocol.isPrivate(c.id)) {
            pm.add(menuItem("Xem thông tin", () -> showUserProfile(Protocol.peerOf(c.id, me))));
        }
        pm.show(convList, e.getX(), e.getY());
    }

    // =====================================================================
    //                           GỬI TIN
    // =====================================================================

    private void onInputChanged() {
        updateSendButton();
        resizeInput();
        if (current == null || !connected) return;
        if (input.getDocument().getLength() > 0) {
            if (!typingSent) {
                typingSent = true;
                typingConv = current.id;
                conn.send(Protocol.TYPING, current.id, 1);
            }
            typingStop.restart();
        } else {
            stopTyping();
        }
    }

    private void stopTyping() {
        typingStop.stop();
        if (typingSent && typingConv != null && connected) conn.send(Protocol.TYPING, typingConv, 0);
        typingSent = false;
    }

    private void updateSendButton() {
        boolean empty = input.getText().trim().isEmpty();
        if (empty) {
            sendBtn.setIcon(null);
            sendBtn.setText("👍");
            sendBtn.setToolTipText("Gửi nhanh biểu tượng 👍");
        } else {
            sendBtn.setText(null);
            sendBtn.setIcon(Theme.icon("send", 24, Theme.PRIMARY));
            sendBtn.setToolTipText("Gửi (Enter)");
        }
    }

    private void resizeInput() {
        FontMetrics fm = input.getFontMetrics(input.getFont());
        int lines = Math.max(1, Math.min(6, input.getLineCount()));
        inputScroll.setPreferredSize(new Dimension(100, lines * fm.getHeight() + 22));
        inputScroll.revalidate();
    }

    private void sendText() {
        if (current == null || !connected) return;
        String text = input.getText().strip();
        if (text.isEmpty()) text = "👍";
        if (text.length() > 5000) {
            showToast("Tin nhắn quá dài (tối đa 5000 ký tự)", true);
            return;
        }
        stopTyping();
        conn.send(Protocol.SEND, current.id, text, replyingTo == null ? 0 : replyingTo.id);
        input.setText("");
        setReply(null);
        chatToBottom = true;
    }

    private void setReply(Message m) {
        replyingTo = m;
        if (m != null) {
            replyTitle.setText("Trả lời " + name(m.from));
            replyText.setText(Theme.safe(m.snippet()));
        }
        replyBar.setVisible(m != null);
        replyBar.revalidate();
    }

    private void emojiPicker(Component invoker) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 8, 2, 2));
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (String e : EMOJIS) {
            JButton b = Theme.iconButton(null, null);
            b.setText(e);
            b.setFont(Theme.font(Font.PLAIN, 22));
            b.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            b.addActionListener(ev -> {
                input.replaceSelection(e);
                input.requestFocusInWindow();
            });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(invoker, 0, -pop.getPreferredSize().height);
    }

    // ---- file / ảnh ----

    private void chooseFiles(boolean imagesOnly) {
        if (current == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle(imagesOnly ? "Chọn hình ảnh" : "Chọn file đính kèm");
        if (imagesOnly) fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) sendFile(f);
        }
    }

    private void sendFile(File f) {
        if (current == null || !connected) return;
        if (!f.isFile()) return;
        if (f.length() > Protocol.MAX_FILE_SIZE) {
            showToast("“" + f.getName() + "” vượt quá " + Protocol.humanSize(Protocol.MAX_FILE_SIZE), true);
            return;
        }
        try {
            sendBytes(f.getName(), Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            showToast("Không đọc được file: " + e.getMessage(), true);
        }
    }

    /** Gửi dữ liệu file ở luồng nền để không đơ giao diện. */
    private void sendBytes(String name, byte[] data) {
        if (current == null) return;
        String conv = current.id;
        long reply = replyingTo == null ? 0 : replyingTo.id;
        setReply(null);
        chatToBottom = true;
        showToast("Đang gửi " + name + " (" + Protocol.humanSize(data.length) + ")…", false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                conn.send(Protocol.SEND_FILE, conv, name, Base64.getEncoder().encodeToString(data), reply);
                return null;
            }
        }.execute();
    }

    private void sendImage(Image img) {
        try {
            BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            if (out.size() > Protocol.MAX_FILE_SIZE) {
                showToast("Ảnh quá lớn", true);
                return;
            }
            sendBytes("anh-" + System.currentTimeMillis() + ".png", out.toByteArray());
        } catch (IOException e) {
            showToast("Không gửi được ảnh: " + e.getMessage(), true);
        }
    }

    /** Cho phép dán ảnh (Ctrl+V) và kéo thả file vào ô nhập, vẫn giữ copy/paste chữ bình thường. */
    private void installTransfer() {
        input.setTransferHandler(fileDropHandler(input.getTransferHandler()));
    }

    private TransferHandler fileDropHandler(TransferHandler text) {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    return current != null;
                }
                return text != null && text.canImport(s);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        for (File f : (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)) sendFile(f);
                        return true;
                    }
                    if (s.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        Image img = (Image) s.getTransferable().getTransferData(DataFlavor.imageFlavor);
                        int ok = JOptionPane.showConfirmDialog(ChatClientGUI.this, new JLabel(new ImageIcon(scaleToFit(img, 360))),
                                "Gửi ảnh tới " + title(current) + "?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if (ok == JOptionPane.OK_OPTION) sendImage(img);
                        return true;
                    }
                } catch (Exception e) {
                    showToast("Không nhận được dữ liệu: " + e.getMessage(), true);
                    return false;
                }
                return text != null && text.importData(s);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return text == null ? NONE : text.getSourceActions(c);
            }

            @Override
            public void exportToClipboard(JComponent comp, java.awt.datatransfer.Clipboard clip, int action) {
                if (text != null) text.exportToClipboard(comp, clip, action);
            }

            @Override
            public void exportAsDrag(JComponent comp, java.awt.event.InputEvent e, int action) {
                if (text != null) text.exportAsDrag(comp, e, action);
            }
        };
    }

    private static Image scaleToFit(Image img, int max) {
        int w = img.getWidth(null), h = img.getHeight(null);
        double s = Math.min(1.0, Math.min((double) max / w, (double) max / h));
        return img.getScaledInstance(Math.max(1, (int) (w * s)), Math.max(1, (int) (h * s)), Image.SCALE_SMOOTH);
    }

    private static BufferedImage brokenImage() {
        BufferedImage b = new BufferedImage(160, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        Theme.hq(g);
        g.setColor(new Color(0xE3E7EC));
        g.fillRect(0, 0, 160, 90);
        g.setColor(Theme.TEXT_2);
        g.setFont(Theme.font(Font.PLAIN, 12));
        g.drawString("Không xem được ảnh", 22, 50);
        g.dispose();
        return b;
    }

    // =====================================================================
    //                    MessagePanel.Host: hành động trên tin
    // =====================================================================

    @Override
    public BufferedImage image(Message m) {
        BufferedImage img = images.get(m.fileId);
        if (img == null && !m.fileId.isEmpty()) requestFile(m.fileId);
        return img;
    }

    private void requestFile(String fileId) {
        if (requestedFiles.add(fileId)) conn.send(Protocol.GET_FILE, fileId);
    }

    private void withFile(String fileId, Consumer<byte[]> action) {
        byte[] data = files.get(fileId);
        if (data != null) {
            action.accept(data);
            return;
        }
        if (missingFiles.contains(fileId)) {
            showToast("File không còn trên máy chủ", true);
            return;
        }
        fileWaiters.computeIfAbsent(fileId, k -> new ArrayList<>()).add(action);
        requestFile(fileId); // không gửi lại nếu đang tải
    }

    @Override
    public String receipt(Conversation c, Message last) {
        if (Protocol.CONV_ALL.equals(c.id)) return null;
        if (Protocol.isPrivate(c.id)) {
            String peer = Protocol.peerOf(c.id, me);
            if (c.reads.getOrDefault(peer, 0L) >= last.id) return "✓✓ Đã xem";
            return user(peer).online ? "✓ Đã nhận" : "✓ Đã gửi";
        }
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, Long> e : c.reads.entrySet()) {
            if (e.getValue() >= last.id && !e.getKey().equals(me)) seen.add(lastWord(name(e.getKey())));
        }
        if (seen.isEmpty()) return "✓ Đã gửi";
        seen.sort(java.text.Collator.getInstance(Locale.forLanguageTag("vi-VN")));
        return seen.size() <= 3 ? "✓✓ Đã xem: " + String.join(", ", seen) : "✓✓ " + seen.size() + " người đã xem";
    }

    @Override
    public void reply(Message m) {
        if (m.recalled) return;
        setReply(m);
        input.requestFocusInWindow();
    }

    @Override
    public void react(Message m, String emoji) {
        conn.send(Protocol.REACT, m.id, emoji);
    }

    @Override
    public void showMenu(Message m, Component invoker, int x, int y) {
        JPopupMenu pm = new JPopupMenu();
        if (!m.recalled) {
            pm.add(menuItem("Trả lời", () -> reply(m)));
            pm.add(menuItem("Thả cảm xúc…", () -> messages.reactionPopup(m, invoker, x, y)));
            if (Message.TEXT.equals(m.type)) {
                pm.add(menuItem("Sao chép tin nhắn", () -> {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(m.text), null);
                    showToast("Đã sao chép", false);
                }));
            }
            if (Message.IMAGE.equals(m.type)) pm.add(menuItem("Xem ảnh", () -> openAttachment(m)));
            if (m.isFile()) pm.add(menuItem("Lưu về máy…", () -> saveFile(m)));
            pm.add(menuItem("Chuyển tiếp…", () -> forwardDialog(m)));
            pm.addSeparator();
            if (m.from.equals(me) && System.currentTimeMillis() - m.time <= Protocol.RECALL_WINDOW_MS) {
                JMenuItem recall = menuItem("Thu hồi", () -> {
                    int r = JOptionPane.showConfirmDialog(this, "Thu hồi tin nhắn này với mọi người?", "Thu hồi tin nhắn",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (r == JOptionPane.OK_OPTION) conn.send(Protocol.RECALL, m.id);
                });
                recall.setForeground(Theme.BADGE);
                pm.add(recall);
            }
        }
        JMenuItem del = menuItem("Xoá chỉ ở phía tôi", () -> conn.send(Protocol.DELETE_MSG, m.id));
        del.setForeground(Theme.BADGE);
        pm.add(del);
        pm.show(invoker, x, y);
    }

    @Override
    public void openAttachment(Message m) {
        if (m.recalled) return;
        if (Message.IMAGE.equals(m.type)) withFile(m.fileId, data -> showImageViewer(m, data));
        else saveFile(m);
    }

    @Override
    public void loadMore() {
        Conversation c = current;
        if (c == null || !c.hasMore || c.loadingMore || c.messages.isEmpty()) return;
        c.loadingMore = true;
        conn.send(Protocol.HISTORY, c.id, c.messages.get(0).id);
        messages.refresh();
    }

    private void saveFile(Message m) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lưu file");
        fc.setSelectedFile(new File(m.fileName));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = fc.getSelectedFile();
        showToast("Đang tải " + m.fileName + "…", false);
        withFile(m.fileId, data -> {
            try {
                Files.write(target.toPath(), data);
                showToast("Đã lưu " + target.getName(), false);
            } catch (IOException e) {
                showToast("Không lưu được file: " + e.getMessage(), true);
            }
        });
    }

    private void showImageViewer(Message m, byte[] data) {
        BufferedImage img = images.get(m.fileId);
        if (img == null) return;
        JDialog d = new JDialog(this, m.fileName + " - " + name(m.from) + ", " + Theme.dayLabel(m.time) + " " + Theme.hm(m.time), false);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int max = (int) (Math.min(screen.width, screen.height) * 0.8);
        JLabel view = new JLabel(new ImageIcon(scaleToFit(img, max)));
        view.setOpaque(true);
        view.setBackground(new Color(0x1F2329));
        JButton save = Theme.primaryButton("Lưu ảnh");
        save.addActionListener(e -> saveFile(m));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bar.setBackground(new Color(0x1F2329));
        bar.add(save);
        d.add(new JScrollPane(view), BorderLayout.CENTER);
        d.add(bar, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // =====================================================================
    //                              HỘP THOẠI
    // =====================================================================

    private void searchMessages() {
        Conversation c = current;
        if (c == null) return;
        String q = JOptionPane.showInputDialog(this, "Tìm trong các tin nhắn đã tải của “" + title(c) + "”:",
                "Tìm tin nhắn", JOptionPane.PLAIN_MESSAGE);
        if (q == null || q.isBlank()) return;
        String lq = q.trim().toLowerCase(Locale.ROOT);
        DefaultListModel<Message> found = new DefaultListModel<>();
        for (int i = c.messages.size() - 1; i >= 0; i--) {
            Message m = c.messages.get(i);
            if (!m.recalled && (m.text.toLowerCase(Locale.ROOT).contains(lq) || m.fileName.toLowerCase(Locale.ROOT).contains(lq))) {
                found.addElement(m);
            }
        }
        if (found.isEmpty()) {
            showToast("Không tìm thấy tin nhắn nào chứa “" + q.trim() + "”", true);
            return;
        }
        JList<Message> list = new JList<>(found);
        list.setCellRenderer((l, m, i, sel, focus) -> {
            JLabel lb = new JLabel("<html><b>" + esc(name(m.from)) + "</b> <span style='color:#7589A3'>"
                    + Theme.dayLabel(m.time) + " " + Theme.hm(m.time) + "</span><br>" + esc(m.snippet()) + "</html>");
            lb.setFont(Theme.font(Font.PLAIN, 13));
            lb.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            lb.setOpaque(true);
            lb.setBackground(sel ? Theme.SELECTED : Color.WHITE);
            return lb;
        });
        JDialog d = dialog("Kết quả tìm kiếm (" + found.size() + ")");
        list.addMouseListener(onClick(() -> {
            Message m = list.getSelectedValue();
            if (m != null) {
                d.dispose();
                messages.scrollTo(m.id);
            }
        }));
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(420, 360));
        d.add(sp);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void addFriendDialog() {
        String u = JOptionPane.showInputDialog(this, "Nhập tên đăng nhập của người bạn muốn kết bạn:", "Thêm bạn",
                JOptionPane.PLAIN_MESSAGE);
        if (u == null || u.isBlank()) return;
        String name = Protocol.normalizeUsername(u);
        if (!users.containsKey(name) || name.equals(me)) {
            showToast("Không tìm thấy người dùng “" + u.trim() + "”", true);
            return;
        }
        showUserProfile(name);
    }

    private void addPeople() {
        Conversation c = current;
        if (c == null) return;
        if (Protocol.isGroup(c.id)) {
            GroupInfo g = groups.get(Protocol.groupIdOf(c.id));
            if (g == null) return;
            List<String> picked = pickUsers("Thêm thành viên vào “" + g.name + "”", null, g.members, List.of());
            if (picked != null && !picked.isEmpty()) conn.send(Protocol.GROUP_ADD, g.id, String.join(",", picked));
        } else if (Protocol.isPrivate(c.id)) {
            createGroupDialog(List.of(Protocol.peerOf(c.id, me)));
        }
    }

    private void createGroupDialog(List<String> preselected) {
        String[] name = {""};
        List<String> picked = pickUsers("Tạo nhóm", name, List.of(me), preselected);
        if (picked != null) conn.send(Protocol.GROUP_CREATE, name[0], String.join(",", picked));
    }

    /**
     * Hộp chọn nhiều người dùng (có ô tìm kiếm). Nếu groupName != null thì có thêm ô nhập tên nhóm.
     * Trả về danh sách đã chọn, hoặc null nếu huỷ.
     */
    private List<String> pickUsers(String title, String[] groupName, Collection<String> exclude, Collection<String> preselected) {
        JDialog d = dialog(title);
        JTextField nameField = new LoginFrame.HintField("Nhập tên nhóm…");
        JTextField filter = new LoginFrame.HintField("Tìm theo tên hoặc tên đăng nhập");
        Set<String> chosen = new LinkedHashSet<>(preselected);
        DefaultListModel<UserInfo> model = new DefaultListModel<>();
        JList<UserInfo> list = new JList<>(model);
        JLabel count = new JLabel();
        Runnable reload = () -> {
            String q = filter.getText().trim().toLowerCase(Locale.ROOT);
            model.clear();
            users.values().stream()
                    .filter(u -> !exclude.contains(u.username))
                    .filter(u -> q.isEmpty() || u.display.toLowerCase(Locale.ROOT).contains(q) || u.username.contains(q))
                    .sorted(Comparator.comparing((UserInfo u) -> !Protocol.FRIEND_YES.equals(u.friend))
                            .thenComparing(u -> u.display.toLowerCase(Locale.ROOT)))
                    .forEach(model::addElement);
            count.setText("Đã chọn " + chosen.size());
        };
        list.setCellRenderer((l, u, i, sel, focus) -> {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(Color.WHITE);
            row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            JLabel check = new JLabel(new CheckIcon(chosen.contains(u.username)));
            JLabel who = new JLabel("<html><b>" + esc(u.display) + "</b><br><span style='color:#7589A3'>@" + u.username
                    + (Protocol.FRIEND_YES.equals(u.friend) ? " · Bạn bè" : "") + "</span></html>",
                    Theme.avatar(u.display, u.username, 36, false, u.online), SwingConstants.LEFT);
            who.setIconTextGap(10);
            who.setFont(Theme.font(Font.PLAIN, 13));
            row.add(check, BorderLayout.WEST);
            row.add(who, BorderLayout.CENTER);
            return row;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i < 0) return;
                String u = model.get(i).username;
                if (!chosen.remove(u)) chosen.add(u);
                count.setText("Đã chọn " + chosen.size());
                list.repaint();
            }
        });
        filter.getDocument().addDocumentListener(onChange(reload));
        reload.run();

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(Color.WHITE);
        top.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        if (groupName != null) {
            top.add(nameField);
            top.add(Box.createVerticalStrut(10));
        }
        top.add(filter);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(420, 340));
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.DIVIDER));

        boolean[] ok = {false};
        JButton cancel = Theme.secondaryButton("Huỷ");
        cancel.addActionListener(e -> d.dispose());
        JButton done = Theme.primaryButton(groupName != null ? "Tạo nhóm" : "Xác nhận");
        done.addActionListener(e -> {
            if (groupName != null) {
                String n = nameField.getText().trim();
                if (!Protocol.isValidDisplayName(n)) {
                    showToast("Vui lòng nhập tên nhóm (tối đa 40 ký tự)", true);
                    nameField.requestFocusInWindow();
                    return;
                }
                groupName[0] = n;
            }
            ok[0] = true;
            d.dispose();
        });
        count.setFont(Theme.font(Font.PLAIN, 12.5f));
        count.setForeground(Theme.TEXT_2);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(cancel);
        btns.add(done);
        bottom.add(count, BorderLayout.WEST);
        bottom.add(btns, BorderLayout.EAST);

        d.add(top, BorderLayout.NORTH);
        d.add(sp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(done);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true); // modal: chờ người dùng đóng
        return ok[0] ? new ArrayList<>(chosen) : null;
    }

    private void forwardDialog(Message m) {
        JDialog d = dialog("Chuyển tiếp tin nhắn");
        DefaultListModel<Conversation> model = new DefaultListModel<>();
        List<Conversation> all = new ArrayList<>(convs.values());
        all.removeIf(c -> Protocol.isPrivate(c.id) && c.messages.isEmpty());
        all.sort(Comparator.comparingLong(Conversation::activity).reversed());
        for (UserInfo u : users.values()) {
            String conv = Protocol.privateConv(me, u.username);
            if (Protocol.FRIEND_YES.equals(u.friend) && all.stream().noneMatch(c -> c.id.equals(conv))) all.add(new Conversation(conv));
        }
        all.forEach(model::addElement);
        JList<Conversation> list = new JList<>(model);
        list.setCellRenderer(new ConvCell());
        list.setFixedCellHeight(64);
        JLabel what = new JLabel("<html><span style='color:#7589A3'>Nội dung:</span> " + esc(m.snippet()) + "</html>");
        what.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        what.setFont(Theme.font(Font.PLAIN, 13));
        JButton send = Theme.primaryButton("Chuyển tiếp");
        send.addActionListener(e -> {
            Conversation target = list.getSelectedValue();
            if (target == null) return;
            conn.send(Protocol.FORWARD, m.id, target.id);
            showToast("Đã chuyển tiếp tới " + title(target), false);
            d.dispose();
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Color.WHITE);
        bottom.add(send);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(400, 380));
        d.add(what, BorderLayout.NORTH);
        d.add(sp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void showConvInfo() {
        Conversation c = current;
        if (c == null) return;
        if (Protocol.isGroup(c.id)) showGroupInfo(Protocol.groupIdOf(c.id));
        else if (Protocol.isPrivate(c.id)) showUserProfile(Protocol.peerOf(c.id, me));
        else showToast("Chat chung: mọi người dùng MiniChat đều nhận được tin nhắn ở đây", false);
    }

    private void showUserProfile(String username) {
        UserInfo u = user(username);
        JDialog d = dialog(username.equals(me) ? "Hồ sơ của bạn" : "Thông tin tài khoản");
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));
        JLabel av = new JLabel(Theme.avatar(u.display, u.username, 88, false, u.online));
        JLabel nm = new JLabel(Theme.safe(u.display));
        nm.setFont(Theme.font(Font.BOLD, 19));
        nm.setForeground(Theme.TEXT);
        JLabel un = new JLabel("@" + u.username + " · " + Theme.lastSeen(u.online, u.lastSeen));
        un.setFont(Theme.font(Font.PLAIN, 13));
        un.setForeground(Theme.TEXT_2);
        for (JComponent c : new JComponent[]{av, nm, un}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(c);
            p.add(Box.createVerticalStrut(8));
        }
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setOpaque(false);
        if (username.equals(me)) {
            JButton rename = Theme.primaryButton("Đổi tên hiển thị");
            rename.addActionListener(e -> {
                String n = (String) JOptionPane.showInputDialog(d, "Tên hiển thị mới:", "Đổi tên", JOptionPane.PLAIN_MESSAGE,
                        null, null, u.display);
                if (n != null && Protocol.isValidDisplayName(n)) {
                    conn.send(Protocol.PROFILE, n.trim());
                    d.dispose();
                } else if (n != null) {
                    showToast("Tên hiển thị không hợp lệ (1-40 ký tự)", true);
                }
            });
            btns.add(rename);
        } else {
            JButton chat = Theme.primaryButton("Nhắn tin");
            chat.addActionListener(e -> {
                d.dispose();
                showSide("chats");
                select(ensureConv(Protocol.privateConv(me, username)));
            });
            btns.add(chat);
            switch (u.friend) {
                case Protocol.FRIEND_YES:
                    btns.add(button("Huỷ kết bạn", () -> {
                        int r = JOptionPane.showConfirmDialog(d, "Huỷ kết bạn với " + u.display + "?", "Huỷ kết bạn",
                                JOptionPane.OK_CANCEL_OPTION);
                        if (r == JOptionPane.OK_OPTION) {
                            conn.send(Protocol.FRIEND_REMOVE, username);
                            d.dispose();
                        }
                    }));
                    break;
                case Protocol.FRIEND_SENT:
                    btns.add(button("Thu hồi lời mời", () -> {
                        conn.send(Protocol.FRIEND_DECLINE, username);
                        d.dispose();
                    }));
                    break;
                case Protocol.FRIEND_RECEIVED:
                    btns.add(button("Đồng ý kết bạn", () -> {
                        conn.send(Protocol.FRIEND_ACCEPT, username);
                        d.dispose();
                    }));
                    break;
                default:
                    btns.add(button("Kết bạn", () -> {
                        conn.send(Protocol.FRIEND_REQUEST, username);
                        showToast("Đã gửi lời mời kết bạn tới " + u.display, false);
                        d.dispose();
                    }));
            }
        }
        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(Box.createVerticalStrut(10));
        p.add(btns);
        d.add(p);
        d.pack();
        d.setSize(Math.max(360, d.getWidth()), d.getHeight());
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void showGroupInfo(String gid) {
        GroupInfo g = groups.get(gid);
        if (g == null) return;
        JDialog d = dialog("Thông tin nhóm");
        boolean owner = g.owner.equals(me);
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBackground(Color.WHITE);
        head.setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));
        JLabel av = new JLabel(Theme.avatar(g.name, Protocol.groupConv(g.id), 72, true, false));
        JLabel nm = new JLabel(Theme.safe(g.name));
        nm.setFont(Theme.font(Font.BOLD, 18));
        JLabel sub = new JLabel(g.members.size() + " thành viên · Trưởng nhóm: " + name(g.owner));
        sub.setFont(Theme.font(Font.PLAIN, 12.5f));
        sub.setForeground(Theme.TEXT_2);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        actions.setOpaque(false);
        actions.add(button("Đổi tên", () -> {
            String n = (String) JOptionPane.showInputDialog(d, "Tên nhóm mới:", "Đổi tên nhóm", JOptionPane.PLAIN_MESSAGE,
                    null, null, g.name);
            if (n != null && Protocol.isValidDisplayName(n)) {
                conn.send(Protocol.GROUP_RENAME, g.id, n.trim());
                d.dispose();
            }
        }));
        actions.add(button("Thêm thành viên", () -> {
            d.dispose();
            List<String> picked = pickUsers("Thêm thành viên vào “" + g.name + "”", null, g.members, List.of());
            if (picked != null && !picked.isEmpty()) conn.send(Protocol.GROUP_ADD, g.id, String.join(",", picked));
        }));
        for (JComponent c : new JComponent[]{av, nm, sub, actions}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            head.add(c);
            head.add(Box.createVerticalStrut(8));
        }

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Color.WHITE);
        List<String> members = new ArrayList<>(g.members);
        members.sort(Comparator.comparing((String u) -> !u.equals(g.owner)).thenComparing(u -> name(u).toLowerCase(Locale.ROOT)));
        for (String u : members) {
            UserInfo info = user(u);
            JPanel row = personRow(info, (u.equals(g.owner) ? "Trưởng nhóm · " : "") + Theme.lastSeen(info.online, info.lastSeen));
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            right.setOpaque(false);
            if (!u.equals(me)) right.add(button("Nhắn tin", () -> {
                d.dispose();
                showSide("chats");
                select(ensureConv(Protocol.privateConv(me, u)));
            }));
            if (owner && !u.equals(me)) right.add(button("Mời ra", () -> {
                int r = JOptionPane.showConfirmDialog(d, "Mời " + info.display + " ra khỏi nhóm?", "Xoá thành viên",
                        JOptionPane.OK_CANCEL_OPTION);
                if (r == JOptionPane.OK_OPTION) {
                    conn.send(Protocol.GROUP_KICK, g.id, u);
                    d.dispose();
                }
            }));
            row.add(right, BorderLayout.EAST);
            list.add(row);
        }
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.DIVIDER));
        sp.setPreferredSize(new Dimension(440, Math.min(360, members.size() * 58 + 4)));
        sp.getVerticalScrollBar().setUnitIncrement(20);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setBackground(Color.WHITE);
        JButton leave = Theme.secondaryButton("Rời nhóm");
        leave.setForeground(Theme.BADGE);
        leave.addActionListener(e -> {
            d.dispose();
            leaveGroup(g.id);
        });
        bottom.add(leave);
        if (owner) {
            JButton disband = Theme.secondaryButton("Giải tán nhóm");
            disband.setForeground(Theme.BADGE);
            disband.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(d, "Giải tán nhóm “" + g.name + "”? Toàn bộ tin nhắn sẽ bị xoá.",
                        "Giải tán nhóm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r == JOptionPane.OK_OPTION) {
                    conn.send(Protocol.GROUP_DISBAND, g.id);
                    d.dispose();
                }
            });
            bottom.add(disband);
        }
        d.add(head, BorderLayout.NORTH);
        d.add(sp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void leaveGroup(String gid) {
        GroupInfo g = groups.get(gid);
        if (g == null) return;
        int r = JOptionPane.showConfirmDialog(this, "Rời khỏi nhóm “" + g.name + "”?", "Rời nhóm", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) conn.send(Protocol.GROUP_LEAVE, gid);
    }

    // =====================================================================
    //                           DANH BẠ
    // =====================================================================

    private void refreshContacts() {
        contactsBox.removeAll();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<UserInfo> all = new ArrayList<>(users.values());
        all.removeIf(u -> u.username.equals(me)
                || !(q.isEmpty() || u.display.toLowerCase(Locale.ROOT).contains(q) || u.username.contains(q)));
        all.sort(Comparator.comparing((UserInfo u) -> !u.online).thenComparing(u -> u.display.toLowerCase(Locale.ROOT)));

        List<UserInfo> received = filter(all, Protocol.FRIEND_RECEIVED);
        if (!received.isEmpty()) {
            section("Lời mời kết bạn (" + received.size() + ")");
            for (UserInfo u : received) {
                JPanel row = personRow(u, "Muốn kết bạn với bạn");
                row.add(buttons(button("Từ chối", () -> conn.send(Protocol.FRIEND_DECLINE, u.username)),
                        primary("Đồng ý", () -> conn.send(Protocol.FRIEND_ACCEPT, u.username))), BorderLayout.EAST);
                contactsBox.add(row);
            }
        }
        List<UserInfo> sent = filter(all, Protocol.FRIEND_SENT);
        if (!sent.isEmpty()) {
            section("Lời mời đã gửi (" + sent.size() + ")");
            for (UserInfo u : sent) {
                JPanel row = personRow(u, "Đang chờ đồng ý");
                row.add(buttons(button("Thu hồi", () -> conn.send(Protocol.FRIEND_DECLINE, u.username))), BorderLayout.EAST);
                contactsBox.add(row);
            }
        }
        List<UserInfo> friends = filter(all, Protocol.FRIEND_YES);
        section("Bạn bè (" + friends.size() + ")");
        if (friends.isEmpty()) hint("Chưa có bạn bè. Hãy kết bạn với mọi người ở mục Gợi ý bên dưới.");
        for (UserInfo u : friends) contactsBox.add(personRow(u, Theme.lastSeen(u.online, u.lastSeen)));

        List<GroupInfo> gs = new ArrayList<>(groups.values());
        gs.removeIf(g -> !(q.isEmpty() || g.name.toLowerCase(Locale.ROOT).contains(q)));
        section("Nhóm (" + gs.size() + ")");
        if (gs.isEmpty()) hint("Chưa có nhóm nào. Bấm biểu tượng tạo nhóm ở trên để tạo nhóm mới.");
        for (GroupInfo g : gs) {
            boolean member = g.members.contains(me);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            styleRow(row);
            JLabel l = new JLabel("<html><b>" + esc(g.name) + "</b><br><span style='color:#7589A3'>" + g.members.size()
                    + " thành viên" + (member ? " · Đã tham gia" : "") + "</span></html>",
                    Theme.avatar(g.name, Protocol.groupConv(g.id), 40, true, false), SwingConstants.LEFT);
            l.setIconTextGap(10);
            l.setFont(Theme.font(Font.PLAIN, 13.5f));
            row.add(l, BorderLayout.CENTER);
            if (member) {
                row.addMouseListener(onClick(() -> {
                    showSide("chats");
                    select(ensureConv(Protocol.groupConv(g.id)));
                }));
            } else {
                row.add(buttons(primary("Tham gia", () -> conn.send(Protocol.GROUP_JOIN, g.id))), BorderLayout.EAST);
            }
            contactsBox.add(row);
        }

        List<UserInfo> others = filter(all, Protocol.FRIEND_NONE);
        if (!others.isEmpty()) {
            section("Gợi ý kết bạn (" + others.size() + ")");
            for (UserInfo u : others) {
                JPanel row = personRow(u, "@" + u.username + " · " + Theme.lastSeen(u.online, u.lastSeen));
                row.add(buttons(button("Kết bạn", () -> conn.send(Protocol.FRIEND_REQUEST, u.username))), BorderLayout.EAST);
                contactsBox.add(row);
            }
        }
        contactsBox.revalidate();
        contactsBox.repaint();
    }

    private static List<UserInfo> filter(List<UserInfo> all, String status) {
        List<UserInfo> res = new ArrayList<>();
        for (UserInfo u : all) if (status.equals(u.friend)) res.add(u);
        return res;
    }

    private void section(String title) {
        JLabel l = new JLabel(title);
        l.setFont(Theme.font(Font.BOLD, 13.5f));
        l.setForeground(Theme.TEXT);
        l.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        contactsBox.add(l);
    }

    private void hint(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setFont(Theme.font(Font.PLAIN, 12.5f));
        l.setForeground(Theme.TEXT_2);
        l.setBorder(BorderFactory.createEmptyBorder(2, 16, 6, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        contactsBox.add(l);
    }

    private void styleRow(JPanel row) {
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(Theme.HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!row.contains(e.getPoint())) row.setBackground(Color.WHITE);
            }
        });
    }

    /** Một dòng người dùng: avatar + tên + dòng phụ; bấm để xem thông tin. */
    private JPanel personRow(UserInfo u, String sub) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        styleRow(row);
        JLabel l = new JLabel("<html><b>" + esc(u.display) + "</b><br><span style='color:#7589A3'>" + esc(sub) + "</span></html>",
                Theme.avatar(u.display, u.username, 40, false, u.online), SwingConstants.LEFT);
        l.setIconTextGap(10);
        l.setFont(Theme.font(Font.PLAIN, 13.5f));
        row.add(l, BorderLayout.CENTER);
        row.addMouseListener(onClick(() -> showUserProfile(u.username)));
        return row;
    }

    private static JPanel buttons(JButton... bs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        p.setOpaque(false);
        for (JButton b : bs) p.add(b);
        return p;
    }

    private static JButton button(String text, Runnable r) {
        JButton b = Theme.secondaryButton(text);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        b.addActionListener(e -> r.run());
        return b;
    }

    private static JButton primary(String text, Runnable r) {
        JButton b = Theme.primaryButton(text);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        b.addActionListener(e -> r.run());
        return b;
    }

    // =====================================================================
    //                           TIỆN ÍCH
    // =====================================================================

    private JDialog dialog(String title) {
        JDialog d = new JDialog(this, title, true);
        d.getContentPane().setBackground(Color.WHITE);
        d.setLayout(new BorderLayout());
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    private static JMenuItem menuItem(String text, Runnable r) {
        JMenuItem it = new JMenuItem(text);
        it.setFont(Theme.font(Font.PLAIN, 13.5f));
        it.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 16));
        it.addActionListener(e -> r.run());
        return it;
    }

    private static MouseAdapter onClick(Runnable r) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) r.run();
            }
        };
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Thông báo nhỏ ở cuối cửa sổ, tự ẩn sau vài giây. */
    void showToast(String text, boolean error) {
        toast.setText(Theme.safe(text));
        toast.setIcon(null);
        toast.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        toast.putClientProperty("bg", error ? new Color(0xD93025) : new Color(0x2B3440));
        toast.setUI(new javax.swing.plaf.basic.BasicLabelUI() {
            @Override
            public void paint(Graphics g0, JComponent c) {
                Graphics2D g = (Graphics2D) g0.create();
                Theme.hq(g);
                g.setColor((Color) c.getClientProperty("bg"));
                g.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 12, 12);
                g.dispose();
                super.paint(g0, c);
            }
        });
        Dimension d = toast.getPreferredSize();
        int w = Math.min(d.width, getLayeredPane().getWidth() - 40);
        toast.setBounds((getLayeredPane().getWidth() - w) / 2, getLayeredPane().getHeight() - d.height - 120, w, d.height);
        toast.setVisible(true);
        toastTimer.restart();
    }

    /** Tin nhắn mới không nằm trong hội thoại đang mở: rung taskbar + thông báo Windows nếu cửa sổ không được chọn. */
    private void notifyIncoming(Conversation c, Message m) {
        if (isActive()) return;
        String who = isGroupLike(c) ? name(m.from) + " · " + title(c) : name(m.from);
        notifyDesktop(who, m.snippet());
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                Taskbar.getTaskbar().requestWindowUserAttention(this);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void notifyDesktop(String title, String text) {
        if (isActive() || !SystemTray.isSupported()) return;
        try {
            if (trayIcon == null) {
                trayIcon = new TrayIcon(appIcon(16), "MiniChat");
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> {
                    setState(NORMAL);
                    toFront();
                });
                SystemTray.getSystemTray().add(trayIcon);
            }
            trayIcon.displayMessage(title, text, TrayIcon.MessageType.NONE);
        } catch (Exception ignored) {
            // hệ điều hành không hỗ trợ khay hệ thống
        }
    }

    private void logout() {
        int r = JOptionPane.showConfirmDialog(this, "Bạn có muốn đăng xuất khỏi MiniChat?", "Đăng xuất", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        closing = true;
        conn.close();
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        dispose();
        new LoginFrame().setVisible(true);
    }

    static Image appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Theme.hq(g);
        g.setColor(Theme.PRIMARY);
        g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        Theme.icon("chat", (int) (size * 0.75), Color.WHITE).paintIcon(null, g, size / 8, size / 8);
        g.dispose();
        return img;
    }

    /** Panel trong JScrollPane luôn rộng bằng khung nhìn (không cuộn ngang). */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return Math.max(20, r.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Biểu tượng ô chọn tròn trong hộp chọn thành viên. */
    private static final class CheckIcon implements Icon {
        private final boolean on;

        CheckIcon(boolean on) {
            this.on = on;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            if (on) {
                g.setColor(Theme.PRIMARY);
                g.fillOval(x, y, 20, 20);
                g.setColor(Color.WHITE);
                g.setStroke(new java.awt.BasicStroke(2.2f));
                g.drawPolyline(new int[]{x + 5, x + 9, x + 15}, new int[]{y + 10, y + 14, y + 6}, 3);
            } else {
                g.setColor(new Color(0xB8C2CC));
                g.setStroke(new java.awt.BasicStroke(1.5f));
                g.drawOval(x + 1, y + 1, 18, 18);
            }
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    /** Nút trên thanh điều hướng bên trái, có huy hiệu số. */
    private static final class NavButton extends JComponent {
        private final String kind;
        boolean selected;
        int badge;

        NavButton(String kind, String tooltip) {
            this.kind = kind;
            setToolTipText(tooltip);
            Dimension d = new Dimension(64, 58);
            setPreferredSize(d);
            setMaximumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            if (selected) {
                g.setColor(Theme.NAV_ACTIVE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            Theme.icon(kind, 28, Color.WHITE).paintIcon(this, g, (getWidth() - 28) / 2, (getHeight() - 28) / 2);
            if (badge > 0) {
                String s = badge > 99 ? "99+" : badge > 5 && "chat".equals(kind) ? "5+" : String.valueOf(badge);
                g.setFont(Theme.font(Font.BOLD, 10.5f));
                FontMetrics fm = g.getFontMetrics();
                int w = Math.max(17, fm.stringWidth(s) + 8);
                int x = getWidth() / 2 + 4, y = 9;
                g.setColor(Theme.BADGE);
                g.fillRoundRect(x, y, w, 17, 17, 17);
                g.setColor(Color.WHITE);
                g.drawString(s, x + (w - fm.stringWidth(s)) / 2f, y + 13);
            }
            g.dispose();
        }
    }

    /** Ô hiển thị 1 hội thoại trong danh sách bên trái. */
    private final class ConvCell extends JPanel implements ListCellRenderer<Conversation> {
        static int hoverIndex = -1;
        private final JLabel avatar = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel preview = new JLabel();
        private final JLabel time = new JLabel();
        private final Badge badge = new Badge();

        ConvCell() {
            super(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            title.setForeground(Theme.TEXT);
            time.setFont(Theme.font(Font.PLAIN, 12));
            time.setForeground(Theme.TEXT_2);
            time.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel texts = new JPanel(new GridLayout(2, 1, 0, 2));
            texts.setOpaque(false);
            texts.add(title);
            texts.add(preview);
            JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
            right.setOpaque(false);
            right.add(time);
            JPanel bw = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bw.setOpaque(false);
            bw.add(badge);
            right.add(bw);
            add(avatar, BorderLayout.WEST);
            add(texts, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Conversation> list, Conversation c, int index,
                                                      boolean selected, boolean focus) {
            boolean unread = c.unread > 0;
            avatar.setIcon(convAvatar(c, 48));
            title.setText(title(c));
            title.setFont(Theme.font(unread ? Font.BOLD : Font.PLAIN, 15));
            preview.setText(preview(c));
            boolean typing = !c.typing.isEmpty();
            preview.setFont(Theme.font(typing ? Font.ITALIC : unread ? Font.BOLD : Font.PLAIN, 13));
            preview.setForeground(typing ? Theme.PRIMARY : unread ? Theme.TEXT : Theme.TEXT_2);
            Message last = c.last();
            time.setText(last == null ? "" : Theme.shortTime(last.time));
            badge.count = c.unread;
            setBackground(selected ? Theme.SELECTED : index == hoverIndex ? Theme.HOVER : Color.WHITE);
            return this;
        }
    }

    /** Huy hiệu đỏ đếm tin chưa đọc. */
    private static final class Badge extends JComponent {
        int count;

        Badge() {
            setPreferredSize(new Dimension(28, 18));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (count <= 0) return;
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hq(g);
            String s = count > 5 ? "5+" : String.valueOf(count);
            g.setFont(Theme.font(Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int w = Math.max(18, fm.stringWidth(s) + 10);
            int x = getWidth() - w;
            g.setColor(Theme.BADGE);
            g.fillRoundRect(x, 0, w, 18, 18, 18);
            g.setColor(Color.WHITE);
            g.drawString(s, x + (w - fm.stringWidth(s)) / 2f, 13);
            g.dispose();
        }
    }

    // =====================================================================

    public static void main(String[] args) {
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /** Giao diện hệ điều hành + font hỗ trợ tiếng Việt và emoji. */
    static void installLookAndFeel() {
        // khử răng cưa chữ kiểu ClearType (LCD) cho nét sắc trên màn hình Windows; phải đặt trước khi khởi tạo AWT
        if (System.getProperty("awt.useSystemAAFontSettings") == null) System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        // dùng font Be Vietnam Pro cho mọi thành phần Swing (hộp thoại, menu, tooltip, ô nhập...)
        javax.swing.UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        for (Object key : new ArrayList<>(defaults.keySet())) {
            Object v = defaults.get(key);
            if (v instanceof Font) {
                Font f = (Font) v;
                UIManager.put(key, new javax.swing.plaf.FontUIResource(Theme.font(f.getStyle(), Math.max(13, f.getSize()))));
            }
        }
    }
}
