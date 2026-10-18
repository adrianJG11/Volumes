package com.adrian.volumes.book;

import java.net.URI;

/** Covers come only from Open Library or Apple's iTunes artwork over HTTPS; nothing else is stored. */
public final class CoverUrls {

    private CoverUrls() {}

    public static boolean allowed(String url) {
        if (url == null || url.length() > 1000) return false;
        try {
            var uri = new URI(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1 && host != null
                && (host.equals("covers.openlibrary.org") || host.matches("is[0-9]+-ssl\\.mzstatic\\.com"));
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }

    public static String openLibraryCover(long coverId) {
        return "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg?default=false";
    }
}
