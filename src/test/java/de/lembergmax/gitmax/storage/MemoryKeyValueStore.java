package de.lembergmax.gitmax.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** {@link KeyValueStore} im Arbeitsspeicher für Tests. */
public final class MemoryKeyValueStore implements KeyValueStore {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> get(
            final String key
    ) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(
            final String key,
            final String value
    ) {
        values.put(key, value);
    }

    @Override
    public void remove(
            final String key
    ) {
        values.remove(key);
    }

    /** Rohzugriff für Tests, die den gespeicherten Text prüfen oder manipulieren. */
    public Map<String, String> raw() {
        return values;
    }
}
