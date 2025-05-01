package io.axor.api;

import org.jetbrains.annotations.NotNull;

public class ErrorMsgException extends Exception {
    private final Object msg;

    public ErrorMsgException(@NotNull Object msg) {
        super(msg.toString());
        this.msg = msg;
    }

    public Object getMsg() {
        return msg;
    }
}
