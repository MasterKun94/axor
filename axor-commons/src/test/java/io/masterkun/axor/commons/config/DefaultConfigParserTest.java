package io.masterkun.axor.commons.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultConfigParserTest {

    @Test
    public void testParseString() {
        Config config = ConfigFactory.parseString("key = \"value\"");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, String.class);
        assertEquals("value", parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseInteger() {
        Config config = ConfigFactory.parseString("key = 42");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, Integer.class);
        assertEquals(42, parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseLong() {
        Config config = ConfigFactory.parseString("key = 42");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, Long.class);
        assertEquals(42L, parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseDouble() {
        Config config = ConfigFactory.parseString("key = 3.14");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, Double.class);
        assertEquals(3.14, ((Number) parser.parseFrom(config, "key", typeRef))
                .doubleValue(), 0.001);
    }

    @Test
    public void testParseBoolean() {
        Config config = ConfigFactory.parseString("key = true");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, Boolean.class);
        assertTrue((Boolean) parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseDuration() {
        Config config = ConfigFactory.parseString("key = 2h");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, Duration.class);
        assertEquals(Duration.ofHours(2), parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseURI() {
        Config config = ConfigFactory.parseString("key = \"http://example.com\"");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, URI.class);
        assertEquals(URI.create("http://example.com"), parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseListStrings() {
        Config config = ConfigFactory.parseString("key = [a, b, c]");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, List.class, String.class);
        assertEquals(List.of("a", "b", "c"), parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseListIntegers() {
        Config config = ConfigFactory.parseString("key = [1, 2, 3]");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, List.class, Integer.class);
        assertEquals(List.of(1, 2, 3), parser.parseFrom(config, "key", typeRef));
    }

    @Test
    public void testParseListDurations() {
        Config config = ConfigFactory.parseString("key = [1s, 2s, 3s]");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, List.class, Duration.class);
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3))
                , parser.parseFrom(config, "key", typeRef));
    }

    @Test(expected = ConfigParseException.class)
    public void testMissingKey() {
        Config config = ConfigFactory.parseString("");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(false, String.class);
        parser.parseFrom(config, "missingKey", typeRef);
    }

    @Test
    public void testNullableString() {
        Config config = ConfigFactory.parseString("key = null");
        DefaultConfigParser parser = new DefaultConfigParser();
        TypeRef typeRef = new TypeRef(true, String.class);
        assertNull(parser.parseFrom(config, "key", typeRef));
    }
}
