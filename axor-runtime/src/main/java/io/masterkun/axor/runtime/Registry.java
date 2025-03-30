package io.masterkun.axor.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class that provides a registry for service providers. This class allows the lookup,
 * creation, and listing of service provider implementations based on their class, priority, or
 * name.
 *
 * <p>Service providers must implement the {@code Provider} interface, which includes
 * methods to specify the priority, group, and name of the provider, as well as a method to create
 * an instance of the service.
 *
 * @see Provider
 */
public class Registry {

    private static final Map<Class<?>, ServiceLoader<?>> CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> List<T> cacheGet(Class<T> clazz) {
        return (List<T>) CACHE.computeIfAbsent(clazz, k -> ServiceLoader.load(clazz))
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    public static <T extends Provider<?>> T getByPriority(Class<T> clazz) {
        return cacheGet(clazz)
                .stream()
                .max(Comparator.comparing(Provider::priority))
                .orElseThrow(() -> new RuntimeException("No implementation found for " + clazz));
    }

    public static <T extends Provider<?>> T getByName(Class<T> clazz, String name) {
        return cacheGet(clazz)
                .stream()
                .filter(s -> s.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No implementation found for " + clazz +
                                                        " with name " + name));
    }

    @SuppressWarnings("unchecked")
    public static <T> T createByName(Class<?> clazz, String name) {
        assert Provider.class.isAssignableFrom(clazz);
        return getByName((Class<Provider<T>>) clazz, name).create();
    }

    @SuppressWarnings("unchecked")
    public static <T> T createByPriority(Class<?> clazz) {
        assert Provider.class.isAssignableFrom(clazz);
        return getByPriority((Class<Provider<T>>) clazz).create();
    }

    public static <T> List<T> listAvailable(Class<T> clazz) {
        return cacheGet(clazz);
    }
}
