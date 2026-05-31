package com.aidea.aidea.domain.backlog.dto.response;

import java.util.List;

public record ReorderResponse(
        List<Long> orderedIds
) {}
