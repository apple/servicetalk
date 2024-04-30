/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
    public static final Key<Double> WEIGHT = new ServiceDiscovererMetadata.Key<>(Double.class, "endpoint.weight", 1d);

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

        /**
         * Get the name associated with the meta-data.
         * @return the name associated with the meta-data.
         */
        public String name() {
            return name;
        }

        /**
         * The java class that is expected to be associated with the meta-data.
         * @return the java class that is expected to be associated with the meta-data.
         */
        public Class<T> clazz() {
            return clazz;
        }

        /**
         * Determine whether the meta-data both contains an entry with the keys name and that entry is the correct type.
         * @param event the {@link ServiceDiscovererEvent} for which to check if the meta-data exists.
         * @return true if the meta-data contains an entry with the keys name and the value is the correct type.
         */
        public boolean contains(ServiceDiscovererEvent<?> event) {
            return clazz.isInstance(event.metadata().get(name));
        }

        /**
         * Extract the meta-data from a {@link ServiceDiscovererEvent}, or get the default.
         * @param event the {@link ServiceDiscovererEvent} from which to extract the meta-data.
         * @return the value contained in the meta-data, or the default if it doesn't exist or has the wrong type.
         */
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
