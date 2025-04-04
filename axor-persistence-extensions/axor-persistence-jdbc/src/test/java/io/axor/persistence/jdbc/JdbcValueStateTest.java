package io.axor.persistence.jdbc;

import com.typesafe.config.ConfigFactory;
import io.axor.persistence.state.ShareableScope;
import io.axor.persistence.state.StateRegistry;
import io.axor.persistence.state.ValueState;
import io.axor.persistence.state.ValueStateDef;
import io.axor.persistence.state.ValueStateDefBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JdbcValueStateTest {

    @Test
    public void test() throws Exception {
        ValueStateDef<String, String> def = new ValueStateDefBuilder<>()
                .name("JdbcValueStateTest")
                .impl("jdbc")
                .scope(ShareableScope.PRIVATE)
                .valueSerde(new StringSerde())
                .config(ConfigFactory.parseResources("axor-persistence-jdbc-mysql.conf"))
                .build((v, c) -> v + c);
        ValueState<String, String> state = StateRegistry.getValueState(def);
        state.remove();
        assertNull(state.get());
        state.set("foo");
        assertEquals("foo", state.get());
        state.command("bar");
        assertEquals("foobar", state.get());
        assertEquals("foobar", state.getAndCommand(" hello"));
        assertEquals("foobar hello hihi", state.commandAndGet(" hihi"));
        assertEquals("foobar hello hihi", state.getAndSet("111"));
        assertEquals("111", state.getAndRemove());
        assertNull(state.get());
        if (state instanceof AutoCloseable) {
            ((AutoCloseable) state).close();
        }
    }
}
