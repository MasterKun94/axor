package io.axor.runtime;

import io.masterkun.stateeasy.concurrent.EventStage;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.Callable;

import static io.axor.runtime.EventContextImpl.FALLBACK_CTX_TL;

/**
 * The {@code EventContext} interface represents a context for event handling, providing methods to
 * manage and manipulate the context. It is sealed to only allow specific implementations.
 *
 * <p>This interface provides methods to get and set the current context, retrieve and update
 * values associated with keys, and manage scopes within the context. It also includes methods to
 * execute runnables and callables asynchronously within a new scope.
 */
public sealed interface EventContext permits EventContextInitial, EventContextImpl {
    EventContext INITIAL = new EventContextInitial();

    /**
     * Retrieves the current {@link EventContext} for the current thread.
     *
     * @return the current {@link EventContext} for the thread, or the initial {@link EventContext}
     * if the current thread is not an instance of {@link EventDispatcher.DispatcherThread}
     */
    static EventContext current() {
        if (Thread.currentThread() instanceof EventDispatcher.DispatcherThread ext) {
            return ext.getContext();
        }
        return FALLBACK_CTX_TL.get();
    }

    /**
     * Sets the given {@link EventContext} for the current thread.
     *
     * @param context the {@link EventContext} to set for the current thread
     * @return the previous {@link EventContext} associated with the current thread
     * @throws RuntimeException if the current thread is not an instance of
     *                          {@link EventDispatcher.DispatcherThread}
     */
    static EventContext set(EventContext context) {
        if (Thread.currentThread() instanceof EventDispatcher.DispatcherThread ext) {
            return ext.setContext(context);
        }
        EventContext prev = FALLBACK_CTX_TL.get();
        FALLBACK_CTX_TL.set(context == null ? EventContext.INITIAL : context);
        return prev;
    }

    /**
     * Retrieves the value associated with the given key from the event context.
     *
     * @param <T> the type of the value associated with the key
     * @param key the key for which to retrieve the value
     * @return the value associated with the given key, or null if no value is found
     */
    @Nullable
    <T> T get(Key<T> key);

    /**
     * Adds or updates a key-value pair in the current event context.
     *
     * @param <T>   the type of the value associated with the key
     * @param key   the key for which to set the value
     * @param value the value to be associated with the given key
     * @return a new {@link EventContext} with the updated key-value pair
     */
    <T> EventContext with(Key<T> key, T value);

    /**
     * Returns a new {@link EventContext} with the specified key removed.
     *
     * @param key the key to be removed from the event context
     * @return a new {@link EventContext} without the specified key
     */
    EventContext without(Key<?> key);

    /**
     * Opens a new scope within the current event context.
     *
     * <p>A scope is a logical unit of work that can be used to manage resources and state. When a
     * scope is opened, it can be used to encapsulate a block of code, and when the scope is closed,
     * any resources or state associated with that scope can be released.
     *
     * @return a {@link Scope} object representing the newly opened scope
     */
    Scope openScope();

    /**
     * Executes the given {@link Runnable} within a new scope in the provided
     * {@link EventDispatcher}.
     *
     * <p>This method opens a new scope, executes the provided {@link Runnable}, and ensures that
     * the scope is properly closed after the execution, even if an exception occurs.
     *
     * @param runnable   the {@link Runnable} to be executed
     * @param dispatcher the {@link EventDispatcher} in which the {@link Runnable} should be
     *                   executed
     */
    default void execute(Runnable runnable, EventDispatcher dispatcher) {
        dispatcher.execute(() -> {
            try (var ignore = openScope()) {
                runnable.run();
            }
        });
    }

    /**
     * Executes the given {@link Runnable} asynchronously within a new scope in the provided
     * {@link EventDispatcher}.
     *
     * <p>This method opens a new scope, executes the provided {@link Runnable}, and ensures that
     * the scope is properly closed after the execution, even if an exception occurs. The execution
     * is performed asynchronously using the specified {@link EventDispatcher}.
     *
     * @param runnable   the {@link Runnable} to be executed
     * @param dispatcher the {@link EventDispatcher} in which the {@link Runnable} should be
     *                   executed
     * @return an {@link EventStage} representing the asynchronous computation
     */
    default EventStage<Void> runAsync(Runnable runnable, EventDispatcher dispatcher) {
        return EventStage.runAsync(() -> {
            try (var ignore = openScope()) {
                runnable.run();
            }
        }, dispatcher);
    }

    /**
     * Asynchronously supplies a result using the provided callable and event dispatcher.
     *
     * <p>This method opens a new scope, executes the provided callable, and ensures that the
     * scope is properly closed after the execution, even if an exception occurs. The execution is
     * performed asynchronously using the specified {@link EventDispatcher}.
     *
     * @param <T>        the type of the result supplied by the callable
     * @param callable   the callable to be executed
     * @param dispatcher the event dispatcher in which the callable should be executed
     * @return an {@link EventStage} representing the asynchronous computation
     */
    default <T> EventStage<T> supplyAsync(Callable<T> callable, EventDispatcher dispatcher) {
        return EventStage.supplyAsync(() -> {
            try (var ignore = openScope()) {
                return callable.call();
            }
        }, dispatcher);
    }

    /**
     * Defines the contract for marshalling and unmarshalling keys to and from a byte array.
     *
     * <p>This interface is used to convert an object of type {@code T} into a byte array and vice
     * versa. Implementations of this interface are typically used in scenarios where objects need
     * to be serialized and deserialized, such as when storing or transmitting data.
     *
     * @param <T> the type of the key that can be marshalled and unmarshalled
     */
    interface KeyMarshaller<T> {
        T read(byte[] bytes, int off, int len);

        byte[] write(T value);
    }

    /**
     * The {@code Scope} interface represents a logical unit of work that can be used to manage
     * resources and state within an {@link EventContext}. A scope is typically used to encapsulate
     * a block of code, and when the scope is closed, any resources or state associated with that
     * scope can be released. This interface extends {@link Closeable}, requiring implementations to
     * provide a method to close the scope and release any associated resources.
     */
    interface Scope extends Closeable {
        @Override
        void close();
    }

    /**
     * A record representing a key used in an event context to store and retrieve values.
     *
     * @param <T> the type of the value associated with the key
     */
    record Key<T>(int id, String name, String description, KeyMarshaller<T> marshaller) {
    }
}
