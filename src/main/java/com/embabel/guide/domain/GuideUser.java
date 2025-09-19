package com.embabel.guide.domain;

import com.embabel.agent.identity.User;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
public class GuideUser {

    @Id
    private String id;

    public GuideUser(User user) {
        this.id = user.getId();
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "GuideUser{" +
                "id='" + id + '\'' +
                '}';
    }
}
