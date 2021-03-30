/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.plugins;

import org.gradle.api.Buildable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.io.File;
import java.util.List;

public abstract class GroovyClasspath extends LazilyInitializedFileCollection {

    protected final Iterable<File> classpath;

    protected GroovyClasspath(Iterable<File> classpath) {
        this.classpath = classpath;
    }

    protected void addGroovyDependency(Project project, String groovyDependencyNotation, List<Dependency> dependencies, String otherDependency) {
        // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
        dependencies.add(project.getDependencies().create(groovyDependencyNotation.replace(":groovy:", ":" + otherDependency + ":")));
    }

    // let's override this so that delegate isn't created at autowiring time (which would mean on every build)
    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (classpath instanceof Buildable) {
            context.add(classpath);
        }
    }
}
