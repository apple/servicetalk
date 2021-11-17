/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.ContextMapUtils;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

@Deprecated
final class AsyncContextMapToContextMapAdapter implements AsyncContextMap {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncContextMapToContextMapAdapter.class);

    private static final ConcurrentMap<AsyncContextMap.Key<?>, ContextMap.Key<?>> acmToCm = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ContextMap.Key<?>, AsyncContextMap.Key<?>> cmToAcm = new ConcurrentHashMap<>();

    private final ContextMap contextMap;

    AsyncContextMapToContextMapAdapter(final ContextMap contextMap) {
        this.contextMap = requireNonNull(contextMap);
    }

    static <T> void newKeyMapping(final AsyncContextMap.Key<T> acmKey, final ContextMap.Key<T> cmKey) {
        final ContextMap.Key<?> oldCmKey = acmToCm.putIfAbsent(acmKey, cmKey);
        final AsyncContextMap.Key<?> oldAcmKey = cmToAcm.putIfAbsent(cmKey, acmKey);
        if (oldCmKey != null || oldAcmKey != null) {
            LOGGER.warn("Tried to register a new mapping of AsyncContextMap.Key={} to {}, but other mappings already " +
                    "exist: AsyncContextMap.Key={} to {} and/or {} to AsyncContextMap.Key={}. Discarding request.",
                    acmKey, cmKey, acmKey, oldCmKey, cmKey, oldAcmKey);
        }
    }

    private static <T> ContextMap.Key<?> toCmKey(final AsyncContextMap.Key<T> acmKey) {
        requireNonNull(acmKey);
        final ContextMap.Key<?> cmKey = acmToCm.computeIfAbsent(acmKey, k -> {
            // Use Object.class because we can not infer the actual type of T
            final ContextMap.Key<Object> newCmKey = ContextMap.Key.newKey(k.toString(), Object.class);
            LOGGER.info("Created a {} mapping for unknown AsyncContextMap.Key={}. Migrate your keys to {} API. " +
                    "If migration is complicated, consider temporarily using " +
                    "AsyncContext.newKeyMapping(AsyncContextMap.Key, ContextMap.Key) to register " +
                    "AsyncContextMap.Key(s).", newCmKey, k, ContextMap.Key.class.getCanonicalName());
            return newCmKey;
        });
        LOGGER.trace("Using {} to lookup AsyncContextMap.Key={}", cmKey, acmKey);
        return cmKey;
    }

    @Nullable
    private static AsyncContextMap.Key<?> toAcmKey(@Nullable final ContextMap.Key<?> cmKey) {
        if (cmKey == null) {
            return null;
        }
        final AsyncContextMap.Key<?> acmKey = cmToAcm.computeIfAbsent(cmKey, k -> {
            // Use Object.class because we can not infer the actual type of T
            final Key<Object> newAcmKey = Key.newKey(k.toString());
            LOGGER.info("Created an AsyncContextMap.Key={} mapping for unknown {}. Migrate your keys to {} API. " +
                    "If migration is complicated, consider temporarily using " +
                    "AsyncContext.newKeyMapping(AsyncContextMap.Key, ContextMap.Key) to register " +
                    "AsyncContextMap.Key(s).", newAcmKey, k, ContextMap.Key.class.getCanonicalName());
            return newAcmKey;
        });
        LOGGER.trace("Using AsyncContextMap.Key={} to lookup {}", acmKey, cmKey);
        return acmKey;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final AsyncContextMap.Key<T> key) {
        return (T) contextMap.get(toCmKey(key));
    }

    @Override
    public boolean containsKey(final AsyncContextMap.Key<?> key) {
        return contextMap.containsKey(toCmKey(key));
    }

    @Override
    public boolean isEmpty() {
        return contextMap.isEmpty();
    }

    @Override
    public int size() {
        return contextMap.size();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T put(final AsyncContextMap.Key<T> key, @Nullable final T value) {
        final ContextMap.Key<?> cmKey = toCmKey(key);
        if (Object.class.equals(cmKey.type())) {
            // Auto-computed mapping, Key doesn't know the type, but expect T:
            return (T) contextMap.put((ContextMap.Key<Object>) cmKey, (Object) value);
        } else {
            // Manually provided mapping, type is known:
            return contextMap.put((ContextMap.Key<T>) cmKey, value);
        }
    }

    @Override
    public void putAll(final Map<AsyncContextMap.Key<?>, Object> map) {
        if (map.isEmpty()) {
            return;
        }
        // Concurrent implementations, like CopyOnWriteAsyncContextMap, expect to put all or retry an operation. Use
        // an intermediate map to preserve this behavior instead of invoking `put` multiple times.
        final Map<ContextMap.Key<?>, Object> cMap = new HashMap<>(map.size());
        for (Map.Entry<AsyncContextMap.Key<?>, Object> entry : map.entrySet()) {
            cMap.put(toCmKey(entry.getKey()), entry.getValue());
        }
        contextMap.putAll(cMap);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T remove(final AsyncContextMap.Key<T> key) {
        return (T) contextMap.remove(toCmKey(key));
    }

    @Override
    public boolean removeAll(final Iterable<AsyncContextMap.Key<?>> entries) {
        final List<ContextMap.Key<?>> cKeys = new ArrayList<>();
        for (AsyncContextMap.Key<?> key : entries) {
            cKeys.add(toCmKey(key));
        }
        return contextMap.removeAll(cKeys);
    }

    @Override
    public void clear() {
        contextMap.clear();
    }

    @Nullable
    @Override
    public AsyncContextMap.Key<?> forEach(final BiPredicate<AsyncContextMap.Key<?>, Object> consumer) {
        return toAcmKey(contextMap.forEach((cmKey, value) -> consumer.test(toAcmKey(cmKey), value)));
    }

    @Override
    public AsyncContextMap copy() {
        return new AsyncContextMapToContextMapAdapter(contextMap.copy());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '-' + ContextMapUtils.toString(contextMap);
    }
}
