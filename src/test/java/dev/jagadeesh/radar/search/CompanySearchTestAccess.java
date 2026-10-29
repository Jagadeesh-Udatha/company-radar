package dev.jagadeesh.radar.search;

/** Exposes package-private helpers of CompanySearch to tests in other packages. */
public final class CompanySearchTestAccess {

    private CompanySearchTestAccess() {
    }

    public static String highlight(String description, String text) {
        return CompanySearch.highlight(description, text);
    }
}
