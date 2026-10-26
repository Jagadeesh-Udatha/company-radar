package dev.jagadeesh.radar.crawl;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;

public class JsoupPageFetcher implements PageFetcher {

    private final String userAgent;
    private final int timeoutMillis;
    private final int maxBodyBytes;

    public JsoupPageFetcher(String userAgent, int timeoutMillis, int maxBodyBytes) {
        this.userAgent = userAgent;
        this.timeoutMillis = timeoutMillis;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public Response fetch(String url) throws IOException {
        Connection.Response res = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMillis)
                .maxBodySize(maxBodyBytes)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .execute();
        return new Response(res.statusCode(), res.contentType(), res.body(), res.url().toString());
    }
}
