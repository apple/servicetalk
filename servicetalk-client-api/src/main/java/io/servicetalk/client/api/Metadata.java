package io.servicetalk.client.api;

import static java.util.Objects.requireNonNull;

public interface Metadata {

    final class Key<T> {

        private final Class<T> clazz;
        private final String name;
        private final T defaultValue;

        public Key(final Class<T> clazz, final String name, final T defaultValue) {
            this.clazz = requireNonNull(clazz, "clazz");
            this.name = requireNonNull(name, "name");
            this.defaultValue = requireNonNull(defaultValue, "defaultValue");
        }

        String name() {
            return name;
        }

        Class<T> clazz() {
            return clazz;
        }

        T defaultValue() {
            return defaultValue;
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

    <T> T get(Key<T> key);
}
