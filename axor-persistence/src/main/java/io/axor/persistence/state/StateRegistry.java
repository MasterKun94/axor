package io.axor.persistence.state;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.ServiceLoader.load;

public class StateRegistry {
    private static final Map<String, ValueStateFactory> VSF_MAP;
    private static final Map<String, EventSourceValueStateFactory> EVSF_MAP;
    private static final Map<String, MapStateFactory> MSF_MAP;
    private static final Map<String, EventSourceMapStateFactory> EMSF_MAP;

    static {
        Map<String, ValueStateFactory> vsfMap = new HashMap<>();
        for (ValueStateFactory factory : load(ValueStateFactory.class)) {
            vsfMap.put(factory.impl(), factory);
        }
        VSF_MAP = Collections.unmodifiableMap(vsfMap);
        EVSF_MAP = new HashMap<>();
        for (EventSourceValueStateFactory factory : load(EventSourceValueStateFactory.class)) {
            EVSF_MAP.put(factory.impl(), factory);
        }
        MSF_MAP = new HashMap<>();
        for (MapStateFactory factory : load(MapStateFactory.class)) {
            MSF_MAP.put(factory.impl(), factory);
        }
        EMSF_MAP = new HashMap<>();
        for (EventSourceMapStateFactory factory :
                load(EventSourceMapStateFactory.class)) {
            EMSF_MAP.put(factory.impl(), factory);
        }
    }

    public static <V, C> ValueState<V, C> getValueState(ValueStateDef<V, C> def) {
        if (def instanceof EventSourceValueStateDef<V, C> sdef) {
            EventSourceValueStateFactory factory = EVSF_MAP.get(def.getImpl());
            if (factory == null) {
                throw new IllegalArgumentException("No EventSourceValueStateFactory for " + def.getImpl());
            }
            return factory.create(sdef);
        } else {
            ValueStateFactory factory = VSF_MAP.get(def.getImpl());
            if (factory == null) {
                throw new IllegalArgumentException("No ValueStateFactory for " + def.getImpl());
            }
            return factory.create(def);
        }
    }

    public static <K, V, C> MapState<K, V, C> getMapState(MapStateDef<K, V, C> def) {
        if (def instanceof EventSourceMapStateDef<K, V, C> edef) {
            EventSourceMapStateFactory factory = EMSF_MAP.get(edef.getImpl());
            if (factory == null) {
                throw new IllegalArgumentException("No EventSourceMapStateFactory for " + def.getImpl());
            }
            return factory.create(edef);
        } else {
            MapStateFactory factory = MSF_MAP.get(def.getImpl());
            if (factory == null) {
                throw new IllegalArgumentException("No MapStateFactory for " + def.getImpl());
            }
            return factory.create(def);
        }
    }
}
