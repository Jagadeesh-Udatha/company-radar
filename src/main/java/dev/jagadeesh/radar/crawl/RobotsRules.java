package dev.jagadeesh.radar.crawl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal robots.txt support: the group for our user agent (or "*" if there is none), Allow/Disallow with
 * longest-match-wins, "*" and "$" wildcards, and Crawl-delay.
 */
public final class RobotsRules {

    private record Rule(String pattern, boolean allow) {
    }

    private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), null);

    private final List<Rule> rules;
    private final Duration crawlDelay;

    private RobotsRules(List<Rule> rules, Duration crawlDelay) {
        this.rules = rules;
        this.crawlDelay = crawlDelay;
    }

    public static RobotsRules allowAll() {
        return ALLOW_ALL;
    }

    public static RobotsRules parse(String robotsTxt, String userAgent) {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return ALLOW_ALL;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        Duration specificDelay = null;
        Duration wildcardDelay = null;
        boolean foundSpecific = false;

        List<String> groupAgents = new ArrayList<>();
        boolean inRules = false;
        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.contains("#") ? rawLine.substring(0, rawLine.indexOf('#')) : rawLine;
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (inRules) {          // a new group starts
                    groupAgents.clear();
                    inRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
                continue;
            }
            inRules = true;
            boolean forUs = groupAgents.stream().anyMatch(a -> !a.equals("*") && agent.contains(a));
            boolean forAll = groupAgents.contains("*");
            foundSpecific |= forUs;
            switch (key) {
                case "allow", "disallow" -> {
                    if (value.isEmpty()) {
                        continue;       // "Disallow:" with no path allows everything
                    }
                    Rule rule = new Rule(value, key.equals("allow"));
                    if (forUs) specific.add(rule);
                    if (forAll) wildcard.add(rule);
                }
                case "crawl-delay" -> {
                    Duration d = parseDelay(value);
                    if (forUs) specificDelay = d;
                    if (forAll) wildcardDelay = d;
                }
                default -> { }
            }
        }
        return foundSpecific ? new RobotsRules(specific, specificDelay) : new RobotsRules(wildcard, wildcardDelay);
    }

    /** @param pathAndQuery e.g. "/careers?team=eng" */
    public boolean isAllowed(String pathAndQuery) {
        Rule best = null;
        for (Rule r : rules) {
            if (matches(r.pattern, pathAndQuery)
                    && (best == null || r.pattern.length() > best.pattern.length()
                    || (r.pattern.length() == best.pattern.length() && r.allow))) {
                best = r;
            }
        }
        return best == null || best.allow;
    }

    public Optional<Duration> crawlDelay() {
        return Optional.ofNullable(crawlDelay);
    }

    private static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder regex = new StringBuilder("^");
        for (char c : p.toCharArray()) {
            regex.append(c == '*' ? ".*" : java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        regex.append(anchored ? "$" : ".*");
        return path.matches(regex.toString());
    }

    private static Duration parseDelay(String value) {
        try {
            return Duration.ofMillis((long) (Double.parseDouble(value) * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
