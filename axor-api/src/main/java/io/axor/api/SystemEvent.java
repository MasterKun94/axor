package io.axor.api;

import io.axor.runtime.MsgType;
import io.axor.runtime.Status;

/**
 * Represents a system event in the actor framework, which can be an event related to an actor or a
 * stream. This interface is sealed and defines two sub-interfaces: {@code ActorEvent} and
 * {@code StreamEvent}.
 */
public sealed interface SystemEvent extends Signal {

    enum ActorAction {
        ON_START,
        ON_RECEIVE,
        ON_SIGNAL,
        ON_RESTART,
        ON_PRE_STOP,
        ON_POST_STOP,
    }

    sealed interface ActorEvent extends SystemEvent {
        ActorRef<?> actor();
    }

    sealed interface StreamEvent extends SystemEvent {
        ActorAddress remoteAddress();

        MsgType<?> remoteMsgType();

        ActorAddress selfAddress();

        MsgType<?> selfMsgType();
    }

    record ActorStarted(ActorRef<?> actor) implements ActorEvent {
    }

    record ActorStopped(ActorRef<?> actor) implements ActorEvent {
    }

    record ActorError(ActorRef<?> actor,
                      ActorAction action,
                      Throwable cause) implements ActorEvent {
    }

    record ActorRestarted(ActorRef<?> actor) implements ActorEvent {
    }

    record StreamOutOpened(ActorAddress remoteAddress, MsgType<?> remoteMsgType,
                           ActorAddress selfAddress,
                           MsgType<?> selfMsgType) implements StreamEvent {
    }

    record StreamInOpened(ActorAddress remoteAddress, MsgType<?> remoteMsgType,
                          ActorAddress selfAddress, MsgType<?> selfMsgType) implements StreamEvent {
    }

    record StreamOutClosed(ActorAddress remoteAddress, MsgType<?> remoteMsgType,
                           ActorAddress selfAddress, MsgType<?> selfMsgType,
                           Status status) implements StreamEvent {
    }

    record StreamInClosed(ActorAddress remoteAddress, MsgType<?> remoteMsgType,
                          ActorAddress selfAddress, MsgType<?> selfMsgType,
                          Status status) implements StreamEvent {
    }
}
