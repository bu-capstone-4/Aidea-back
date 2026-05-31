package com.aidea.aidea.domain.backlog.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "story_epics")
@IdClass(StoryEpicId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryEpic {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id")
    private Epic epic;

    public static StoryEpic create(Story story, Epic epic) {
        StoryEpic se = new StoryEpic();
        se.story = story;
        se.epic = epic;
        return se;
    }
}
