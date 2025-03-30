package io.axor.commons.util;

public record Tuple3<T1, T2, T3>(T1 _1, T2 _2, T3 _3) {
    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 _1, T2 _2, T3 _3) {
        return new Tuple3<>(_1, _2, _3);
    }
}
