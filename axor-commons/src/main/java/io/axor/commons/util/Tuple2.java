package io.axor.commons.util;

public record Tuple2<T1, T2>(T1 _1, T2 _2) {
    public static <T1, T2> Tuple2<T1, T2> of(T1 _1, T2 _2) {
        return new Tuple2<>(_1, _2);
    }
}
