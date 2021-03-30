/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.GroovyClasspath;
import org.gradle.api.internal.plugins.GroovyJarFile;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides information related to the Groovy runtime(s) used in a project. Added by the
 * {@link org.gradle.api.plugins.GroovyBasePlugin} as a project extension named {@code groovyRuntime}.
 *
 * <p>Example usage:
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'groovy'
 *     }
 *
 *     repositories {
 *         mavenCentral()
 *     }
 *
 *     dependencies {
 *         implementation "org.codehaus.groovy:groovy-all:2.1.2"
 *     }
 *
 *     def groovyClasspath = groovyRuntime.inferGroovyClasspath(configurations.compileClasspath)
 *     // The returned class path can be used to configure the 'groovyClasspath' property of tasks
 *     // such as 'GroovyCompile' or 'Groovydoc', or to execute these and other Groovy tools directly.
 * </pre>
 */
public class GroovyRuntime {
    private static final VersionNumber GROOVY_VERSION_WITH_SEPARATE_ANT = VersionNumber.parse("2.0");
    private static final VersionNumber GROOVY_VERSION_REQUIRING_TEMPLATES = VersionNumber.parse("2.5");
    private final Project project;

    public GroovyRuntime(Project project) {
        this.project = project;
    }

    /**
     * Searches the specified class path for Groovy Jars ({@code groovy(-indy)}, {@code groovy-all(-indy)}) and returns a corresponding class path for executing Groovy tools such as the Groovy
     * compiler and Groovydoc tool. The tool versions will match those of the Groovy Jars found. If no Groovy Jars are found on the specified class path, a class path with the contents of the {@code
     * groovy} configuration will be returned.
     *
     * <p>The returned class path may be empty, or may fail to resolve when asked for its contents.
     *
     * @param classpath a class path containing Groovy Jars
     * @return a corresponding class path for executing Groovy tools such as the Groovy compiler and Groovydoc tool
     */
    public FileCollection inferGroovyClasspath(final Iterable<File> classpath) {
        // alternatively, we could return project.getLayout().files(Runnable)
        // would differ in at least the following ways: 1. live 2. no autowiring
        return new GroovyClasspath(classpath) {
            @Override
            public String getDisplayName() {
                return "Groovy runtime classpath";
            }

            @Override
            public FileCollection createDelegate() {
                GroovyJarFile groovyJar = GroovyJarFile.locate(classpath);
                VersionNumber groovyVersion = groovyJar.getVersion();
                if (groovyJar.isGroovyAll() || groovyVersion.getMajor() == 3) {
                    return project.getLayout().files(groovyJar.getFile());
                }

                List<Dependency> dependencies = new ArrayList<>();
                String notation = groovyJar.getDependencyNotation();
                dependencies.add(project.getDependencies().create(notation));
                if (groovyVersion.compareTo(GROOVY_VERSION_WITH_SEPARATE_ANT) >= 0) {
                    // add groovy-ant to bring in Groovydoc for Groovy 2.0+
                    addGroovyDependency(project, notation, dependencies, "groovy-ant");
                }
                if (groovyVersion.compareTo(GROOVY_VERSION_REQUIRING_TEMPLATES) >= 0) {
                    // add groovy-templates for Groovy 2.5+
                    addGroovyDependency(project, notation, dependencies, "groovy-templates");
                }
                return project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[0]));
            }
        };
    }

}
