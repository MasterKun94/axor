package io.masterkun.kactor.cluster.membership;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class MessageIterable<T> implements Iterable<T> {

    private final T[] elems;

    MessageIterable(T[] elems) {
        this.elems = elems;
    }

    @Override
    public Iterator<T> iterator() {
        return Arrays.asList(elems).iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return Arrays.spliterator(elems);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (T elem : elems) {
            action.accept(elem);
        }
    }

    public Stream<T> stream() {
        return Arrays.stream(elems);
    }

    public int size() {
        return elems.length;
    }

    T[] unwrap() {
        return elems;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MessageIterable<?> that = (MessageIterable<?>) o;
        return Objects.deepEquals(elems, that.elems);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elems);
    }

    @Override
    public String toString() {
        return Arrays.toString(elems);
    }
}
