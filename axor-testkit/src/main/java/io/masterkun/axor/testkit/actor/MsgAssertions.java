package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import org.junit.Assert;

import java.util.function.Predicate;

public class MsgAssertions {

    public static <T> MsgAssertion<T> any() {
        return (msg, sender) -> {
        };
    }

    public static <T> MsgAssertion<T> test(Predicate<T> predicate) {
        return (msg, sender) -> Assert.assertTrue(predicate.test(msg));
    }

    public static <T> MsgAssertion<T> test(String message, Predicate<T> predicate) {
        return (msg, sender) -> Assert.assertTrue(message, predicate.test(msg));
    }

    public static <T> MsgAssertion<T> test(MsgMatcher<T> matcher) {
        return (msg, sender) -> Assert.assertTrue(matcher.match(msg, sender));
    }

    public static <T> MsgAssertion<T> test(String message, MsgMatcher<T> matcher) {
        return (msg, sender) -> Assert.assertTrue(message, matcher.match(msg, sender));
    }

    public static <T> MsgAssertion<T> msgEq(T expect) {
        return (msg, sender) -> Assert.assertEquals(expect, msg);
    }

    public static <T> MsgAssertion<T> msgEq(String message, T expect) {
        return ((msg, sender) -> Assert.assertEquals(message, expect, msg));
    }

    public static <T> MsgAssertion<T> eq(T expect, ActorRef<?> expectSender) {
        return (msg, sender) -> {
            Assert.assertEquals(expect, msg);
            Assert.assertEquals(expectSender, sender);
        };
    }

    public static <T> MsgAssertion<T> eq(T expect, ActorAddress expectSender) {
        return (msg, sender) -> {
            Assert.assertEquals(expect, msg);
            Assert.assertEquals(expectSender, sender.address());
        };
    }

    public static <T> MsgAssertion<T> eq(String message, T expect, ActorRef<?> expectSender) {
        return (msg, sender) -> {
            Assert.assertEquals(message, expect, msg);
            Assert.assertEquals(message, expectSender, sender);
        };
    }

    public static <T> MsgAssertion<T> eq(String message, T expect, ActorAddress expectSender) {
        return (msg, sender) -> {
            Assert.assertEquals(message, expect, msg);
            Assert.assertEquals(message, expectSender, sender.address());
        };
    }

    public static <T> MsgAssertion<T> same(T expect) {
        return ((msg, sender) -> Assert.assertSame(expect, msg));
    }

    public static <T> MsgAssertion<T> same(String message, T expect) {
        return ((msg, sender) -> Assert.assertSame(message, expect, msg));
    }

    public static <T> MsgAssertion<T> same(T expect, ActorRef<?> expectSender) {
        return (msg, sender) -> {
            Assert.assertSame(expect, msg);
            Assert.assertSame(expectSender, sender);
        };
    }

    public static <T> MsgAssertion<T> same(String message, T expect, ActorRef<?> expectSender) {
        return (msg, sender) -> {
            Assert.assertSame(message, expect, msg);
            Assert.assertSame(message, expectSender, sender);
        };
    }

    public static <T> MsgAssertion<T> same(T expect, ActorAddress expectSender) {
        return (msg, sender) -> {
            Assert.assertSame(expect, msg);
            Assert.assertSame(expectSender, sender.address());
        };
    }

    public static <T> MsgAssertion<T> same(String message, T expect, ActorAddress expectSender) {
        return (msg, sender) -> {
            Assert.assertSame(message, expect, msg);
            Assert.assertSame(message, expectSender, sender.address());
        };
    }

    public static <T> MsgAssertion<T> and(MsgAssertion<T> matcher, MsgAssertion<T> matcher2) {
        return (msg, sender) -> {
            matcher.testAssert(msg, sender);
            matcher2.testAssert(msg, sender);
        };
    }
}
