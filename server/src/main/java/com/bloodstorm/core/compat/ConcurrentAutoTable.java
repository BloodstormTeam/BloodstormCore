package com.bloodstorm.core.compat;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.bloodstorm.core.util.UnsafeUtil.UNSAFE;

public class ConcurrentAutoTable implements Serializable {
    public void add( long x ) { add_if(  x); }
    public void decrement()   { add_if(-1L); }
    public void increment()   { add_if( 1L); }
    public void set( long x ) {
        CAT newcat = new CAT(null,4,x);
        // Spin until CAS works
        while( !CAS_cat(_cat,newcat) ) {/*empty*/}
    }
    public long get()       { return      _cat.sum(); }
    public int  intValue()  { return (int)_cat.sum(); }
    public long longValue() { return      _cat.sum(); }
    public long estimate_get( ) { return _cat.estimate_sum(); }
    public String toString() { return _cat.toString(); }
    public int internal_size() { return _cat._t.length; }
    private long add_if( long x ) { return _cat.add_if(x,hash(),this); }
    private volatile CAT _cat = new CAT(null,16/*Start Small, Think Big!*/,0L);
    private static final AtomicReferenceFieldUpdater<ConcurrentAutoTable,CAT> _catUpdater =
            AtomicReferenceFieldUpdater.newUpdater(ConcurrentAutoTable.class,CAT.class, "_cat");
    private boolean CAS_cat( CAT oldcat, CAT newcat ) { return _catUpdater.compareAndSet(this,oldcat,newcat); }
    private static int hash() {
        int h = System.identityHashCode(Thread.currentThread());
        return h << 3;
    }

    private static class CAT implements Serializable {

        // Unsafe crud: get a function which will CAS arrays
        private static final int _Lbase  = UNSAFE.arrayBaseOffset(long[].class);
        private static final int _Lscale = UNSAFE.arrayIndexScale(long[].class);
        private static long rawIndex(long[] ary, int i) {
            assert i >= 0 && i < ary.length;
            return _Lbase + (i * (long)_Lscale);
        }
        private static boolean CAS( long[] A, int idx, long old, long nnn ) {
            return UNSAFE.compareAndSwapLong( A, rawIndex(A,idx), old, nnn );
        }

        //volatile long _resizers;    // count of threads attempting a resize
        //static private final AtomicLongFieldUpdater<CAT> _resizerUpdater =
        //  AtomicLongFieldUpdater.newUpdater(CAT.class, "_resizers");

        private final CAT _next;
        private volatile long _fuzzy_sum_cache;
        private volatile long _fuzzy_time;
        private static final int MAX_SPIN=1;
        private final long[] _t;     // Power-of-2 array of longs

        CAT( CAT next, int sz, long init ) {
            _next = next;
            _t = new long[sz];
            _t[0] = init;
        }

        // Only add 'x' to some slot in table, hinted at by 'hash'.  The sum can
        // overflow.  Value is CAS'd so no counts are lost.  The CAS is attempted
        // ONCE.
        public long add_if( long x, int hash, ConcurrentAutoTable master ) {
            final long[] t = _t;
            final int idx = hash & (t.length-1);
            // Peel loop; try once fast
            long old = t[idx];
            final boolean ok = CAS( t, idx, old, old+x );
            if( ok ) return old;      // Got it
            // Try harder
            int cnt=0;
            while( true ) {
                old = t[idx];
                if( CAS( t, idx, old, old+x ) ) break; // Got it!
                cnt++;
            }
            if( cnt < MAX_SPIN ) return old; // Allowable spin loop count
            if( t.length >= 1024*1024 ) return old; // too big already

            // Too much contention; double array size in an effort to reduce contention
            //long r = _resizers;
            //final int newbytes = (t.length<<1)<<3/*word to bytes*/;
            //while( !_resizerUpdater.compareAndSet(this,r,r+newbytes) )
            //  r = _resizers;
            //r += newbytes;
            if( master._cat != this ) return old; // Already doubled, don't bother
            //if( (r>>17) != 0 ) {      // Already too much allocation attempts?
            //  // We could use a wait with timeout, so we'll wakeup as soon as the new
            //  // table is ready, or after the timeout in any case.  Annoyingly, this
            //  // breaks the non-blocking property - so for now we just briefly sleep.
            //  //synchronized( this ) { wait(8*megs); }         // Timeout - we always wakeup
            //  try { Thread.sleep(r>>17); } catch( InterruptedException e ) { }
            //  if( master._cat != this ) return old;
            //}

            CAT newcat = new CAT(this,t.length*2,0);
            // Take 1 stab at updating the CAT with the new larger size.  If this
            // fails, we assume some other thread already expanded the CAT - so we
            // do not need to retry until it succeeds.
            while( master._cat == this && !master.CAS_cat(this,newcat) ) {/*empty*/}
            return old;
        }


        // Return the current sum of all things in the table.  Writers can be
        // updating the table furiously, so the sum is only locally accurate.
        public long sum( ) {
            long sum = _next == null ? 0 : _next.sum(); // Recursively get cached sum
            final long[] t = _t;
            for( long cnt : t ) sum += cnt;
            return sum;
        }

        // Fast fuzzy version.  Used a cached value until it gets old, then re-up
        // the cache.
        public long estimate_sum( ) {
            // For short tables, just do the work
            if( _t.length <= 64 ) return sum();
            // For bigger tables, periodically freshen a cached value
            long millis = System.currentTimeMillis();
            if( _fuzzy_time != millis ) { // Time marches on?
                _fuzzy_sum_cache = sum(); // Get sum the hard way
                _fuzzy_time = millis;   // Indicate freshness of cached value
            }
            return _fuzzy_sum_cache;  // Return cached sum
        }

        public String toString( ) { return Long.toString(sum()); }
    }
}
