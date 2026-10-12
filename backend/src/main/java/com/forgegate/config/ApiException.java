package com.forgegate.config;
public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static ApiException bad(String m) { return new ApiException(400,m); }
    public static ApiException forbidden(String m) { return new ApiException(403,m); }
    public static ApiException missing(String m) { return new ApiException(404,m); }
    public static ApiException conflict(String m) { return new ApiException(409,m); }
    public static ApiException unavailable(String m) { return new ApiException(503,m); }
}
