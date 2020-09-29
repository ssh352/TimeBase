package deltix.qsrv.dtb.store.dataacc;

import deltix.qsrv.dtb.store.pub.*;

/**
 *  Provides access to all data in the associated root belonging to a specific time slice.
 * 
 *  <p>The methods of TimeSlice are valid ONLY while it's checked out. The entity
 *  to which a TimeSlice can be checked out is called <I>accessor</i> and is
 *  represented by the {@link DAPrivate} interface. Checking out
 *  a TimeSlice guarantees the validity of the object. Accessing a TimeSlice 
 *  after it's checked in is illegal (the object may be destroyed or reused).
 *  While a TimeSlice is checked out, its data can be added, removed; it can be
 *  split into multiple TimeSlices, or merged with adjacent TimeSlices. However,
 *  the accessors that have checked the TimeSlice out are notified of such changes.
 *  </p>
 * 
 *  <p>A large subset of the methods, called Direct Access Methods, also
 *  requires obtaining a read or write lock by the accessor. Holding a read lock
 *  prevents modifications to TimeSlice, and holding a write lock prevents
 *  other accessors from reading or modifying it.
 *  </p>
 */
public interface TimeSlice {
    public TimeSliceStore   getStore ();
    
    public void             checkIn (DAPrivate accessor);
    
    /**
     *  The first timestamp covered by this time slice, inclusive. Requires
     *  a lock.
     */
    public long             getStartTimestamp ();
    
    /**
     *  The limit timestamp NOT covered by this time slice, exclusive. Requires
     *  a lock.
     */
    public long             getLimitTimestamp ();
    
    /**
     *  Retrieves or creates a block for the specified entity. Requires an
     *  appropriate lock.
     * 
     *  @param entity
     *  @param create
     *  @return valid DataBlock
     */
    public DataBlock        getBlock (
        int                     entity,
        boolean                 create
    );

    public void             insertNotify (
        EntityFilter            loadHint,
        DAPrivate               accessor, 
        long                    timestamp, 
        int                     addlLength
    )
        throws SwitchTimeSliceException;

    public void             blockGoesDirty (DataBlock db);

    public void             processBlocks (
        EntityFilter            filter, 
        BlockProcessor          bp
    );             

    public void             dataInserted (DAPrivate accessor, DataBlock db, int dataOffset, int msgLength, long timestamp);

    public void             dataDropped (DAPrivate accessor, DataBlock db, int dataOffset, int length, long timestamp);
    
    public void             truncate(long timestamp, int entity, DataAccessorBase accessor);

    public void             cut(long[] range, int[] entities, DataAccessorBase accessor);

    public void             cut(long startTime, long endTime, DataAccessorBase accessor);
}
