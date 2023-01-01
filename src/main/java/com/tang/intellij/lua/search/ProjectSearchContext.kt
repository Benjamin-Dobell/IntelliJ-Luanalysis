/*
 * Copyright (c) 2020
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

package com.tang.intellij.lua.search

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement


class ProjectSearchContext : SearchContext {
    override var project: Project

    constructor(project: Project): super() {
        this.project = project
    }

    constructor(sourceContext: SearchContext): super(sourceContext) {
        this.project = sourceContext.project
    }

    override val element: PsiElement? = null

    override val identifier: String = "project"

    override fun getProjectContext() = this
}
