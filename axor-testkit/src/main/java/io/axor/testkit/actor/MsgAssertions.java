package io.axor.testkit.actor;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import org.junit.Assert;

import java.util.function.Predicate;

/**
 * A utility class providing factory methods for creating message assertions.
 * <p>
 * This class contains static methods that create various types of assertions for testing messages
 * in actor-based systems. These assertions can be used with MockActorRef to verify that the
 * expected messages are received.
 */
public class MsgAssertions {

    /**
     * Creates an assertion that always passes, regardless of the message or sender.
     *
     * @param <T> The type of messages to assert on
     * @return A message assertion that always passes
     */
    public static <T> MsgAssertion<T> any() {
        return (msg, sender) -> {
            // Always passes
        };
    }

    /**
     * Creates an assertion that tests the message against a predicate.
     *
     * @param predicate The predicate to test the message against
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if the predicate returns true for the message
     */
    public static <T> MsgAssertion<T> test(Predicate<T> predicate) {
        return (msg, sender) -> Assert.assertTrue(predicate.test(msg));
    }

    /**
     * Creates an assertion that tests the message against a predicate, with a custom error
     * message.
     *
     * @param message   The error message to use if the assertion fails
     * @param predicate The predicate to test the message against
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if the predicate returns true for the message
     */
    public static <T> MsgAssertion<T> test(String message, Predicate<T> predicate) {
        return (msg, sender) -> Assert.assertTrue(message, predicate.test(msg));
    }

    /**
     * Creates an assertion that tests the message and sender against a matcher.
     *
     * @param matcher The matcher to test the message and sender against
     * @param <T>     The type of messages to assert on
     * @return A message assertion that passes if the matcher returns true for the message and
     * sender
     */
    public static <T> MsgAssertion<T> test(MsgMatcher<T> matcher) {
        return (msg, sender) -> Assert.assertTrue(matcher.match(msg, sender));
    }

    /**
     * Creates an assertion that tests the message and sender against a matcher, with a custom error
     * message.
     *
     * @param message The error message to use if the assertion fails
     * @param matcher The matcher to test the message and sender against
     * @param <T>     The type of messages to assert on
     * @return A message assertion that passes if the matcher returns true for the message and
     * sender
     */
    public static <T> MsgAssertion<T> test(String message, MsgMatcher<T> matcher) {
        return (msg, sender) -> Assert.assertTrue(message, matcher.match(msg, sender));
    }

    /**
     * Creates an assertion that tests if the message equals the expected value.
     *
     * @param expect The expected message value
     * @param <T>    The type of messages to assert on
     * @return A message assertion that passes if the message equals the expected value
     */
    public static <T> MsgAssertion<T> msgEq(T expect) {
        return (msg, sender) -> Assert.assertEquals(expect, msg);
    }

    /**
     * Creates an assertion that tests if the message equals the expected value, with a custom error
     * message.
     *
     * @param message The error message to use if the assertion fails
     * @param expect  The expected message value
     * @param <T>     The type of messages to assert on
     * @return A message assertion that passes if the message equals the expected value
     */
    public static <T> MsgAssertion<T> msgEq(String message, T expect) {
        return ((msg, sender) -> Assert.assertEquals(message, expect, msg));
    }

    /**
     * Creates an assertion that tests if the message equals the expected value and the sender
     * equals the expected sender or receiver.
     *
     * @param expect    The expected message value
     * @param expectRef The expected sender or receiver
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender match the expected
     * values
     */
    public static <T> MsgAssertion<T> eq(T expect, ActorRef<?> expectRef) {
        return (msg, sender) -> {
            Assert.assertEquals(expect, msg);
            Assert.assertEquals(expectRef, sender);
        };
    }

    /**
     * Creates an assertion that tests if the message equals the expected value and the sender's
     * address equals the expected address.
     *
     * @param expect    The expected message value
     * @param expectRef The expected sender or receiver address
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender address match the
     * expected values
     */
    public static <T> MsgAssertion<T> eq(T expect, ActorAddress expectRef) {
        return (msg, sender) -> {
            Assert.assertEquals(expect, msg);
            Assert.assertEquals(expectRef, sender.address());
        };
    }

    /**
     * Creates an assertion that tests if the message equals the expected value and the sender
     * equals the expected ref, with a custom error message.
     *
     * @param message   The error message to use if the assertion fails
     * @param expect    The expected message value
     * @param expectRef The expected sender or receiver
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender match the expected
     * values
     */
    public static <T> MsgAssertion<T> eq(String message, T expect, ActorRef<?> expectRef) {
        return (msg, sender) -> {
            Assert.assertEquals(message, expect, msg);
            Assert.assertEquals(message, expectRef, sender);
        };
    }

    /**
     * Creates an assertion that tests if the message equals the expected value and the sender's
     * address equals the expected address, with a custom error message.
     *
     * @param message   The error message to use if the assertion fails
     * @param expect    The expected message value
     * @param expectRef The expected sender or receiver address
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender address match the
     * expected values
     */
    public static <T> MsgAssertion<T> eq(String message, T expect, ActorAddress expectRef) {
        return (msg, sender) -> {
            Assert.assertEquals(message, expect, msg);
            Assert.assertEquals(message, expectRef, sender.address());
        };
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value.
     *
     * @param expect The expected message instance
     * @param <T>    The type of messages to assert on
     * @return A message assertion that passes if the message is the same instance as the expected
     * value
     */
    public static <T> MsgAssertion<T> same(T expect) {
        return ((msg, sender) -> Assert.assertSame(expect, msg));
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value,
     * with a custom error message.
     *
     * @param message The error message to use if the assertion fails
     * @param expect  The expected message instance
     * @param <T>     The type of messages to assert on
     * @return A message assertion that passes if the message is the same instance as the expected
     * value
     */
    public static <T> MsgAssertion<T> same(String message, T expect) {
        return ((msg, sender) -> Assert.assertSame(message, expect, msg));
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value and
     * the sender is the same instance as the expected sender.
     *
     * @param expect    The expected message instance
     * @param expectRef The expected sender or receiver of the message
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender are the same instances
     * as the expected values
     */
    public static <T> MsgAssertion<T> same(T expect, ActorRef<?> expectRef) {
        return (msg, sender) -> {
            Assert.assertSame(expect, msg);
            Assert.assertSame(expectRef, sender);
        };
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value and
     * the sender is the same instance as the expected sender, with a custom error message.
     *
     * @param message   The error message to use if the assertion fails
     * @param expect    The expected message instance
     * @param expectRef The expected sender or receiver of the message
     * @param <T>       The type of messages to assert on
     * @return A message assertion that passes if both the message and sender are the same instances
     * as the expected values
     */
    public static <T> MsgAssertion<T> same(String message, T expect, ActorRef<?> expectRef) {
        return (msg, sender) -> {
            Assert.assertSame(message, expect, msg);
            Assert.assertSame(message, expectRef, sender);
        };
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value and
     * the sender's address is the same instance as the expected address.
     *
     * @param expect        The expected message instance
     * @param expectAddress The expected sender or receiver address instance
     * @param <T>           The type of messages to assert on
     * @return A message assertion that passes if both the message and sender address are the same
     * instances as the expected values
     */
    public static <T> MsgAssertion<T> same(T expect, ActorAddress expectAddress) {
        return (msg, sender) -> {
            Assert.assertSame(expect, msg);
            Assert.assertSame(expectAddress, sender.address());
        };
    }

    /**
     * Creates an assertion that tests if the message is the same instance as the expected value and
     * the sender's address is the same instance as the expected address, with a custom error
     * message.
     *
     * @param message       The error message to use if the assertion fails
     * @param expect        The expected message instance
     * @param expectAddress The expected sender or receiver address instance
     * @param <T>           The type of messages to assert on
     * @return A message assertion that passes if both the message and sender address are the same
     * instances as the expected values
     */
    public static <T> MsgAssertion<T> same(String message, T expect, ActorAddress expectAddress) {
        return (msg, sender) -> {
            Assert.assertSame(message, expect, msg);
            Assert.assertSame(message, expectAddress, sender.address());
        };
    }

    /**
     * Combines two assertions into a single assertion that passes only if both assertions pass.
     *
     * @param matcher  The first assertion
     * @param matcher2 The second assertion
     * @param <T>      The type of messages to assert on
     * @return A message assertion that passes if both assertions pass
     */
    public static <T> MsgAssertion<T> and(MsgAssertion<T> matcher, MsgAssertion<T> matcher2) {
        return (msg, sender) -> {
            matcher.testAssert(msg, sender);
            matcher2.testAssert(msg, sender);
        };
    }
}
