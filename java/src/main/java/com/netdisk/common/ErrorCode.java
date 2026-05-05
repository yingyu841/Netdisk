package com.netdisk.common;

public final class ErrorCode {
    private ErrorCode() {}

    public static final int OK = 0;
    public static final int UNAUTHORIZED = 1001001;
    public static final int FORBIDDEN = 1001002;
    public static final int NOT_FOUND = 1002001;
    public static final int CONFLICT = 1003001;
    public static final int INVALID_PARAM = 1004001;
    public static final int SESSION_EXPIRED = 1004002;
    public static final int VERIFICATION_WRONG = 1004004;
    public static final int TOO_MANY_REQUESTS = 1005001;
    public static final int SEND_FAILED = 1006001;
    public static final int INTERNAL = 1009001;
}
