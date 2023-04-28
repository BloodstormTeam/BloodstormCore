package com.bloodstorm.core.compat;

import com.bloodstorm.core.util.UnsafeUtil;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class FastestConcurrentMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Cloneable, Serializable {

    public static final Object TOMBSTONE = new Object();
    private static final int REPROVE_LIMIT = 10;
    private static final Unsafe UNSAFE = UnsafeUtil.UNSAFE;
    private static final int ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);
    private static final int ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(Object[].class);
    private static final int LOG = ARRAY_INDEX_SCALE == 4 ? 2 : (ARRAY_INDEX_SCALE == 8 ? 3 : 9999);
    private static final long KEYS_OFFSET;
    private static final int MIN_SIZE_LOG = 3;
    private static final int MIN_SIZE = (1 << MIN_SIZE_LOG);
    private static final Object NO_MATCH_OLD = new Object();
    private static final Object MATCH_ANY = new Object();
    private static final Prime TOMBPRIME = new Prime(TOMBSTONE);
    static volatile int DUMMY_VOLATILE;

    static {
        try {
            KEYS_OFFSET = UNSAFE.objectFieldOffset(FastestConcurrentMap.class.getDeclaredField("_kvs"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private transient Object[] _kvs;
    private transient long _last_resize_milli;
    // Count of reprobes

    /**
     * Create a new NonBlockingHashMap with default minimum size (currently set
     * to 8 K/V pairs or roughly 84 bytes on a standard 32-bit JVM).
     */
    public FastestConcurrentMap() {
        this(MIN_SIZE);
    }
    /**
     * Create a new NonBlockingHashMap with initial room for the given number of
     * elements, thus avoiding internal resizing operations to reach an
     * appropriate size.  Large numbers here when used with a small count of
     * elements will sacrifice space for a small amount of time gained.  The
     * initial size will be rounded up internally to the next larger power of 2.
     */
    public FastestConcurrentMap(final int initial_sz) {
        initialize(initial_sz);
    }

    private static long rawIndex(final Object[] ary, final int idx) {
        assert idx >= 0 && idx < ary.length;
        return ARRAY_BASE_OFFSET + ((long) idx << LOG);
    }

    private static int hash(final Object key) {
        int h = key.hashCode();
        h ^= (h >>> 20) ^ (h >>> 12);
        h ^= (h >>> 7) ^ (h >>> 4);
        h += h << 7;
        return h;
    }

    private static CHM chm(Object[] kvs) {
        return (CHM) kvs[0];
    }

    private static int[] hashes(Object[] kvs) {
        return (int[]) kvs[1];
    }

    // Number of K,V pairs in the table
    private static int len(Object[] kvs) {
        return (kvs.length - 2) >> 1;
    }

    private static Object key(Object[] kvs, int idx) {
        return kvs[(idx << 1) + 2];
    }

    private static Object val(Object[] kvs, int idx) {
        return kvs[(idx << 1) + 3];
    }

    private static boolean CAS_key(Object[] kvs, int idx, Object old, Object key) {
        return UNSAFE.compareAndSwapObject(kvs, rawIndex(kvs, (idx << 1) + 2), old, key);
    }

    private static boolean CAS_val(Object[] kvs, int idx, Object old, Object val) {
        return UNSAFE.compareAndSwapObject(kvs, rawIndex(kvs, (idx << 1) + 3), old, val);
    }

    // --- reprobe_limit -----------------------------------------------------
    // Heuristic to decide if we have reprobed toooo many times.  Running over
    // the reprobe limit on a 'get' call acts as a 'miss'; on a 'put' call it
    // can trigger a table resize.  Several places must have exact agreement on
    // what the reprobe_limit is, so we share it here.
    private static int reprobe_limit(int len) {
        return REPROVE_LIMIT + (len >> 4);
    }

    // --- NonBlockingHashMap --------------------------------------------------
    // Constructors

    private static boolean objectsEquals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    // --- keyeq ---------------------------------------------------------------
    // Check for key equality.  Try direct pointer compare first, then see if
    // the hashes are unequal (fast negative test) and finally do the full-on
    // 'equals' v-call.
    private static boolean keyeq(Object K, Object key, int[] hashes, int hash, int fullhash) {
        return
                K == key ||                 // Either keys match exactly OR
                        // hash exists and matches?  hash can be zero during the install of a
                        // new key/value pair.
                        ((hashes[hash] == 0 || hashes[hash] == fullhash) &&
                                // Do not call the users' "equals()" call with a Tombstone, as this can
                                // surprise poorly written "equals()" calls that throw exceptions
                                // instead of simply returning false.
                                K != TOMBSTONE &&        // Do not call users' equals call with a Tombstone
                                // Do the match the hard way - with the users' key being the loop-
                                // invariant "this" pointer.  I could have flipped the order of
                                // operands (since equals is commutative), but I'm making mega-morphic
                                // v-calls in a re-probing loop and nailing down the 'this' argument
                                // gives both the JIT and the hardware a chance to prefetch the call target.
                                key.equals(K));          // Finally do the hard match
    }

    private static final Object get_impl(final FastestConcurrentMap topmap, final Object[] kvs, final Object key) {
        final int fullhash = hash(key); // throws NullPointerException if key is null
        final int len = len(kvs); // Count of key/value pairs, reads kvs.length
        final CHM chm = chm(kvs); // The CHM, for a volatile read below; reads slot 0 of kvs
        final int[] hashes = hashes(kvs); // The memoized hashes; reads slot 1 of kvs

        int idx = fullhash & (len - 1); // First key hash

        // Main spin/reprobe loop, looking for a Key hit
        int reprobe_cnt = 0;
        while (true) {
            // Probe table.  Each read of 'val' probably misses in cache in a big
            // table; hopefully the read of 'key' then hits in cache.
            final Object K = key(kvs, idx); // Get key   before volatile read, could be null
            final Object V = val(kvs, idx); // Get value before volatile read, could be null or Tombstone or Prime
            if (K == null) return null;   // A clear miss

            // We need a volatile-read here to preserve happens-before semantics on
            // newly inserted Keys.  If the Key body was written just before inserting
            // into the table a Key-compare here might read the uninitialized Key body.
            // Annoyingly this means we have to volatile-read before EACH key compare.
            // .
            // We also need a volatile-read between reading a newly inserted Value
            // and returning the Value (so the user might end up reading the stale
            // Value contents).  Same problem as with keys - and the one volatile
            // read covers both.
            final Object[] newkvs = chm._newkvs; // VOLATILE READ before key compare

            // Key-compare
            if (keyeq(K, key, hashes, idx, fullhash)) {
                // Key hit!  Check for no table-copy-in-progress
                if (!(V instanceof Prime)) // No copy?
                    return (V == TOMBSTONE) ? null : V; // Return the value
                // Key hit - but slot is (possibly partially) copied to the new table.
                // Finish the copy & retry in the new table.
                return get_impl(topmap, chm.copy_slot_and_check(topmap, kvs, idx, key), key); // Retry in the new table
            }
            // get and put must have the same key lookup logic!  But only 'put'
            // needs to force a table-resize for a too-long key-reprobe sequence.
            // Check for too-many-reprobes on get - and flip to the new table.
            if (++reprobe_cnt >= reprobe_limit(len) || // too many probes
                    K == TOMBSTONE) // found a TOMBSTONE key, means no more keys in this table
                return newkvs == null ? null : get_impl(topmap, topmap.helpCopy(newkvs), key); // Retry in the new table

            idx = (idx + 1) & (len - 1);    // Reprobe by 1!  (could now prefetch)
        }
    }

    private static Object getk_impl(final FastestConcurrentMap topmap, final Object[] kvs, final Object key) {
        final int fullhash = hash(key); // throws NullPointerException if key is null
        final int len = len(kvs); // Count of key/value pairs, reads kvs.length
        final CHM chm = chm(kvs); // The CHM, for a volatile read below; reads slot 0 of kvs
        final int[] hashes = hashes(kvs); // The memoized hashes; reads slot 1 of kvs

        int idx = fullhash & (len - 1); // First key hash

        // Main spin/reprobe loop, looking for a Key hit
        int reprobe_cnt = 0;
        while (true) {
            // Probe table.
            final Object K = key(kvs, idx); // Get key before volatile read, could be null
            if (K == null) return null;   // A clear miss
            final Object[] newkvs = chm._newkvs; // VOLATILE READ before key compare

            // Key-compare
            if (keyeq(K, key, hashes, idx, fullhash))
                return K;              // Return existing Key!
            if (++reprobe_cnt >= reprobe_limit(len) || // too many probes
                    K == TOMBSTONE) { // found a TOMBSTONE key, means no more keys in this table
                return newkvs == null ? null : getk_impl(topmap, topmap.helpCopy(newkvs), key); // Retry in the new table
            }

            idx = (idx + 1) & (len - 1);    // Reprobe by 1!  (could now prefetch)
        }
    }

    private static Object putIfMatch0(final FastestConcurrentMap<?, ?> toMap, final Object[] keys, final Object key, final Object puttableValue, final Object expVal) {
        assert puttableValue != null;
        assert !(puttableValue instanceof Prime);
        assert !(expVal instanceof Prime);
        final int fullhash = hash(key); // throws NullPointerException if key null
        final int len = len(keys); // Count of key/value pairs, reads keys.length
        final CHM chm = chm(keys); // Reads keys[0]
        final int[] hashes = hashes(keys); // Reads keys[1], read before keys[0]
        int idx = fullhash & (len - 1);
        int reprobe_cnt = 0;
        Object K, V;
        Object[] newkvs = null;
        while (true) {
            V = val(keys, idx);
            K = key(keys, idx);
            if (K == null) {
                if (puttableValue == TOMBSTONE) return TOMBSTONE;
                if (expVal == MATCH_ANY) return TOMBSTONE;
                if (CAS_key(keys, idx, null, key)) {
                    chm._slots.add(1);
                    hashes[idx] = fullhash;
                    break;
                }
                int dummy = DUMMY_VOLATILE;
                continue;
            }
            newkvs = chm._newkvs;

            if (keyeq(K, key, hashes, idx, fullhash)) {
                break;
            }

            if (++reprobe_cnt >= reprobe_limit(len) || K == TOMBSTONE) {
                newkvs = chm.resize(toMap, keys);
                if (expVal != null) toMap.helpCopy(newkvs);
                return putIfMatch0(toMap, newkvs, key, puttableValue, expVal);
            }

            idx = (idx + 1) & (len - 1);
        }
        while (true) {
            if (puttableValue == V) return V;
            if (newkvs == null && ((V == null && chm.tableFull(reprobe_cnt, len)) || V instanceof Prime)) {
                newkvs = chm.resize(toMap, keys); // Force the new table copy to start
            }
            if (newkvs != null) {
                return putIfMatch0(toMap, chm.copy_slot_and_check(toMap, keys, idx, expVal), key, puttableValue, expVal);
            }

            assert !(V instanceof Prime);
            if (expVal != NO_MATCH_OLD && // Do we care about expected-Value at all?
                    V != expVal &&            // No instant match already?
                    (expVal != MATCH_ANY || V == TOMBSTONE || V == null) &&
                    !(V == null && expVal == TOMBSTONE) &&    // Match on null/TOMBSTONE combo
                    (expVal == null || !expVal.equals(V))) { // Expensive equals check at the last
                return (V == null) ? TOMBSTONE : V;         // Do not update!
            }
            if (CAS_val(keys, idx, V, puttableValue)) break;
            if (V instanceof Prime)
                return putIfMatch0(toMap, chm.copy_slot_and_check(toMap, keys, idx, expVal), key, puttableValue, expVal);
            int dummy = DUMMY_VOLATILE;
        }
        if (expVal != null) {
            if ((V == null || V == TOMBSTONE) && puttableValue != TOMBSTONE) chm._size.add(1);
            if (!(V == null || V == TOMBSTONE) && puttableValue == TOMBSTONE) chm._size.add(-1);
        }
        return (V == null && expVal != null) ? TOMBSTONE : V;
    }

    private boolean CAS_kvs(final Object[] oldkvs, final Object[] newkvs) {
        return UNSAFE.compareAndSwapObject(this, KEYS_OFFSET, oldkvs, newkvs);
    }

    private void initialize(int initial_sz) {
        RangeUtil.checkPositiveOrZero(initial_sz, "initial_sz");
        int i;                      // Convert to next largest power-of-2
        if (initial_sz > 1024 * 1024) initial_sz = 1024 * 1024;
        for (i = MIN_SIZE_LOG; (1 << i) < (initial_sz << 2); i++) ;
        // Double size for K,V pairs, add 1 for CHM and 1 for hashes
        _kvs = new Object[((1 << i) << 1) + 2];
        _kvs[0] = new CHM<>(new ConcurrentAutoTable()); // CHM in slot 0
        _kvs[1] = new int[1 << i];          // Matching hash entries
        _last_resize_milli = System.currentTimeMillis();
    }
    protected final void initialize() {
        initialize(MIN_SIZE);
    }

    @Override
    public int size() {
        return chm(_kvs).size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public V put(K key, V val) {
        return putIfMatch(key, val, NO_MATCH_OLD);
    }

    @Override
    public V putIfAbsent(@NotNull K key, V val) {
        return putIfMatch(key, val, TOMBSTONE);
    }

    @Override
    public V remove(Object key) {
        return putIfMatch(key, TOMBSTONE, NO_MATCH_OLD);
    }

    public boolean remove(@NotNull Object key, Object val) {
        return objectsEquals(putIfMatch(key, TOMBSTONE, val), val);
    }

    @Override
    public V replace(@NotNull K key, @NotNull V val) {
        return putIfMatch(key, val, MATCH_ANY);
    }

    @Override
    public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
        return objectsEquals(putIfMatch(key, newValue, oldValue), oldValue);
    }

    public final V putIfMatchAllowNull(Object key, Object newVal, Object oldVal) {
        if (oldVal == null) oldVal = TOMBSTONE;
        if (newVal == null) newVal = TOMBSTONE;
        final V res = (V) putIfMatch0(this, _kvs, key, newVal, oldVal);
        assert !(res instanceof Prime);
        //assert res != null;
        return res == TOMBSTONE ? null : res;
    }

    public final V putIfMatch(Object key, Object newVal, Object oldVal) {
        if (oldVal == null || newVal == null) throw new NullPointerException();
        final Object res = putIfMatch0(this, _kvs, key, newVal, oldVal);
        assert !(res instanceof Prime);
        assert res != null;
        return res == TOMBSTONE ? null : (V) res;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    @Override
    public void clear() {
        Object[] newkvs = new FastestConcurrentMap(MIN_SIZE)._kvs;
        while (!CAS_kvs(_kvs, newkvs));
    }

    @Override
    public boolean containsValue(final Object val) {
        if (val == null) throw new NullPointerException();
        for (V V : values())
            if (V == val || V.equals(val))
                return true;
        return false;
    }

    protected void rehash() {
    }

    @Override
    public Object clone() {
        try {
            FastestConcurrentMap<K, V> t = (FastestConcurrentMap<K, V>) super.clone();
            t.clear();
            for (K K : keySet()) {
                final V V = get(K);
                t.put(K, V);
            }
            return t;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    @Override
    public String toString() {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (!i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (; ; ) {
            Entry<K, V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (!i.hasNext())
                return sb.append('}').toString();
            sb.append(", ");
        }
    }

    @Override
    public V get(Object key) {
        final Object V = get_impl(this, _kvs, key);
        assert !(V instanceof Prime); // Never return a Prime
        assert V != TOMBSTONE;
        return (V) V;
    }

    private Object[] helpCopy(Object[] helper) {
        Object[] topkvs = _kvs;
        CHM topchm = chm(topkvs);
        if (topchm._newkvs == null) return helper; // No copy in-progress
        topchm.help_copy_impl(this, topkvs, false);
        return helper;
    }

    public Object[] rawArray() {
        return new SnapshotV()._sskvs;
    }

    public Enumeration<V> elements() {
        return new SnapshotV();
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override
            public void clear() {
                FastestConcurrentMap.this.clear();
            }

            @Override
            public int size() {
                return FastestConcurrentMap.this.size();
            }

            @Override
            public boolean contains(Object v) {
                return FastestConcurrentMap.this.containsValue(v);
            }

            @Override
            public Iterator<V> iterator() {
                return new SnapshotV();
            }
        };
    }

    public Enumeration<K> keys() {
        return new SnapshotK();
    }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<K>() {
            @Override
            public void clear() {
                FastestConcurrentMap.this.clear();
            }

            @Override
            public int size() {
                return FastestConcurrentMap.this.size();
            }

            @Override
            public boolean contains(Object k) {
                return FastestConcurrentMap.this.containsKey(k);
            }

            @Override
            public boolean remove(Object k) {
                return FastestConcurrentMap.this.remove(k) != null;
            }

            @Override
            public Iterator<K> iterator() {
                return new SnapshotK();
            }

            @Override
            public <T> T[] toArray(@NotNull T[] a) {
                Object[] kvs = rawArray();
                int sz = size();
                T[] r = a.length >= sz ? a : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), sz);
                int j = 0;
                for (int i = 0; i < len(kvs); i++) {
                    Object K = key(kvs, i);
                    Object V = Prime.unbox(val(kvs, i));
                    if (K != null && K != TOMBSTONE && V != null && V != TOMBSTONE) {
                        if (j >= r.length) {
                            int sz2 = (int) Math.min(Integer.MAX_VALUE - 8, ((long) j) << 1);
                            if (sz2 <= r.length) throw new OutOfMemoryError("Required array size too large");
                            r = Arrays.copyOf(r, sz2);
                        }
                        r[j++] = (T) K;
                    }
                }
                if (j <= a.length) {
                    if (a != r) System.arraycopy(r, 0, a, 0, j);
                    if (j < a.length) r[j++] = null;
                    return a;
                }
                return Arrays.copyOf(r, j);
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            @Override
            public void clear() {
                FastestConcurrentMap.this.clear();
            }

            @Override
            public int size() {
                return FastestConcurrentMap.this.size();
            }

            @Override
            public boolean remove(final Object o) {
                if (!(o instanceof Map.Entry)) return false;
                final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                return FastestConcurrentMap.this.remove(e.getKey(), e.getValue());
            }

            @Override
            public boolean contains(final Object o) {
                if (!(o instanceof Map.Entry)) return false;
                final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                V v = get(e.getKey());
                return v != null && v.equals(e.getValue());
            }

            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return new SnapshotE();
            }
        };
    }

    private static final class Prime {
        final Object _V;

        Prime(Object V) {
            _V = V;
        }

        static Object unbox(Object V) {
            return V instanceof Prime ? ((Prime) V)._V : V;
        }
    }

    // --- CHM -----------------------------------------------------------------
    // The control structure for the NonBlockingHashMap
    private static final class CHM<TypeK, TypeV> {
        private static final AtomicReferenceFieldUpdater<CHM, Object[]> _newkvsUpdater =
                AtomicReferenceFieldUpdater.newUpdater(CHM.class, Object[].class, "_newkvs");
        private static final AtomicLongFieldUpdater<CHM> _resizerUpdater =
                AtomicLongFieldUpdater.newUpdater(CHM.class, "_resizers");

        // ---
        // These next 2 fields are used in the resizing heuristics, to judge when
        // it is time to resize or copy the table.  Slots is a count of used-up
        // key slots, and when it nears a large fraction of the table we probably
        // end up reprobing too much.  Last-resize-milli is the time since the
        // last resize; if we are running back-to-back resizes without growing
        // (because there are only a few live keys but many slots full of dead
        // keys) then we need a larger table to cut down on the churn.
        static private final AtomicLongFieldUpdater<CHM> _copyIdxUpdater =
                AtomicLongFieldUpdater.newUpdater(CHM.class, "_copyIdx");
        static private final AtomicLongFieldUpdater<CHM> _copyDoneUpdater =
                AtomicLongFieldUpdater.newUpdater(CHM.class, "_copyDone");
        // Size in active K,V pairs
        private final ConcurrentAutoTable _size;
        // Count of used slots, to tell when table is full of dead unusable slots
        private final ConcurrentAutoTable _slots;
        // ---
        // New mappings, used during resizing.
        // The 'new KVs' array - created during a resize operation.  This
        // represents the new table being copied from the old one.  It's the
        // volatile variable that is read as we cross from one table to the next,
        // to get the required memory orderings.  It monotonically transits from
        // null to set (once).
        volatile Object[] _newkvs;
        // Sometimes many threads race to create a new very large table.  Only 1
        // wins the race, but the losers all allocate a junk large table with
        // hefty allocation costs.  Attempt to control the overkill here by
        // throttling attempts to create a new table.  I cannot really block here
        // (lest I lose the non-blocking property) but late-arriving threads can
        // give the initial resizing thread a little time to allocate the initial
        // new table.  The Right Long Term Fix here is to use array-lets and
        // incrementally create the new very large array.  In C I'd make the array
        // with malloc (which would mmap under the hood) which would only eat
        // virtual-address and not real memory - and after Somebody wins then we
        // could in parallel initialize the array.  Java does not allow
        // un-initialized array creation (especially of ref arrays!).
        volatile long _resizers; // count of threads attempting an initial resize
        // The next part of the table to copy.  It monotonically transits from zero
        // to _kvs.length.  Visitors to the table can claim 'work chunks' by
        // CAS'ing this field up, then copying the indicated indices from the old
        // table to the new table.  Workers are not required to finish any chunk;
        // the counter simply wraps and work is copied duplicately until somebody
        // somewhere completes the count.
        volatile long _copyIdx = 0;
        // Work-done reporting.  Used to efficiently signal when we can move to
        // the new table.  From 0 to len(oldkvs) refers to copying from the old
        // table to the new.
        volatile long _copyDone = 0;

        // ---
        // Simple constructor
        CHM(ConcurrentAutoTable size) {
            _size = size;
            _slots = new ConcurrentAutoTable();
        }

        public int size() {
            return (int) _size.get();
        }

        public int slots() {
            return (int) _slots.get();
        }

        // Set the _next field if we can.
        boolean CAS_newkvs(Object[] newkvs) {
            while (_newkvs == null)
                if (_newkvsUpdater.compareAndSet(this, null, newkvs))
                    return true;
            return false;
        }

        // --- tableFull ---------------------------------------------------------
        // Heuristic to decide if this table is too full, and we should start a
        // new table.  Note that if a 'get' call has reprobed too many times and
        // decided the table must be full, then always the estimate_sum must be
        // high and we must report the table is full.  If we do not, then we might
        // end up deciding that the table is not full and inserting into the
        // current table, while a 'get' has decided the same key cannot be in this
        // table because of too many reprobes.  The invariant is:
        //   slots.estimate_sum >= max_reprobe_cnt >= reprobe_limit(len)
        private boolean tableFull(int reprobe_cnt, int len) {
            return
                    // Do the cheap check first: we allow some number of reprobes always
                    reprobe_cnt >= REPROVE_LIMIT &&
                            (reprobe_cnt >= reprobe_limit(len) ||
                                    // More expensive check: see if the table is > 1/2 full.
                                    _slots.estimate_get() >= (len >> 1));
        }

        // --- resize ------------------------------------------------------------
        // Resizing after too many probes.  "How Big???" heuristics are here.
        // Callers will (not this routine) will 'help_copy' any in-progress copy.
        // Since this routine has a fast cutout for copy-already-started, callers
        // MUST 'help_copy' lest we have a path which forever runs through
        // 'resize' only to discover a copy-in-progress which never progresses.
        private Object[] resize(FastestConcurrentMap topmap, Object[] kvs) {
            assert chm(kvs) == this;

            // Check for resize already in progress, probably triggered by another thread
            Object[] newkvs = _newkvs; // VOLATILE READ
            if (newkvs != null)       // See if resize is already in progress
                return newkvs;           // Use the new table already

            // No copy in-progress, so start one.  First up: compute new table size.
            int oldlen = len(kvs);    // Old count of K,V pairs allowed
            int sz = size();          // Get current table count of active K,V pairs
            int newsz = sz;           // First size estimate

            // Heuristic to determine new size.  We expect plenty of dead-slots-with-keys
            // and we need some decent padding to avoid endless reprobing.
            if (sz >= (oldlen >> 2)) { // If we are >25% full of keys then...
                newsz = oldlen << 1;      // Double size, so new table will be between 12.5% and 25% full
                // For tables less than 1M entries, if >50% full of keys then...
                // For tables more than 1M entries, if >75% full of keys then...
                if (4L * sz >= ((oldlen >> 20) != 0 ? 3L : 2L) * oldlen)
                    newsz = oldlen << 2;    // Double double size, so new table will be between %12.5 (18.75%) and 25% (25%)
            }
            // This heuristic in the next 2 lines leads to a much denser table
            // with a higher reprobe rate
            //if( sz >= (oldlen>>1) ) // If we are >50% full of keys then...
            //  newsz = oldlen<<1;    // Double size

            // Last (re)size operation was very recent?  Then double again
            // despite having few live keys; slows down resize operations
            // for tables subject to a high key churn rate - but do not
            // forever grow the table.  If there is a high key churn rate
            // the table needs a steady state of rare same-size resize
            // operations to clean out the dead keys.
            long tm = System.currentTimeMillis();
            if (newsz <= oldlen && // New table would shrink or hold steady?
                    tm <= topmap._last_resize_milli + 10000)  // Recent resize (less than 10 sec ago)
                newsz = oldlen << 1;      // Double the existing size

            // Do not shrink, ever.  If we hit this size once, assume we
            // will again.
            if (newsz < oldlen) newsz = oldlen;

            // Convert to power-of-2
            int log2;
            for (log2 = MIN_SIZE_LOG; (1 << log2) < newsz; log2++) ; // Compute log2 of size
            long len = ((1L << log2) << 1) + 2;
            // prevent integer overflow - limit of 2^31 elements in a Java array
            // so here, 2^30 + 2 is the largest number of elements in the hash table
            if ((int) len != len) {
                log2 = 30;
                len = (1L << log2) + 2;
                if (sz > ((len >> 2) + (len >> 1))) throw new RuntimeException("Table is full.");
            }

            // Now limit the number of threads actually allocating memory to a
            // handful - lest we have 750 threads all trying to allocate a giant
            // resized array.
            long r = _resizers;
            while (!_resizerUpdater.compareAndSet(this, r, r + 1))
                r = _resizers;
            // Size calculation: 2 words (K+V) per table entry, plus a handful.  We
            // guess at 64-bit pointers; 32-bit pointers screws up the size calc by
            // 2x but does not screw up the heuristic very much.
            long megs = ((((1L << log2) << 1) + 8) << 3/*word to bytes*/) >> 20/*megs*/;
            if (r >= 2 && megs > 0) { // Already 2 guys trying; wait and see
                newkvs = _newkvs;        // Between dorking around, another thread did it
                if (newkvs != null)     // See if resize is already in progress
                    return newkvs;         // Use the new table already
                // TODO - use a wait with timeout, so we'll wakeup as soon as the new table
                // is ready, or after the timeout in any case.
                //synchronized( this ) { wait(8*megs); }         // Timeout - we always wakeup
                // For now, sleep a tad and see if the 2 guys already trying to make
                // the table actually get around to making it happen.
                try {
                    Thread.sleep(megs);
                } catch (Exception e) {
                }
            }
            // Last check, since the 'new' below is expensive and there is a chance
            // that another thread slipped in a new thread while we ran the heuristic.
            newkvs = _newkvs;
            if (newkvs != null)      // See if resize is already in progress
                return newkvs;          // Use the new table already

            // Double size for K,V pairs, add 1 for CHM
            newkvs = new Object[(int) len]; // This can get expensive for big arrays
            newkvs[0] = new CHM(_size); // CHM in slot 0
            newkvs[1] = new int[1 << log2]; // hashes in slot 1

            // Another check after the slow allocation
            if (_newkvs != null)     // See if resize is already in progress
                return _newkvs;         // Use the new table already

            // The new table must be CAS'd in so only 1 winner amongst duplicate
            // racing resizing threads.  Extra CHM's will be GC'd.
            if (CAS_newkvs(newkvs)) { // NOW a resize-is-in-progress!
                //notifyAll();            // Wake up any sleepers
                //long nano = System.nanoTime();
                //System.out.println(" "+nano+" Resize from "+oldlen+" to "+(1<<log2)+" and had "+(_resizers-1)+" extras" );
                //if( System.out != null ) System.out.print("["+log2);
                topmap.rehash();        // Call for Hashtable's benefit
            } else                    // CAS failed?
                newkvs = _newkvs;       // Reread new table
            return newkvs;
        }

        // --- help_copy_impl ----------------------------------------------------
        // Help along an existing resize operation.  We hope its the top-level
        // copy (it was when we started) but this CHM might have been promoted out
        // of the top position.
        private void help_copy_impl(FastestConcurrentMap topmap, Object[] oldkvs, boolean copy_all) {
            assert chm(oldkvs) == this;
            Object[] newkvs = _newkvs;
            assert newkvs != null;    // Already checked by caller
            int oldlen = len(oldkvs); // Total amount to copy
            final int MIN_COPY_WORK = Math.min(oldlen, 1024); // Limit per-thread work

            // ---
            int panic_start = -1;
            int copyidx = -9999;            // Fool javac to think it's initialized
            while (_copyDone < oldlen) { // Still needing to copy?
                // Carve out a chunk of work.  The counter wraps around so every
                // thread eventually tries to copy every slot repeatedly.

                // We "panic" if we have tried TWICE to copy every slot - and it still
                // has not happened.  i.e., twice some thread somewhere claimed they
                // would copy 'slot X' (by bumping _copyIdx) but they never claimed to
                // have finished (by bumping _copyDone).  Our choices become limited:
                // we can wait for the work-claimers to finish (and become a blocking
                // algorithm) or do the copy work ourselves.  Tiny tables with huge
                // thread counts trying to copy the table often 'panic'.
                if (panic_start == -1) { // No panic?
                    copyidx = (int) _copyIdx;
                    while (!_copyIdxUpdater.compareAndSet(this, copyidx, copyidx + MIN_COPY_WORK))
                        copyidx = (int) _copyIdx;      // Re-read
                    if (!(copyidx < (oldlen << 1)))  // Panic!
                        panic_start = copyidx;        // Record where we started to panic-copy
                }

                // We now know what to copy.  Try to copy.
                int workdone = 0;
                for (int i = 0; i < MIN_COPY_WORK; i++)
                    if (copy_slot(topmap, (copyidx + i) & (oldlen - 1), oldkvs, newkvs)) // Made an oldtable slot go dead?
                        workdone++;         // Yes!
                if (workdone > 0)      // Report work-done occasionally
                    copy_check_and_promote(topmap, oldkvs, workdone);// See if we can promote
                //for( int i=0; i<MIN_COPY_WORK; i++ )
                //  if( copy_slot(topmap,(copyidx+i)&(oldlen-1),oldkvs,newkvs) ) // Made an oldtable slot go dead?
                //    copy_check_and_promote( topmap, oldkvs, 1 );// See if we can promote

                copyidx += MIN_COPY_WORK;
                // Uncomment these next 2 lines to turn on incremental table-copy.
                // Otherwise this thread continues to copy until it is all done.
                if (!copy_all && panic_start == -1) // No panic?
                    return;       // Then done copying after doing MIN_COPY_WORK
            }
            // Extra promotion check, in case another thread finished all copying
            // then got stalled before promoting.
            copy_check_and_promote(topmap, oldkvs, 0);// See if we can promote
        }


        // --- copy_slot_and_check -----------------------------------------------
        // Copy slot 'idx' from the old table to the new table.  If this thread
        // confirmed the copy, update the counters and check for promotion.
        //
        // Returns the result of reading the volatile _newkvs, mostly as a
        // convenience to callers.  We come here with 1-shot copy requests
        // typically because the caller has found a Prime, and has not yet read
        // the _newkvs volatile - which must have changed from null-to-not-null
        // before any Prime appears.  So the caller needs to read the _newkvs
        // field to retry his operation in the new table, but probably has not
        // read it yet.
        private Object[] copy_slot_and_check(FastestConcurrentMap topmap, Object[] oldkvs, int idx, Object should_help) {
            assert chm(oldkvs) == this;
            Object[] newkvs = _newkvs; // VOLATILE READ
            // We're only here because the caller saw a Prime, which implies a
            // table-copy is in progress.
            assert newkvs != null;
            if (copy_slot(topmap, idx, oldkvs, _newkvs))   // Copy the desired slot
                copy_check_and_promote(topmap, oldkvs, 1); // Record the slot copied
            // Generically help along any copy (except if called recursively from a helper)
            return (should_help == null) ? newkvs : topmap.helpCopy(newkvs);
        }

        // --- copy_check_and_promote --------------------------------------------
        private void copy_check_and_promote(FastestConcurrentMap topmap, Object[] oldkvs, int workdone) {
            assert chm(oldkvs) == this;
            int oldlen = len(oldkvs);
            // We made a slot unusable and so did some of the needed copy work
            long copyDone = _copyDone;
            assert (copyDone + workdone) <= oldlen;
            if (workdone > 0) {
                while (!_copyDoneUpdater.compareAndSet(this, copyDone, copyDone + workdone)) {
                    copyDone = _copyDone; // Reload, retry
                    assert (copyDone + workdone) <= oldlen;
                }
            }

            // Check for copy being ALL done, and promote.  Note that we might have
            // nested in-progress copies and manage to finish a nested copy before
            // finishing the top-level copy.  We only promote top-level copies.
            if (copyDone + workdone == oldlen && // Ready to promote this table?
                    topmap._kvs == oldkvs &&       // Looking at the top-level table?
                    // Attempt to promote
                    topmap.CAS_kvs(oldkvs, _newkvs)) {
                topmap._last_resize_milli = System.currentTimeMillis(); // Record resize time for next check
            }
        }

        // --- copy_slot ---------------------------------------------------------
        // Copy one K/V pair from oldkvs[i] to newkvs.  Returns true if we can
        // confirm that we set an old-table slot to TOMBPRIME, and only returns after
        // updating the new table.  We need an accurate confirmed-copy count so
        // that we know when we can promote (if we promote the new table too soon,
        // other threads may 'miss' on values not-yet-copied from the old table).
        // We don't allow any direct updates on the new table, unless they first
        // happened to the old table - so that any transition in the new table from
        // null to not-null must have been from a copy_slot (or other old-table
        // overwrite) and not from a thread directly writing in the new table.
        private boolean copy_slot(FastestConcurrentMap topmap, int idx, Object[] oldkvs, Object[] newkvs) {
            // Blindly set the key slot from null to TOMBSTONE, to eagerly stop
            // fresh put's from inserting new values in the old table when the old
            // table is mid-resize.  We don't need to act on the results here,
            // because our correctness stems from box'ing the Value field.  Slamming
            // the Key field is a minor speed optimization.
            Object key;
            while ((key = key(oldkvs, idx)) == null)
                CAS_key(oldkvs, idx, null, TOMBSTONE);

            // ---
            // Prevent new values from appearing in the old table.
            // Box what we see in the old table, to prevent further updates.
            Object oldval = val(oldkvs, idx); // Read OLD table
            while (!(oldval instanceof Prime)) {
                final Prime box = (oldval == null || oldval == TOMBSTONE) ? TOMBPRIME : new Prime(oldval);
                if (CAS_val(oldkvs, idx, oldval, box)) { // CAS down a box'd version of oldval
                    // If we made the Value slot hold a TOMBPRIME, then we both
                    // prevented further updates here but also the (absent)
                    // oldval is vacuously available in the new table.  We
                    // return with true here: any thread looking for a value for
                    // this key can correctly go straight to the new table and
                    // skip looking in the old table.
                    if (box == TOMBPRIME)
                        return true;
                    // Otherwise we boxed something, but it still needs to be
                    // copied into the new table.
                    oldval = box;         // Record updated oldval
                    break;                // Break loop; oldval is now boxed by us
                }
                oldval = val(oldkvs, idx); // Else try, try again
            }
            if (oldval == TOMBPRIME) return false; // Copy already complete here!

            // ---
            // Copy the value into the new table, but only if we overwrite a null.
            // If another value is already in the new table, then somebody else
            // wrote something there and that write is happens-after any value that
            // appears in the old table.
            Object old_unboxed = ((Prime) oldval)._V;
            assert old_unboxed != TOMBSTONE;
            putIfMatch0(topmap, newkvs, key, old_unboxed, null);

            // ---
            // Finally, now that any old value is exposed in the new table, we can
            // forever hide the old-table value by slapping a TOMBPRIME down.  This
            // will stop other threads from uselessly attempting to copy this slot
            // (i.e., it's a speed optimization not a correctness issue).
            while (oldval != TOMBPRIME && !CAS_val(oldkvs, idx, oldval, TOMBPRIME))
                oldval = val(oldkvs, idx);

            return oldval != TOMBPRIME; // True if we slammed the TOMBPRIME down
        } // end copy_slot
    } // End of CHM

    // --- Snapshot ------------------------------------------------------------
    // The main class for iterating over the NBHM.  It "snapshots" a clean
    // view of the K/V array.
    private class SnapshotV implements Iterator<V>, Enumeration<V> {
        final Object[] _sskvs;
        private int _idx;              // Varies from 0-keys.length
        private Object _nextK, _prevK; // Last 2 keys found
        private V _nextV, _prevV; // Last 2 values found

        public SnapshotV() {
            while (true) {           // Verify no table-copy-in-progress
                Object[] topkvs = _kvs;
                CHM topchm = chm(topkvs);
                if (topchm._newkvs == null) { // No table-copy-in-progress
                    // The "linearization point" for the iteration.  Every key in this
                    // table will be visited, but keys added later might be skipped or
                    // even be added to a following table (also not iterated over).
                    _sskvs = topkvs;
                    break;
                }
                // Table copy in-progress - so we cannot get a clean iteration.  We
                // must help finish the table copy before we can start iterating.
                topchm.help_copy_impl(FastestConcurrentMap.this, topkvs, true);
            }
            // Warm-up the iterator
            next();
        }

        int length() {
            return len(_sskvs);
        }

        Object key(int idx) {
            return FastestConcurrentMap.key(_sskvs, idx);
        }

        public boolean hasNext() {
            return _nextV != null;
        }

        public V next() {
            // 'next' actually knows what the next value will be - it had to
            // figure that out last go-around lest 'hasNext' report true and
            // some other thread deleted the last value.  Instead, 'next'
            // spends all its effort finding the key that comes after the
            // 'next' key.
            if (_idx != 0 && _nextV == null) throw new NoSuchElementException();
            _prevK = _nextK;          // This will become the previous key
            _prevV = _nextV;          // This will become the previous value
            _nextV = null;            // We have no more next-key
            // Attempt to set <_nextK,_nextV> to the next K,V pair.
            // _nextV is the trigger: stop searching when it is != null
            while (_idx < length()) {  // Scan array
                _nextK = key(_idx++); // Get a key that definitely is in the set (for the moment!)
                if (_nextK != null && // Found something?
                        _nextK != TOMBSTONE &&
                        (_nextV = get(_nextK)) != null)
                    break;                // Got it!  _nextK is a valid Key
            }                         // Else keep scanning
            return _prevV;            // Return current value.
        }

        public void removeKey() {
            if (_prevV == null) throw new IllegalStateException();
            FastestConcurrentMap.this.putIfMatch(_prevK, TOMBSTONE, NO_MATCH_OLD);
            _prevV = null;
        }

        @Override
        public void remove() {
            // NOTE: it would seem logical that value removal will semantically mean removing the matching value for the
            // mapping <k,v>, but the JDK always removes by key, even when the value has changed.
            removeKey();
        }

        public V nextElement() {
            return next();
        }

        public boolean hasMoreElements() {
            return hasNext();
        }
    }

    // --- keySet --------------------------------------------------------------
    private class SnapshotK implements Iterator<K>, Enumeration<K> {
        final SnapshotV _ss;

        public SnapshotK() {
            _ss = new SnapshotV();
        }

        public void remove() {
            _ss.removeKey();
        }

        public K next() {
            _ss.next();
            return (K) _ss._prevK;
        }

        public boolean hasNext() {
            return _ss.hasNext();
        }

        public K nextElement() {
            return next();
        }

        public boolean hasMoreElements() {
            return hasNext();
        }
    }

    // --- entrySet ------------------------------------------------------------
    // Warning: Each call to 'next' in this iterator constructs a new NBHMEntry.
    private class NBHMEntry extends AbstractEntry<K, V> {
        NBHMEntry(final K k, final V v) {
            super(k, v);
        }

        public V setValue(final V val) {
            if (val == null) throw new NullPointerException();
            VALUE = val;
            return put(KEY, val);
        }
    }

    private class SnapshotE implements Iterator<Entry<K, V>> {
        final SnapshotV SNAPSHOT;

        public SnapshotE() {
            SNAPSHOT = new SnapshotV();
        }

        public void remove() {
            SNAPSHOT.removeKey();
        }

        public Map.Entry<K, V> next() {
            SNAPSHOT.next();
            return new NBHMEntry((K) SNAPSHOT._prevK, SNAPSHOT._prevV);
        }

        public boolean hasNext() {
            return SNAPSHOT.hasNext();
        }
    }
}
