package io.axor.commons.stream;

public interface FoldFunction<T, P> {
    P fold(P left, T right) throws Throwable;
}
