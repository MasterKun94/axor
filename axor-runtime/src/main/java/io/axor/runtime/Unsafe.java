package io.axor.runtime;

import java.util.List;

public class Unsafe {
    @SuppressWarnings("unchecked")
    public static <T> MsgType<T> msgType(Class<?> type, List<MsgType<?>> typeArguments) {
        return new Parameterized<>((Class<T>) type, typeArguments);
    }
}
