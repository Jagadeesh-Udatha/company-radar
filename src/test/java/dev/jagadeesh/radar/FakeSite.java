package dev.jagadeesh.radar;

import dev.jagadeesh.radar.crawl.PageFetcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory website for crawler tests. Records every URL fetched. */
public class FakeSite implements PageFetcher {

    private final Map<String, Response> pages = new HashMap<>();
    public final List<String> fetched = new ArrayList<>();

    public FakeSite html(String url, String body) {
        pages.put(url, new Response(200, "text/html; charset=utf-8", body, url));
        return this;
    }

    public FakeSite text(String url, int status, String body) {
        pages.put(url, new Response(status, "text/plain", body, url));
        return this;
    }

    @Override
    public synchronized Response fetch(String url) throws IOException {
        fetched.add(url);
        Response r = pages.get(url);
        if (r == null) {
            return new Response(404, "text/html", "", url);
        }
        return r;
    }

    /** A small fintech company site with the traps a crawler must handle. */
    public static FakeSite acme() {
        return new FakeSite()
                .text("https://acme.example/robots.txt", 200, """
                        User-agent: *
                        Disallow: /private
                        """)
                .html("https://acme.example/", """
                        <html><head><title>Acme Pay | UPI payments for small businesses</title>
                        <meta name="description" content="Acme Pay helps small businesses accept UPI and card payments.">
                        </head><body>
                        <p>Payments made simple. Lending and credit for merchants.</p>
                        <a href="/about">About</a>
                        <a href="/about#team">Team</a>
                        <a href="/?utm_source=newsletter">Home</a>
                        <a href="/careers">Careers</a>
                        <a href="/private/admin">Admin</a>
                        <a href="/mirror">Mirror</a>
                        <a href="/brochure.pdf">Brochure</a>
                        <a href="https://other.example/partner">Partner</a>
                        <a href="https://www.linkedin.com/company/acme-pay">LinkedIn</a>
                        </body></html>""")
                .html("https://acme.example/about", """
                        <html><head><title>About Acme Pay</title></head><body>
                        <p>We build payments and banking infrastructure.</p><a href="/team">Team</a></body></html>""")
                .html("https://acme.example/mirror", """
                        <html><head><title>About Acme Pay</title></head><body>
                        <p>We build payments and banking infrastructure.</p><a href="/team">Team</a></body></html>""")
                .html("https://acme.example/careers", """
                        <html><head><title>Careers</title></head><body><h1>We're hiring Java engineers</h1></body></html>""")
                .html("https://acme.example/team", """
                        <html><head><title>Team</title></head><body><a href="/deep">Deep</a></body></html>""")
                .html("https://acme.example/deep", "<html><body>deep</body></html>");
    }
}
