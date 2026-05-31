package com.aidea.aidea.domain.backlog.entity;

import java.io.Serializable;
import java.util.Objects;

public class StoryEpicId implements Serializable {

    private Long story;
    private Long epic;

    public StoryEpicId() {}

    public StoryEpicId(Long story, Long epic) {
        this.story = story;
        this.epic = epic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoryEpicId)) return false;
        StoryEpicId that = (StoryEpicId) o;
        return Objects.equals(story, that.story) && Objects.equals(epic, that.epic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(story, epic);
    }
}
