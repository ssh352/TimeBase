package deltix.qsrv.hf.tickdb.pub.channel;

import deltix.qsrv.hf.blocks.InstrumentSet;
import deltix.qsrv.hf.tickdb.pub.*;
import deltix.qsrv.hf.tickdb.pub.query.*;
import deltix.streaming.MessageChannel;
import deltix.timebase.messages.ConstantIdentityKey;
import deltix.timebase.messages.IdentityKey;
import deltix.timebase.messages.InstrumentMessage;
import deltix.util.concurrent.*;
import deltix.util.lang.*;

import java.util.*;

/**
 *
 */
public class MessageDistributor 
    implements IntermittentlyAvailableResource
{
    private final InstrumentMessageSource   source;
    private final ArrayList <ConstantIdentityKey> entities =
        new ArrayList <ConstantIdentityKey> ();

    public class ChannelLink
        implements
            EntitySubscriptionController,
            TypeSubscriptionController,
            StreamSubscriptionController,
            Disposable
    {
        private final MessageChannel<InstrumentMessage> delegate;
        private final int       id;
        private InstrumentSet   subscribedEntities = new InstrumentSet ();
        private int             entityIndexLength = 0;
        private final BitSet    entityIndex = new BitSet ();

        public ChannelLink (
            MessageChannel <InstrumentMessage>      delegate,
            int                                     id
        )
        {
            this.delegate = delegate;
            this.id = id;
        }

        public void close () {
            removeLink (id);
        }
        //
        // Subscription control methods
        //
        public void addEntities (IdentityKey[] ids, int offset, int length) {
            if (subscribedEntities == null) {
                assert entityIndexLength == 0;
                subscribedEntities = new InstrumentSet (length);
            }

            for (int ii = 0; ii < length; ii++)
                subscribedEntities.add (ids [offset + ii]);
        }

        public void addEntity (IdentityKey id) {
            if (subscribedEntities == null) {
                assert entityIndexLength == 0;
                subscribedEntities = new InstrumentSet ();
            }

            subscribedEntities.add (id);
        }

        public void clearAllEntities () {
            if (subscribedEntities == null) {
                assert entityIndexLength == 0;
                subscribedEntities = new InstrumentSet ();
            }
            else {
                subscribedEntities.clear ();
                entityIndexLength = 0;
            }
        }

        public void removeEntities (IdentityKey[] ids, int offset, int length) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void removeEntity (IdentityKey id) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void subscribeToAllEntities () {
            subscribedEntities = null;
            entityIndexLength = 0;
        }

        public void addTypes (String... names) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void removeTypes (String... names) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        @Override
        public void setTypes(String... names) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void subscribeToAllTypes () {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void addStream (TickStream... tickStreams) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void removeAllStreams () {
            throw new UnsupportedOperationException ("Not supported yet.");
        }

        public void removeStream (TickStream... tickStreams) {
            throw new UnsupportedOperationException ("Not supported yet.");
        }
        //
        //  End subscription control methods
        //
        private void    updateEntityIndex () {
            for (int numEnts = entities.size (); entityIndexLength < numEnts; entityIndexLength++) {
                entityIndex.set (
                    entityIndexLength,
                    subscribedEntities.contains (entities.get (entityIndexLength))
                );
            }
        }

        void        dispatchCurrent () {
            if (subscribedEntities != null) {
                updateEntityIndex ();
                
                if (!entityIndex.get (source.getCurrentEntityIndex ()))
                    return;
            }
        }
    }

    private final ArrayList <ChannelLink>   links =
        new ArrayList <ChannelLink> ();
    
    public MessageDistributor (InstrumentMessageSource source) {
        this.source = source;
    }

    private synchronized void removeLink (int id) {
        links.set (id, null);
    }

    public void             setAvailabilityListener (Runnable maybeAvailable) {
        source.setAvailabilityListener (maybeAvailable);
    }

    public boolean          dispatchOne () {
        if (!source.next ())
            return (false);

        assert source.getCurrentEntityIndex () <= entities.size ();

        if (source.getCurrentEntityIndex () == entities.size ())
            entities.add (new ConstantIdentityKey (source.getMessage ().getSymbol()));

        synchronized (this) {
            int                 n = links.size ();
            
            for (int ii = 0; ii < n; ii++)
                links.get (ii).dispatchCurrent ();
        }

        return (true);
    }
    
    public synchronized ChannelLink  createLink (
        MessageChannel <InstrumentMessage>  delegate
    )
    {
        int             id = links.indexOf (null);

        if (id < 0)
            id = links.size ();

        ChannelLink     link = new ChannelLink (delegate, id);

        links.add (id, link);
        
        return (link);
    }
}
