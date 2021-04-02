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

package org.gradle.normalization;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configuration of source file normalization.  Source files will be normalized to
 * ignore differences in line endings during input fingerprinting.
 *
 * @since 7.1
 */
@HasInternalProtocol
@Incubating
public interface SourceFileNormalization {
    /**
     * Configures a file extension that Gradle should always consider to be a source file.
     *
     * For example, to always consider `py` files:
     * <pre>
     *     includeExtension("py")
     * </pre>
     *
     * Multiple calls to this method adds multiple extensions to be considered source files.
     *
     * @param extension The file extension that should be considered a source file.
     */
    void includeExtension(String extension);
}
