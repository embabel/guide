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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for GuideProperties path resolution (resolvePath, projectRootPath).
 * Tests tilde expansion, absolute paths, and relative-to-cwd resolution.
 */
class GuidePropertiesPathResolutionTest {

    private val home = File.separator + "home" + File.separator + "user"
    private val cwd = File.separator + "work" + File.separator + "guide"

    @Test
    fun `resolvePath expands tilde to user home`() {
        val result = GuideProperties.resolvePath("~", home, cwd)
        assertEquals(home, result)
    }

    @Test
    fun `resolvePath expands tilde slash path to user home plus path`() {
        val result = GuideProperties.resolvePath("~/github/jmjava", home, cwd)
        assertEquals(
            File(home, "github/jmjava").normalize().absolutePath,
            result
        )
    }

    @Test
    fun `resolvePath leaves absolute path unchanged apart from normalization`() {
        val absolute = File.separator + "abs" + File.separator + "path" + File.separator + ".." + File.separator + "repo"
        val result = GuideProperties.resolvePath(absolute, home, cwd)
        assertEquals(File(absolute).normalize().absolutePath, result)
    }

    @Test
    fun `resolvePath resolves relative path against user dir`() {
        val result = GuideProperties.resolvePath("./embabel-projects", home, cwd)
        assertEquals(
            File(cwd, "embabel-projects").normalize().absolutePath,
            result
        )
    }

    @Test
    fun `resolvePath returns null when path is null`() {
        val result = GuideProperties.resolvePath(null, home, cwd)
        assertEquals(null, result)
    }

    @Test
    fun `resolvePath returns blank when path is blank`() {
        val result = GuideProperties.resolvePath("   ", home, cwd)
        assertEquals("   ", result)
    }
}
