package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

public interface PostingService {

    String name();

    DuplicateSearchResult findDuplicate(ExceptionEvent event);

    PostingResult createIssue(ExceptionEvent event);

    PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event);
}
