package io.axor.api;

public class ActorSettings {
    private boolean autoAck = true;

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }
}
