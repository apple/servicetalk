package io.servicetalk.client.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Utilities helpful for extracting meta-data from {@link ServiceDiscovererEvent}s.
 */
public final class ServiceDiscovererMetadata {

    public static final Map<String, Object> EMPTY_MAP = Collections.unmodifiableMap(new HashMap<>(0));

    /**
     * Metadata that describes the relative weight of an endpoint.
     */
    public static final Key<Double> WEIGHT = new ServiceDiscovererMetadata.Key<>(Double.class, "endpoint.weight", 1.0);

    /**
     * Metadata describing the priority class of an endpoint.
     */
    public static final Key<Integer> PRIORITY = new ServiceDiscovererMetadata.Key<>(
            Integer.class, "endpoint.priority", 0);

    /**
     * An extractor of meta-data to user with {@link ServiceDiscovererEvent} instances.
     *
     * A {@link ServiceDiscovererEvent} can carry additional metadata, but this data is not type safe. The key type
     * exists to provide a uniform way to define meta-data extractors that can properly extract and cast meta-data
     * while also providing a default.
     * @param <T> the expected type of the meta-data.
     */
    public static final class Key<T> {

        private final Class<T> clazz;
        private final String name;
        private final T defaultValue;

        public Key(final Class<T> clazz, final String name, final T defaultValue) {
            this.clazz = requireNonNull(clazz, "clazz");
            this.name = requireNonNull(name, "name");
            this.defaultValue = requireNonNull(defaultValue, "defaultValue");
        }

        public String name() {
            return name;
        }

        public Class<T> clazz() {
            return clazz;
        }

        public <T> boolean exists(ServiceDiscovererEvent<?> event) {
            return event.metadata().containsKey(name);
        }

        public T getValue(ServiceDiscovererEvent<?> event) {
            Object result = event.metadata().get(name);
            if (clazz.isInstance(result)) {
                return (T) result;
            } else {
                return defaultValue;
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != Key.class) {
                return false;
            }
            Key<?> other = (Key<?>) obj;
            return other.clazz.equals(clazz) && other.name.equals(name);
        }

        @Override
        public String toString() {
            return "Key<" + clazz.getName() + ">(" + name + ", " + defaultValue + ")";
        }
    }

    private ServiceDiscovererMetadata() {
        // no instances.
    }
}
