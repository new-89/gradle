/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.model;

import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.WorkerLimits;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A factory for creating {@link LoadingCache} instances of various types.
 * <p>
 * Caches created by this factory are intended for general use wherever concurrent
 * in-memory caching is required.
 */
@ServiceScope(Scope.BuildSession.class)
public class LoadingCacheFactory {

    private final WorkerLimits workerLimits;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    @Inject
    public LoadingCacheFactory(
        WorkerLimits workerLimits,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.workerLimits = workerLimits;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    /**
     * Create an unbounded cache intended to be accessed concurrently.
     * <p>
     * This cache is optimized for read-heavy workloads and does not permit null values.
     *
     * @param loader the function to compute values that are not present in the cache
     */
    public <K, V> LoadingCache<K, V> create(Function<K, V> loader) {
        return new DefaultLoadingCache<>(workerLimits.getMaxWorkerCount(), loader);
    }

    /**
     * Create a cache similar to that created by {@link #create(Function)}, but which bypasses
     * the equals and hashcode methods of the key and intead compares object identity. Caches
     * created by this method should be used only with keys that are known to be interned.
     * <p>
     * This is likely a more performant alternative to {@code Collections.synchronizedMap(new IdentityHashMap<>())}
     * in concurrent scenarios.
     */
    public <K, V> LoadingCache<K, V> createIdentityCache(Function<K, V> loader) {
        return new IdentityLoadingCache<>(loader, workerLimits.getMaxWorkerCount());
    }

    /**
     * Create a cache similar to that created by {@link #create(Function)}, but which wraps all
     * value loading in a {@link CalculatedValue}, so that the value loader may safely acquire
     * project locks.
     */
    public <K, V> LoadingCache<K, V> createCalculatedValueCache(DisplayName type, Function<K, V> loader) {
        return new CalculatedValueCache<>(
            type,
            loader,
            calculatedValueContainerFactory,
            workerLimits.getMaxWorkerCount()
        );
    }

    // TODO: Back this cache using a Caffeine cache so we can record statistics
    // and monitor cache performance across all caches in the build.
    private static class DefaultLoadingCache<K, V> implements LoadingCache<K, V> {
        private final Map<K, V> delegate;
        private final Function<K, V> loader;

        public DefaultLoadingCache(int maxConcurrency, Function<K, V> loader) {
            // Use at least as many bins as estimated threads
            this.delegate = new ConcurrentHashMap<>(maxConcurrency);
            this.loader = preventNullValues(loader);
        }

        private static <K, V> Function<K, V> preventNullValues(Function<K, V> factory) {
            return key -> {
                V value = factory.apply(key);
                if (value == null) {
                    throw new IllegalArgumentException("cached value cannot be null");
                }
                return value;
            };
        }

        @Override
        public V get(K key) {
            // Try to load the value without locking
            // See https://bugs.openjdk.org/browse/JDK-8161372
            V value = delegate.get(key);
            if (value != null) {
                return value;
            }

            // Lock and compute the value if it is not present
            return delegate.computeIfAbsent(key, loader);
        }

        @Override
        public void invalidate() {
            delegate.clear();
        }
    }

    private static class IdentityLoadingCache<K, V> implements LoadingCache<K, V> {
        private final LoadingCache<IdentityKey<K>, V> delegate;

        public IdentityLoadingCache(Function<K, V> loader, int maxConcurrency) {
            this.delegate = new DefaultLoadingCache<>(maxConcurrency, key ->
                loader.apply(key.value)
            );
        }

        @Override
        public V get(K key) {
            return delegate.get(new IdentityKey<>(key));
        }

        @Override
        public void invalidate() {
            delegate.invalidate();
        }
    }

    private static class IdentityKey<T> {
        private final T value;

        private IdentityKey(T value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass", "EqualsUnsafeCast"})
        public boolean equals(Object obj) {
            return ((IdentityKey<?>) obj).value == value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private static class CalculatedValueCache<K, V> implements LoadingCache<K, V> {
        private final LoadingCache<K, CalculatedValue<V>> delegate;

        public CalculatedValueCache(
            DisplayName type,
            Function<K, V> loader,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            int maxConcurrency
        ) {
            this.delegate = new DefaultLoadingCache<>(maxConcurrency, key ->
                calculatedValueContainerFactory.create(
                    Describables.of(key, type),
                    context -> loader.apply(key)
                )
            );
        }

        @Override
        public V get(K key) {
            CalculatedValue<V> calculatedValue = delegate.get(key);
            // Calculate the value after adding the entry to the cache, so that
            // the value container can safely handle synchronization.
            calculatedValue.finalizeIfNotAlready();
            return calculatedValue.get();
        }

        @Override
        public void invalidate() {
            delegate.invalidate();
        }
    }

}
