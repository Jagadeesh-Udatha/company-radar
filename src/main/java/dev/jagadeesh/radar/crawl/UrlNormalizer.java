package dev.jagadeesh.radar.crawl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the many spellings of one page into one key, so the crawler visits it once:
 * case-insensitive scheme/host, no default port, no fragment, no tracking parameters, no trailing slash.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING = Set.of("gclid", "fbclid", "mc_cid", "mc_eid", "ref");
    private static final Set<String> SKIPPED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "svg", "webp", "zip", "gz", "mp4", "mp3", "css", "js", "ico", "xml");

    private UrlNormalizer() {
    }

    /** Empty for anything that isn't an http(s) page worth fetching. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(raw.trim()).normalize();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);

        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (SKIPPED_EXTENSIONS.contains(ext)) {
            return Optional.empty();
        }

        String query = uri.getRawQuery() == null ? null : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(p -> !p.isBlank())
                .filter(p -> {
                    String name = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !TRACKING.contains(name);
                })
                .sorted()
                .collect(Collectors.joining("&"));

        StringBuilder out = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) {
            out.append(':').append(port);
        }
        out.append(path);
        if (query != null && !query.isEmpty()) {
            out.append('?').append(query);
        }
        return Optional.of(out.toString());
    }

    /** example.com and www.example.com count as the same site. */
    public static String siteKey(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }
}
