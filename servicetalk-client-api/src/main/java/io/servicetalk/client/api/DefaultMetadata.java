package io.servicetalk.client.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

final class DefaultMetadata implements Metadata {

    private static Logger LOGGER = LoggerFactory.getLogger(DefaultMetadata.class);

    static final Metadata EMPTY_METADATA = new EmptyMetadata();

    private final Map<String, Object> values;

    private DefaultMetadata(final Map<String, Object> values) {
        this.values = requireNonNull(values, "values");
    }

    @Override
    public <T> T get(Key<T> key) {
        Object value = values.get(key.name());
        if (value == null) {
            return key.defaultValue();
        } else if (!key.clazz().isInstance(value)) {
            LOGGER.info("Metadata entry with name {} was found but didn't contain the expected type. Found: {}, " +
                    "expected: {}", key.name(), value.getClass(), key.clazz());
            return key.defaultValue();
        } else {
            return key.clazz().cast(value);
        }
    }

    @Override
    public <T> Metadata put(Key<T> key, T value) {
        Map<String, Object> next = new HashMap<>(values);
        next.put(key.name(), value);
        return new DefaultMetadata(next);
    }

    @Override
    public <T> Metadata remove(Key<T> key) {
        if (!values.containsKey(key.name())) {
            return this;
        }
        Map<String, Object> next = new HashMap<>(values);
        next.remove(key.name());
        return new DefaultMetadata(next);
    }

    // A simple implementation of the always-empty Metadata.
    private static final class EmptyMetadata implements Metadata {
        @Override
        public <T> T get(Key<T> key) {
            return key.defaultValue();
        }

        @Override
        public <T> Metadata put(Key<T> key, T value) {
            Map<String, Object> next = new HashMap<>();
            next.put(key.name(), value);
            return new DefaultMetadata(next);
        }

        @Override
        public <T> Metadata remove(Key<T> key) {
            return this;
        }
    }
}
