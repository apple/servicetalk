package io.servicetalk.concurrent.api;

public interface Scope extends AutoCloseable {

    Scope NOOP = () -> {};

    @Override
    void close();
}
