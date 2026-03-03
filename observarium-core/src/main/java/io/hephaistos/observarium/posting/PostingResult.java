package io.hephaistos.observarium.posting;

public record PostingResult(
    boolean success,
    String externalIssueId,
    String url,
    String errorMessage
) {

    public static PostingResult success(String externalIssueId, String url) {
        return new PostingResult(true, externalIssueId, url, null);
    }

    public static PostingResult failure(String errorMessage) {
        return new PostingResult(false, null, null, errorMessage);
    }
}
