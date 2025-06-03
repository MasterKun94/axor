package io.axor.api;

public interface SessionDefCreator<T> {
    SessionDef<T> create(SessionContext<T> context);
}
