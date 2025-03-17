package io.masterkun.axor.runtime;

import java.util.List;

public class Unsafe {
    public static <T> MsgType<T> msgType(Class<T> type, List<MsgType<?>> typeArguments) {
        return new Parameterized<>(type, typeArguments);
    }
}
