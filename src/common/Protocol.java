package common;

/**
 * Giao thức dạng text, mỗi lệnh là 1 dòng (UTF-8).
 *
 * Client -> Server:
 *   LOGIN <ten>
 *   CREATE <nhom>              tạo nhóm (người tạo tự vào nhóm)
 *   JOIN <nhom>                tham gia nhóm
 *   LEAVE <nhom>               rời nhóm
 *   MSG_ALL <noi dung>         chat chung (tất cả user)
 *   MSG_GROUP <nhom> <noi dung>
 *   MSG_PRIVATE <user> <noi dung>
 *   QUIT
 *
 * Server -> Client:
 *   LOGIN_OK <ten> | LOGIN_FAIL <ly do>
 *   USERS u1,u2,...
 *   GROUPS g1:n1,g2:n2,...
 *   JOINED <nhom> | LEFT <nhom>
 *   ALL <tu> <noi dung>
 *   GROUP <nhom> <tu> <noi dung>
 *   PRIVATE <tu> <den> <noi dung>
 *   INFO <noi dung> | ERROR <noi dung>
 */
public final class Protocol {
    private Protocol() {}

    // Client -> Server
    public static final String LOGIN = "LOGIN";
    public static final String CREATE = "CREATE";
    public static final String JOIN = "JOIN";
    public static final String LEAVE = "LEAVE";
    public static final String MSG_ALL = "MSG_ALL";
    public static final String MSG_GROUP = "MSG_GROUP";
    public static final String MSG_PRIVATE = "MSG_PRIVATE";
    public static final String QUIT = "QUIT";

    // Server -> Client
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String USERS = "USERS";
    public static final String GROUPS = "GROUPS";
    public static final String JOINED = "JOINED";
    public static final String LEFT = "LEFT";
    public static final String ALL = "ALL";
    public static final String GROUP = "GROUP";
    public static final String PRIVATE = "PRIVATE";
    public static final String INFO = "INFO";
    public static final String ERROR = "ERROR";

    /** Tên user / nhóm: chữ, số, _ hoặc -, tối đa 20 ký tự, không khoảng trắng. */
    public static boolean isValidName(String s) {
        return s != null && s.matches("[\\p{L}\\p{N}_-]{1,20}");
    }
}
