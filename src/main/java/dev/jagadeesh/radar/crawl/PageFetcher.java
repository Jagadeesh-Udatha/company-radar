package dev.jagadeesh.radar.crawl;

import java.io.IOException;

/** HTTP access, behind an interface so the crawler can be tested against an in-memory site. */
public interface PageFetcher {

    record Response(int status, String contentType, String body, String finalUrl) {

        public boolean isHtml() {
            return status == 200 && contentType != null && contentType.toLowerCase().contains("text/html");
        }
    }

    Response fetch(String url) throws IOException;
}
