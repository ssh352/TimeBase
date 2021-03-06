package deltix.samples.timebase.advanced;

import deltix.qsrv.hf.pub.*;
import deltix.qsrv.hf.pub.codec.*;
import deltix.qsrv.hf.pub.md.*;
import deltix.qsrv.hf.tickdb.pub.*;
import deltix.qsrv.hf.tickdb.pub.lock.*;
import deltix.qsrv.hf.tickdb.pub.query.*;
import deltix.timebase.messages.InstrumentMessage;
import deltix.util.memory.*;
import java.util.*;

/**
 *
 */
public class UpdateSecuritiesRawSample {
    /**
     *  A caching factory of encoders and decoders.
     */
    private final CodecFactory                          cfactory = 
        CodecFactory.newInterpretingCachingFactory ();
    
    /**
     *  Security stream.
     */
    private DXTickStream                                stream;
    
    /**
     *  Data will be loaded from the security stream into this collection.
     */
    private final ArrayList <RawMessage>                data = 
        new ArrayList <RawMessage> ();
    
    /**
     *  Reusable DataOutputStream-like object for writing data. Required by 
     *  message encoders.
     */
    private final MemoryDataOutput                      mdout = 
        new MemoryDataOutput ();
    
    /**
     *  Reusable DataInputStream-like object for reading data. Required by
     *  message decoders.
     */
    private final MemoryDataInput                       mdin = 
        new MemoryDataInput ();
    
    /**
     *  Reusable buffer for new messages.
     */
    private final RawMessage                            outMsg = 
        new RawMessage ();
    
    /**
     *  TimeBase API object for writing data into TimeBase.
     */
    private TickLoader                                  loader;
    
    public UpdateSecuritiesRawSample () {
    }
    
    public void             setStream (DXTickStream stream) {
        this.stream = stream;        
    }
    
    public void             loadData () {
        //
        //  Clear the collection, in case it is reused.
        //
        data.clear ();
        
        SelectionOptions            options = new SelectionOptions ();        
        //
        //  Read RAW messages. The default is to use the Bound API, which
        //  is not what we want (or need) in this example.
        //
        options.raw = true;         
        //
        //  Set up the cursor for very fast initialization. Slightly 
        //  sub-optimal read speed is not a factor for reading under 1M 
        //  messages.        
        //
        options.channelQOS = ChannelQualityOfService.MIN_INIT_TIME;
                                    
        
        InstrumentMessageSource     ims = 
            stream.select (TimeConstants.TIMESTAMP_UNKNOWN, options);
        
        try {
            while (ims.next ()) {
                //
                //  Make a deep copy of the message. Make sure you do not store
                //  a reference to the reusable buffer into the array list!
                //
                RawMessage          msg = 
                    (RawMessage) ims.getMessage ().clone();
                
                data.add (msg);                
            }
        } finally {
            ims.close ();
        }
        
        System.out.println ("Loaded " + data.size () + " records into memory.");
    }
    
    public void             storeData () {
        LoadingOptions  options = new LoadingOptions ();
        //
        //  Write RAW messages. The default is to use the Bound API, which
        //  is not what we want (or need) in this example.
        //
        options.raw = true;
        //
        //  Set up the loader for very fast initialization. Slightly 
        //  sub-optimal write speed is not a factor for writing under 1M 
        //  messages.        
        //
        options.channelQOS = ChannelQualityOfService.MIN_INIT_TIME;
        
        loader = stream.createLoader (options);

        try {            
            for (RawMessage oldMsg : data) 
                editAndStoreMessage (oldMsg);               
        } finally {
            loader.close ();
            loader = null;
        }
    }
    
    /**     
     *  Override this method to edit data. 
     * 
     *  @param concreteType     Message type.
     *  @param fieldName        The name of the field.
     *  @param in               Interface for extracting the old value.
     *  @param out              Interface for writing the new value.
     * 
     *  @return     true if data has been edited. false if data should be 
     *              carried over unchanged.
     */
    public boolean          editField (
        InstrumentMessage idAndTime,
        RecordClassDescriptor   concreteType, 
        String                  fieldName,
        ReadableValue           in,
        WritableValue           out
    )
    {
        return (false);
    }
    
    public static void      move (
        DataType                type,
        ReadableValue           in,
        WritableValue           out
    )
    {
        if (in.isNull ())
            out.writeNull ();        
        else if (
            type instanceof DateTimeDataType || 
            type instanceof IntegerDataType ||
            type instanceof TimeOfDayDataType
        )
            out.writeLong (in.getLong ());
        else if (type instanceof FloatDataType) {
            FloatDataType   ft = (FloatDataType) type;

            if (ft.isFloat ())
                out.writeFloat (in.getFloat ());
            else
                out.writeDouble (in.getDouble ());
        }
        else if (type instanceof BooleanDataType)
            out.writeBoolean (in.getBoolean ());
        else 
            out.writeString (in.getString ());             
    }
    
    public void         editAndStoreMessage (RawMessage oldMsg) {
        RecordClassDescriptor   type = oldMsg.type;
        //
        //  Decoder and encoder will be cached at codec factory level. 
        //  The following operations are efficient, even though the methods are
        //  called "create...".
        //
        UnboundDecoder          decoder = 
            cfactory.createFixedUnboundDecoder (type);
        
        FixedUnboundEncoder     encoder = 
            cfactory.createFixedUnboundEncoder (type);
        //
        //  Set up mdin to point to the byte array within oldMsg
        //
        oldMsg.setUpMemoryDataInput (mdin);
        //
        //  Set up decoder to read from mdin
        //
        decoder.beginRead (mdin);
        //
        //  Set up encoder to output into mdout
        //
        mdout.reset ();
        encoder.beginWrite (mdout);
        //
        //  Move identity and timestamp
        //
        outMsg.type = type;
        //outMsg.setInstrumentType(oldMsg.getInstrumentType());
        outMsg.setSymbol(oldMsg.getSymbol());
        outMsg.setTimeStampMs(oldMsg.getTimeStampMs());
        //
        //  Iterate over fields making changes if necessary
        //
        while (decoder.nextField ()) {
            boolean     encoderHasNext = encoder.nextField ();
            String      fieldName = decoder.getField ().getName ();
            //
            //  Encoder and decoder will iterate over fields in exactly the
            //  the same order, because they are created for the same exact 
            //  class type. The following assertions illustrate this.
            //  
            assert encoderHasNext;
            assert fieldName.equals (encoder.getField ().getName ());
            
            if (!editField (oldMsg, type, fieldName, decoder, encoder))
                move (decoder.getField ().getType (), decoder, encoder);
        }
        //
        //  Set up outMsg to point to the byte array within mdout
        //
        outMsg.setBytes (mdout, 0);
        //
        //  Send it to TimeBase
        //
        loader.send (outMsg);
    }
    
    public final void   editData () throws StreamLockedException { 
        DBLock              secStreamLock = null;
        
        //
        //  The securities stream should be locked when data is inserted. 
        //  Locking the securities stream achieves two goals:
        //      1) It ensures that no other application can accidentally 
        //          corrupt the data being inserted.
        //      2) Removing the lock also notifies interested applications
        //          about the fact that securities data has been changed.
        //          For instance, QuantOffice reloads symbols, 
        //          Aggregator checks if market data subscription should be
        //          updated, etc.
        //
        try {
            secStreamLock = stream.tryLock (LockType.WRITE, 30000);
            //
            //  Load data from the securities stream into memory.
            //
            loadData ();
            //
            //  Clear all data from the stream.
            //
            stream.clear ();
            //
            //  Store new copy of the data with required edits.
            //
            storeData ();
        } finally {
            //
            // Release the lock. This will generate the system event message
            // deltix.qsrv.hf.pub.EventMessage, which notifies interested 
            // parties that the stream has been updated. This message can be 
            // received from system stream 
            // deltix.qsrv.hf.tickdb.pub.TickDBFactory.EVENTS_STREAM_NAME
            //
            if (secStreamLock != null)
                secStreamLock.release ();
        }
    }
    
    public static void main (String [] args) throws Exception {
        //
        //  Create an instance of the editor that fills out a field called 
        //  "brokerID" (must be present in the schema for this to happen)
        //
        UpdateSecuritiesRawSample       x = 
            new UpdateSecuritiesRawSample () {
                @Override
                public boolean          editField (
                    InstrumentMessage idAndTime,
                    RecordClassDescriptor   concreteType,
                    String                  fieldName, 
                    ReadableValue           in,
                    WritableValue           out
                ) 
                {
                    if (fieldName.equals ("brokerID")) {
                        out.writeString ("TRADE." + idAndTime.getSymbol());
                        return (true);
                    }
                    else
                        return (false);
                }                               
            };
        
        DXTickDB                        tickdb = 
            TickDBFactory.createFromUrl ("dxtick://localhost");        
            
        tickdb.open (false);
        
        try {
            x.setStream (tickdb.getStream ("securities"));

            x.editData ();            
        } finally {
            tickdb.close ();
        }
    }
}
