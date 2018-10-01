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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static java.util.Collections.addAll;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpQuery}.
 */
public final class DefaultHttpQuery implements HttpQuery {

    private static final int DEFAULT_LIST_SIZE = 2;

    private final Consumer<Map<String, List<String>>> queryParamsUpdater;
    private final Map<String, List<String>> params;

    /**
     * Create a new instance.
     *
     * @param params Map of query parameters.
     * @param queryParamsUpdater callback for setting the query parameters on a request.
     */
    public DefaultHttpQuery(final Map<String, List<String>> params, final Consumer<Map<String, List<String>>> queryParamsUpdater) {
        this.queryParamsUpdater = requireNonNull(queryParamsUpdater);
        this.params = requireNonNull(params);
    }

    @Override
    public String get(final String key) {
        final List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    @Override
    public Iterator<String> all(final String key) {
        final List<String> values = params.get(key);
        if (values == null) {
            return emptyIterator();
        }
        return values.iterator();
    }

    @Override
    public Set<String> keys() {
        return params.keySet();
    }

    @Override
    public HttpQuery add(final String key, final String value) {
        getValues(key).add(value);
        return this;
    }

    @Override
    public HttpQuery add(final String key, final Iterable<String> values) {
        final List<String> paramValues = getValues(key);
        for (final String value : values) {
            paramValues.add(value);
        }
        return this;
    }

    @Override
    public HttpQuery add(final String key, final String... values) {
        final List<String> paramValues = getValues(key);
        addAll(paramValues, values);
        return this;
    }

    @Override
    public HttpQuery set(final String key, final String value) {
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        list.add(value);
        params.put(key, list);
        return this;
    }

    @Override
    public HttpQuery set(final String key, final Iterable<String> values) {
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        for (final String value : values) {
            list.add(value);
        }
        params.put(key, list);
        return this;
    }

    @Override
    public HttpQuery set(final String key, final String... values) {
        final ArrayList<String> list = new ArrayList<>(DEFAULT_LIST_SIZE);
        addAll(list, values);
        params.put(key, list);
        return this;
    }

    @Override
    public boolean contains(final String key, final String value) {
        final Iterator<String> values = all(key);
        while (values.hasNext()) {
            if (value.equals(values.next())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(final String key) {
        final List<String> removed = params.remove(key);
        return removed != null && !removed.isEmpty();
    }

    @Override
    public boolean remove(final String key, final String value) {
        final Iterator<String> values = all(key);
        while (values.hasNext()) {
            if (value.equals(values.next())) {
                values.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        int size = 0;
        for (final Map.Entry<String, List<String>> entry : params.entrySet()) {
            size += entry.getValue().size();
        }
        return size;
    }

    @Override
    public boolean empty() {
        return size() > 0;
    }

    @Override
    public void encodeToRequestTarget() {
        queryParamsUpdater.accept(params);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new QueryIterator(params.entrySet().iterator());
    }

    private List<String> getValues(final String key) {
        return params.computeIfAbsent(key, k -> new ArrayList<>(DEFAULT_LIST_SIZE));
    }

    private static final class QueryIterator implements Iterator<Map.Entry<String, String>> {

        private final Iterator<Map.Entry<String, List<String>>> mapIterator;
        @Nullable
        private String key;
        private Iterator<String> listIterator;

        private QueryIterator(final Iterator<Map.Entry<String, List<String>>> mapIterator) {
            this.mapIterator = mapIterator;
            listIterator = emptyIterator();
        }

        @Override
        public boolean hasNext() {
            if (listIterator.hasNext()) {
                return true;
            }
            while (mapIterator.hasNext()) {
                final Map.Entry<String, List<String>> entry = mapIterator.next();
                key = entry.getKey();
                listIterator = entry.getValue().iterator();
                if (listIterator.hasNext()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Map.Entry<String, String> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final String value = listIterator.next();
            assert key != null;
            return new Map.Entry<String, String>() {

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
        }
    }
}
