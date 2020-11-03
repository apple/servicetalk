/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import javax.annotation.Nullable;

import static java.util.Collections.addAll;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.SIZED;

final class HttpQuery implements Iterable<Map.Entry<String, String>> {

    private static final int DEFAULT_LIST_SIZE = 2;

    private final Map<String, List<String>> params;
    private boolean dirty;

    /**
     * Create a new instance.
     *
     * @param params Map of query parameters.
     */
    HttpQuery(final Map<String, List<String>> params) {
        this.params = requireNonNull(params);
    }

    @Nullable
    public String get(final String key) {
        final List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public Iterator<String> valuesIterator(final String key) {
        final List<String> values = params.get(key);
        if (values == null) {
            return emptyIterator();
        }
        return new ValuesIterator(values.iterator(), () -> {
            if (values.isEmpty()) {
                params.remove(key);
            }
            dirty = true;
        });
    }

    public Iterable<String> values(final String key) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return valuesIterator(key);
            }

            @Override
            public Spliterator<String> spliterator() {
                final List<String> values = params.get(key);
                return Spliterators.spliterator(iterator(), values == null ? 0 : values.size(), SIZED);
            }
        };
    }

    public Set<String> keys() {
        return unmodifiableSet(params.keySet());
    }

    public HttpQuery add(final String key, final String value) {
        validateQueryParam(key, value);
        getValues(key).add(value);
        dirty = true;
        return this;
    }

    public HttpQuery add(final String key, final Iterable<String> values) {
        final List<String> paramValues = getValues(key);
        for (final String value : values) {
            paramValues.add(value);
        }
        dirty = true;
        return this;
    }

    public HttpQuery add(final String key, final String... values) {
        final List<String> paramValues = getValues(key);
        addAll(paramValues, values);
        dirty = true;
        return this;
    }

    public HttpQuery set(final String key, final String value) {
        validateQueryParam(key, value);
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        list.add(value);
        dirty = true;
        params.put(key, list);
        return this;
    }

    public HttpQuery set(final String key, final Iterable<String> values) {
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        for (final String value : values) {
            dirty |= list.add(value);
        }
        params.put(key, list);
        return this;
    }

    public HttpQuery set(final String key, final String... values) {
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        dirty |= addAll(list, values);
        params.put(key, list);
        return this;
    }

    public boolean contains(final String key, final String value) {
        final Iterator<String> values = valuesIterator(key);
        while (values.hasNext()) {
            if (value.equals(values.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean remove(final String key) {
        final List<String> removedValues = params.remove(key);
        boolean removed = removedValues != null && !removedValues.isEmpty();
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    public boolean remove(final String key, final String value) {
        final Iterator<String> values = valuesIterator(key);
        while (values.hasNext()) {
            if (value.equals(values.next())) {
                values.remove();
                dirty = true;
                return true;
            }
        }
        return false;
    }

    public int size() {
        int size = 0;
        for (final Entry<String, List<String>> entry : params.entrySet()) {
            size += entry.getValue().size();
        }
        return size;
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return new QueryIterator(params.entrySet().iterator(), this::markDirty);
    }

    Map<String, List<String>> queryParameters() {
        return params;
    }

    boolean isDirty() {
        return dirty;
    }

    void resetDirty() {
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
    }

    private List<String> getValues(final String key) {
        return params.computeIfAbsent(key, k -> new ArrayList<>(DEFAULT_LIST_SIZE));
    }

    private void validateQueryParam(final String key, final String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Null or empty query parameter names are not allowed.");
        }
        if (value == null) {
            throw new IllegalArgumentException("Null query parameter values are not allowed.");
        }
    }

    private static final class ValuesIterator implements Iterator<String> {
        private final Iterator<String> listIterator;
        private final Runnable queryParamsUpdater;

        private ValuesIterator(final Iterator<String> listIterator,
                               final Runnable removalQueryParamsUpdater) {
            this.listIterator = listIterator;
            this.queryParamsUpdater = removalQueryParamsUpdater;
        }

        @Override
        public boolean hasNext() {
            return listIterator.hasNext();
        }

        @Override
        public String next() {
            return listIterator.next();
        }

        @Override
        public void remove() {
            listIterator.remove();
            queryParamsUpdater.run();
        }
    }

    private static final class QueryIterator implements Iterator<Entry<String, String>> {

        private final Iterator<Entry<String, List<String>>> mapIterator;
        private final Runnable queryParamsUpdater;
        @Nullable
        private String key;
        private List<String> value;
        private Iterator<String> listIterator;

        private QueryIterator(final Iterator<Entry<String, List<String>>> mapIterator,
                              final Runnable queryParamsUpdater) {
            this.mapIterator = mapIterator;
            this.queryParamsUpdater = queryParamsUpdater;
            listIterator = emptyIterator();
            value = emptyList();
        }

        @Override
        public boolean hasNext() {
            if (listIterator.hasNext()) {
                return true;
            }
            while (mapIterator.hasNext()) {
                final Entry<String, List<String>> entry = mapIterator.next();
                key = entry.getKey();
                value = entry.getValue();
                listIterator = value.iterator();
                if (listIterator.hasNext()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Entry<String, String> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final String value = listIterator.next();
            assert key != null;
            return new Entry<String, String>() {

                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    return value;
                }

                @Override
                public String setValue(final String value) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void remove() {
            listIterator.remove();
            if (value.isEmpty()) {
                mapIterator.remove();
            }
            queryParamsUpdater.run();
        }
    }
}
