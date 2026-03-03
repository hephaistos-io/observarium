package io.hephaistos.observarium.posting;

public record DuplicateSearchResult(
    boolean found,
    String externalIssueId,
    String url
) {

    public static DuplicateSearchResult notFound() {
        return new DuplicateSearchResult(false, null, null);
    }

    public static DuplicateSearchResult found(String externalIssueId, String url) {
        return new DuplicateSearchResult(true, externalIssueId, url);
    }
}
