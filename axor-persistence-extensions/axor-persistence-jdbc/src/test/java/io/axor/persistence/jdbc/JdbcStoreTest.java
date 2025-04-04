package io.axor.persistence.jdbc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.runtime.Serde;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JdbcStoreTest {

    @Test
    public void testMysql() {
        var config = ConfigFactory.parseResources("axor-persistence-jdbc-mysql.conf")
                .withFallback(ConfigFactory.parseResources("axor-persistence-jdbc.conf"))
                .withFallback(ConfigFactory.parseResources("axor-persistence.conf"))
                .resolve();
        System.out.println(config.origin());
        Testkit test = new Testkit(config);
        test.testAll();
    }

    @Test
    public void testPostgres() {
        var config = ConfigFactory.parseResources("axor-persistence-jdbc-postgres.conf")
                .withFallback(ConfigFactory.parseResources("axor-persistence-jdbc.conf"))
                .withFallback(ConfigFactory.parseResources("axor-persistence.conf"))
                .resolve();
        Testkit test = new Testkit(config);
        test.testAll();
    }

    public static class Testkit {
        private final JdbcStore store;
        private final Serde<String> serde = new StringSerde();
        private final JdbcStoreInstance<String, String> instance;

        public Testkit(Config config) {
            this.store = JdbcStore.get(config);
            this.instance = store.getInstance("test", serde, serde, false);
        }

        public void testAll() {
            instance.deleteBatch(List.of("key1"));
            instance.upsert("key1", "value1");
            assertEquals("value1", instance.get("key1"));
            instance.upsert("key1", "replace1");
            assertEquals("replace1", instance.get("key1"));

            instance.delete("key1");
            assertNull(instance.get("key1"));

            testGet();
            testUpsert();
            testUpsertFunc();
            testGetAndUpsert();
            testGetAndUpsertFunc();
            testUpsertAndGet();
            testDelete();
            testGetAndDelete();
            testGetBatch();
            testUpsertBatch();
            testTestUpsertBatch();
            testGetAndUpsertBatch();
            testGetAndUpsertBatchFunc();
            testUpsertAndGetBatch();
            testDeleteBatch();
            testGetAndDeleteBatch();
        }

        public void testGet() {
            instance.delete("testGet");
            assertNull(instance.get("testGet"));
            instance.upsert("testGet", "testGetValue");
            assertEquals("testGetValue", instance.get("testGet"));
            instance.delete("testGet");
            assertNull(instance.get("testGet"));
        }

        public void testUpsert() {
            instance.delete("testUpsert");
            instance.upsert("testUpsert", "testUpsertValue");
            assertEquals("testUpsertValue", instance.get("testUpsert"));
            instance.upsert("testUpsert", "testUpsertValue2");
            assertEquals("testUpsertValue2", instance.get("testUpsert"));
            instance.upsert("testUpsert", (String) null);
            assertNull(instance.get("testUpsert"));
        }

        public void testUpsertFunc() {
            instance.delete("testUpsert");
            instance.upsert("testUpsert", (k, v) -> {
                assertNull(v);
                assertEquals("testUpsert", k);
                return "testUpsertValue";
            });
            assertEquals("testUpsertValue", instance.get("testUpsert"));
            instance.upsert("testUpsert", (k, v) -> {
                assertEquals("testUpsertValue", v);
                assertEquals("testUpsert", k);
                return "testUpsertValue2";
            });
            assertEquals("testUpsertValue2", instance.get("testUpsert"));
            instance.upsert("testUpsert", (k, v) -> {
                assertEquals("testUpsertValue2", v);
                assertEquals("testUpsert", k);
                return null;
            });
            assertNull(instance.get("testUpsert"));
        }

        public void testGetAndUpsert() {
            instance.delete("testGetAndUpsert");
            assertNull(instance.getAndUpsert("testGetAndUpsert", "testGetAndUpsertValue"));
            assertEquals("testGetAndUpsertValue", instance.get("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue", instance.getAndUpsert("testGetAndUpsert",
                    "testGetAndUpsertValue2"));
            assertEquals("testGetAndUpsertValue2", instance.get("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue2", instance.getAndUpsert("testGetAndUpsert",
                    (String) null));
            assertNull(instance.get("testGetAndUpsert"));
        }

        public void testGetAndUpsertFunc() {
            instance.delete("testGetAndUpsert");
            assertNull(instance.getAndUpsert("testGetAndUpsert", (k, v) -> {
                assertNull(v);
                assertEquals("testGetAndUpsert", k);
                return "testGetAndUpsertValue";
            }));
            assertEquals("testGetAndUpsertValue", instance.get("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue", instance.getAndUpsert("testGetAndUpsert", (k,
                                                                                             v) -> {
                assertEquals("testGetAndUpsertValue", v);
                assertEquals("testGetAndUpsert", k);
                return "testGetAndUpsertValue2";
            }));
            assertEquals("testGetAndUpsertValue2", instance.get("testGetAndUpsert"));
            assertEquals("testGetAndUpsertValue2", instance.getAndUpsert("testGetAndUpsert", (k,
                                                                                              v) -> {
                assertEquals("testGetAndUpsertValue2", v);
                assertEquals("testGetAndUpsert", k);
                return null;
            }));
            assertNull(instance.get("testGetAndUpsert"));
        }

        public void testUpsertAndGet() {
            instance.delete("testUpsertAndGet");
            assertEquals("testUpsertAndGetValue", instance.upsertAndGet("testUpsertAndGet", (k,
                                                                                             v) -> {
                assertNull(v);
                assertEquals("testUpsertAndGet", k);
                return "testUpsertAndGetValue";
            }));
            assertEquals("testUpsertAndGetValue", instance.get("testUpsertAndGet"));
            assertEquals("testUpsertAndGetValue2", instance.upsertAndGet("testUpsertAndGet", (k,
                                                                                              v) -> {
                assertEquals("testUpsertAndGetValue", v);
                assertEquals("testUpsertAndGet", k);
                return "testUpsertAndGetValue2";
            }));
            assertEquals("testUpsertAndGetValue2", instance.get("testUpsertAndGet"));
            assertNull(instance.upsertAndGet("testUpsertAndGet", (k, v) -> {
                assertEquals("testUpsertAndGetValue2", v);
                assertEquals("testUpsertAndGet", k);
                return null;
            }));
            assertNull(instance.get("testUpsertAndGet"));
        }

        public void testDelete() {
            instance.upsert("testDelete", "testDeleteValue");
            assertNotNull(instance.get("testDelete"));
            instance.delete("testDelete");
            assertNull(instance.get("testDelete"));
        }

        public void testGetAndDelete() {
            instance.upsert("testGetAndDelete", "testGetAndDeleteValue");
            assertNotNull(instance.get("testGetAndDelete"));
            assertEquals("testGetAndDeleteValue", instance.getAndDelete("testGetAndDelete"));
            assertNull(instance.get("testGetAndDelete"));
        }

        public void testGetBatch() {
            instance.deleteBatch(List.of("k1", "k2", "k3"));
            instance.upsertBatch(List.of("k1", "k2"), List.of("v1", "v2"));
            List<String> list = instance.getBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            instance.deleteBatch(List.of("k1", "k2"));
            list = instance.getBatch(List.of("k1", "k2", "k3"));
            assertNull(list.get(0));
            assertNull(list.get(1));
            assertNull(list.get(2));
        }

        public void testUpsertBatch() {
            testGetBatch();
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
            List<String> list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            assertNull(list.get(2));
            list = instance.getAndUpsertBatch(List.of("k1", "k2"), Arrays.asList(null, null));
            assertEquals("v1", list.get(0));
            assertEquals("v2", list.get(1));
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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
            list = instance.getBatch(List.of("k1", "k2", "k3"));
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


}
