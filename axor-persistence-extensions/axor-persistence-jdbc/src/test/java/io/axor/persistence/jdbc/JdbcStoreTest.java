package io.axor.persistence.jdbc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JdbcStoreTest {

    @Test
    public void testMysql() {
        var config = ConfigFactory.load("stateeasy-mysql.conf")
                .withFallback(ConfigFactory.load("stateeasy-jdbc.conf"));
        Testkit test = new Testkit(config);
        test.testAll();
    }

    @Test
    public void testPostgres() {
        var config = ConfigFactory.load("stateeasy-postgres.conf")
                .withFallback(ConfigFactory.load("stateeasy-jdbc.conf"));
        Testkit test = new Testkit(config);
        test.testAll();
    }

    public static class Testkit {
        private final JdbcStore store;
        private final StringMarshaller marshaller = new StringMarshaller();
        private final JdbcStoreInstance<String, String> instance;

        public Testkit(Config config) {
            this.store = JdbcStore.get(config);
            this.instance = store.getInstance("test", marshaller, marshaller, false);
        }

        public void testAll() {
            instance.deleteBatch(List.of("key1"));
            instance.upsert("key1", "value1");
            assertEquals("value1", instance.query("key1"));
            instance.upsert("key1", "replace1");
            assertEquals("replace1", instance.query("key1"));

            instance.delete("key1");
            assertNull(instance.query("key1"));

            testQuery();
            testUpsert();
            testUpsertFunc();
            testGetAndUpsert();
            testGetAndUpsertFunc();
            testUpsertAndGet();
            testDelete();
            testGetAndDelete();
            testQueryBatch();
            testUpsertBatch();
            testTestUpsertBatch();
            testGetAndUpsertBatch();
            testGetAndUpsertBatchFunc();
            testUpsertAndGetBatch();
            testDeleteBatch();
            testGetAndDeleteBatch();
        }

        public void testQuery() {
            instance.delete("testQuery");
            assertNull(instance.query("testQuery"));
            instance.upsert("testQuery", "testQueryValue");
            assertEquals("testQueryValue", instance.query("testQuery"));
            instance.delete("testQuery");
            assertNull(instance.query("testQuery"));
        }

        public void testUpsert() {
            instance.delete("testUpsert");
            instance.upsert("testUpsert", "testUpsertValue");
            assertEquals("testUpsertValue", instance.query("testUpsert"));
            instance.upsert("testUpsert", "testUpsertValue2");
            assertEquals("testUpsertValue2", instance.query("testUpsert"));
            instance.upsert("testUpsert", (String) null);
            assertNull(instance.query("testUpsert"));
        }

        public void testUpsertFunc() {
            instance.delete("testUpsert");
            instance.upsert("testUpsert", (k, v) -> {
                assertNull(v);
                assertEquals("testUpsert", k);
                return "testUpsertValue";
            });
            assertEquals("testUpsertValue", instance.query("testUpsert"));
            instance.upsert("testUpsert", (k, v) -> {
                assertEquals("testUpsertValue", v);
                assertEquals("testUpsert", k);
                return "testUpsertValue2";
            });
            assertEquals("testUpsertValue2", instance.query("testUpsert"));
            instance.upsert("testUpsert", (k, v) -> {
                assertEquals("testUpsertValue2", v);
                assertEquals("testUpsert", k);
                return null;
            });
            assertNull(instance.query("testUpsert"));
        }

        public void testGetAndUpsert() {
            instance.delete("testGetAndUpsert");
            assertNull(instance.getAndUpsert("testGetAndUpsert", "testGetAndUpsertValue"));
            assertEquals("testGetAndUpsertValue", instance.query("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue", instance.getAndUpsert("testGetAndUpsert",
                    "testGetAndUpsertValue2"));
            assertEquals("testGetAndUpsertValue2", instance.query("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue2", instance.getAndUpsert("testGetAndUpsert",
                    (String) null));
            assertNull(instance.query("testGetAndUpsert"));
        }

        public void testGetAndUpsertFunc() {
            instance.delete("testGetAndUpsert");
            assertNull(instance.getAndUpsert("testGetAndUpsert", (k, v) -> {
                assertNull(v);
                assertEquals("testGetAndUpsert", k);
                return "testGetAndUpsertValue";
            }));
            assertEquals("testGetAndUpsertValue", instance.query("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue", instance.getAndUpsert("testGetAndUpsert", (k,
                                                                                             v) -> {
                assertEquals("testGetAndUpsertValue", v);
                assertEquals("testGetAndUpsert", k);
                return "testGetAndUpsertValue2";
            }));
            assertEquals("testGetAndUpsertValue2", instance.query("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue2", instance.getAndUpsert("testGetAndUpsert", (k,
                                                                                              v) -> {
                assertEquals("testGetAndUpsertValue2", v);
                assertEquals("testGetAndUpsert", k);
                return null;
            }));
            assertNull(instance.query("testGetAndUpsert"));
        }

        public void testUpsertAndGet() {
            instance.delete("testUpsertAndGet");
            assertEquals("testUpsertAndGetValue", instance.upsertAndGet("testUpsertAndGet", (k,
                                                                                             v) -> {
                assertNull(v);
                assertEquals("testUpsertAndGet", k);
                return "testUpsertAndGetValue";
            }));
            assertEquals("testUpsertAndGetValue", instance.query("testUpsertAndGet"));
            assertEquals("testUpsertAndGetValue2", instance.upsertAndGet("testUpsertAndGet", (k,
                                                                                              v) -> {
                assertEquals("testUpsertAndGetValue", v);
                assertEquals("testUpsertAndGet", k);
                return "testUpsertAndGetValue2";
            }));
            assertEquals("testUpsertAndGetValue2", instance.query("testUpsertAndGet"));
            assertNull(instance.upsertAndGet("testUpsertAndGet", (k, v) -> {
                assertEquals("testUpsertAndGetValue2", v);
                assertEquals("testUpsertAndGet", k);
                return null;
            }));
            assertNull(instance.query("testUpsertAndGet"));
        }

        public void testDelete() {
            instance.upsert("testDelete", "testDeleteValue");
            assertNotNull(instance.query("testDelete"));
            instance.delete("testDelete");
            assertNull(instance.query("testDelete"));
        }

        public void testGetAndDelete() {
            instance.upsert("testGetAndDelete", "testGetAndDeleteValue");
            assertNotNull(instance.query("testGetAndDelete"));
            assertEquals("testGetAndDeleteValue", instance.getAndDelete("testGetAndDelete"));
            assertNull(instance.query("testGetAndDelete"));
        }

        public void testQueryBatch() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            instance.upsertBatch(List.of("k1", "k2"), List.of("v1", "v2"));
            List<String> list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            instance.deleteBatch(List.of("k1", "k2"));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testUpsertBatch() {
            testQueryBatch();
        }

        public void testTestUpsertBatch() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            instance.upsertBatch(List.of("k1", "k2"), (k, v) -> {
                assertNull(v);
                if (k.equals("k1")) {
                    return "v1";
                }
                if (k.equals("k2")) {
                    return "v2";
                }
                throw new IllegalArgumentException();
            });
            List<String> list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            instance.upsertBatch(List.of("k1", "k2"), (k, v) -> {
                if (k.equals("k1")) {
                    assertEquals("v1", v);
                } else if (k.equals("k2")) {
                    assertEquals("v2", v);
                }
                return null;
            });
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testGetAndUpsertBatch() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            List<String> list = instance.getAndUpsertBatch(List.of("k1", "k2"), List.of("v1", "v2"
            ));
            assertNull(list.get(0));
            assertNull(list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            list = instance.getAndUpsertBatch(List.of("k1", "k2"), Arrays.asList(null, null));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testGetAndUpsertBatchFunc() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            List<String> list = instance.getAndUpsertBatch(List.of("k1", "k2"), (k, v) -> {
                assertNull(v);
                if (k.equals("k1")) {
                    return "v1";
                }
                if (k.equals("k2")) {
                    return "v2";
                }
                throw new IllegalArgumentException();
            });
            assertNull(list.get(0));
            assertNull(list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            list = instance.getAndUpsertBatch(List.of("k1", "k2"), (k, v) -> {
                if (k.equals("k1")) {
                    assertEquals("v1", v);
                } else if (k.equals("k2")) {
                    assertEquals("v2", v);
                }
                return null;
            });
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testUpsertAndGetBatch() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            List<String> list = instance.upsertAndGetBatch(List.of("k1", "k2"), (k, v) -> {
                assertNull(v);
                if (k.equals("k1")) {
                    return "v1";
                }
                if (k.equals("k2")) {
                    return "v2";
                }
                throw new IllegalArgumentException();
            });
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            list = instance.upsertAndGetBatch(List.of("k1", "k2"), (k, v) -> {
                if (k.equals("k1")) {
                    assertEquals("v1", v);
                } else if (k.equals("k2")) {
                    assertEquals("v2", v);
                }
                return null;
            });
            assertNull(list.get(0));
            assertNull(list.get(1));
            list = instance.queryBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testDeleteBatch() {
            // do nothing
        }

        public void testGetAndDeleteBatch() {
            List<String> list = instance.getAndDeleteBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
            instance.upsertBatch(List.of("k1", "k2"), List.of("v1", "v2"));
            list = instance.getAndDeleteBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            list = instance.getAndDeleteBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }
    }

    private static class StringMarshaller implements Marshaller<String> {
        public byte[] toBytes(String s) {
            return s.getBytes();
        }

        public String fromBytes(byte[] bytes) {
            return new String(bytes);
        }
    }
}
