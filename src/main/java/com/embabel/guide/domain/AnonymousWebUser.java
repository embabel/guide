/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.guide.domain;

import org.neo4j.ogm.annotation.NodeEntity;

import java.util.UUID;

/**
 * Represents the anonymous web user - a singleton user for non-authenticated web sessions.
 * There should only be one instance of this user in the database.
 */
@NodeEntity(label = "Anonymous")
public class AnonymousWebUser extends WebUser {

    // No-arg constructor for Neo4j OGM
    public AnonymousWebUser() {
        super();
    }

    public AnonymousWebUser(String id) {
        super(id, "Friend", "anonymous", null, null, null);
    }

    public static AnonymousWebUser create() {
        return new AnonymousWebUser(UUID.randomUUID().toString());
    }
}
