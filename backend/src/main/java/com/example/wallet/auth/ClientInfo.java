package com.example.wallet.auth;

import jakarta.servlet.http.HttpServletRequest;

/** Where a request came from, for refresh-token records and the audit log. */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 512;

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT) {
            ua = ua.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }
}
