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
package com.embabel.guide

import org.drivine.autoconfigure.EnableDrivine
import org.drivine.autoconfigure.EnableDrivineTestConfig
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.PropertySource

/**
 * Test configuration for Drivine4j using the new annotation-based approach (0.0.3-SNAPSHOT).
 *
 * Uses:
 * - @EnableDrivine: Enables core Drivine infrastructure beans
 * - @EnableDrivineTestConfig: Auto-configures test datasources with smart Testcontainers integration
 *
 * The @EnableDrivineTestConfig annotation provides:
 * - Local development mode: Set USE_LOCAL_NEO4J=true to use local Neo4j (fast, inspectable)
 * - CI mode (default): Automatically starts Neo4j Testcontainer (isolated, no setup needed)
 *
 * Datasource configuration is read from application-test.yml.
 */
@Configuration
@EnableDrivine
@EnableDrivineTestConfig
@ComponentScan(basePackages = ["org.drivine", "com.embabel"])
@PropertySource("classpath:application.yml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
class TestAppContext

