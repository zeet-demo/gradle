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

package org.gradle.normalization.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.GradleException;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class EvaluatableFilter<T> {
    private T value;
    private final Supplier<T> valueSupplier;
    private final ImmutableSet.Builder<String> builder;

    public EvaluatableFilter(Function<ImmutableSet<String>, T> initializer, T emptyValue) {
        this.builder = ImmutableSet.builder();
        // if there are configured ignores, use the initializer to create the value, otherwise return emptyValue
        this.valueSupplier = () -> Optional.of(builder.build())
            .filter(ignores -> !ignores.isEmpty())
            .map(initializer)
            .orElse(emptyValue);
    }

    public T evaluate() {
        if (value == null) {
            value = valueSupplier.get();
        }
        return value;
    }

    private void checkNotEvaluated() {
        if (value != null) {
            throw new GradleException("Cannot configure runtime classpath normalization after execution started.");
        }
    }

    public void addToFilter(String ignore) {
        checkNotEvaluated();
        builder.add(ignore);
    }
}
