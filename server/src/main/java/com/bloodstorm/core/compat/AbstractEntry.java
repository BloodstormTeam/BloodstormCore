package com.bloodstorm.core.compat;

import java.util.Map;
import java.util.Objects;

abstract class AbstractEntry<K, V> implements Map.Entry<K, V> {
    protected final K KEY;
    protected V VALUE;

    public AbstractEntry(final K key, final V val) {
        KEY = key;
        VALUE = val;
    }

    /**
     * Return "key=val" string
     */
    public String toString() {
        return KEY + "=" + VALUE;
    }

    public K getKey() {
        return KEY;
    }

    public V getValue() {
        return VALUE;
    }

    public boolean equals(final Object o) {
        if (!(o instanceof Map.Entry)) return false;
        final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
        return eq(KEY, e.getKey()) && eq(VALUE, e.getValue());
    }

    public int hashCode() {
        return ((KEY == null) ? 0 : KEY.hashCode()) ^
                        ((VALUE == null) ? 0 : VALUE.hashCode());
    }

    private static boolean eq(final Object o1, final Object o2) {
        return (Objects.equals(o1, o2));
    }
}
