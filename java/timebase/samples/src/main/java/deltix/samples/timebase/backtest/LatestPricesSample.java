package deltix.samples.timebase.backtest;

import deltix.qsrv.hf.blocks.InstrumentToObjectMap;
import deltix.timebase.api.messages.InstrumentMessage;
import deltix.qsrv.hf.tickdb.pub.DXTickDB;
import deltix.qsrv.hf.tickdb.pub.DXTickStream;
import deltix.qsrv.hf.tickdb.pub.SelectionOptions;
import deltix.qsrv.hf.tickdb.pub.TickDBFactory;
import deltix.qsrv.hf.tickdb.pub.query.InstrumentMessageSource;
import deltix.timebase.api.messages.BestBidOfferMessage;
import deltix.util.time.TimeKeeper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


public class LatestPricesSample {
    public static final long    REPORT_INTERVAL = 1000;

    static class PriceInfo {
        public double offerSize = 0;
        public double offerPrice = Double.NaN;

        public double bidSize = 0;
        public double bidPrice = Double.NaN;

        void        process(BestBidOfferMessage msg) {
            if (Double.isNaN(bidPrice)) {
                if (msg.getBidSize() > 0 && !Double.isNaN(msg.getBidPrice())) {
                    bidPrice = msg.getBidPrice();
                    bidSize = msg.getBidSize();
                }
            }

            if (Double.isNaN(offerPrice)) {
                if (msg.getOfferSize() > 0 && !Double.isNaN(msg.getOfferPrice())) {
                    offerPrice = msg.getOfferPrice();
                    offerSize = msg.getOfferSize();
                }
            }
        }

        boolean isComplete() {
            return !Double.isNaN(offerPrice) && !Double.isNaN(bidPrice);
        }
    }

    @SuppressFBWarnings(value = "SA_FIELD_SELF_COMPUTATION", justification = "Spotbugs can't see that TimeKeeper.currentTime is volatile")
    public static void      readData (DXTickDB db, String streamKey) {
        DXTickStream                            stream = 
            db.getStream (streamKey);
        
        //
        //  Create a tabel of indicators, one per instrument
        //
        InstrumentToObjectMap <PriceInfo>     prices =
            new InstrumentToObjectMap <> ();
        
        //
        //  InstrumentMessageSource is similar to a JDBC ResultSet
        //

        SelectionOptions options = new SelectionOptions(false, false);
        options.reversed = true;

        InstrumentMessageSource cursor = stream.createCursor (options);
        // subscribe BBO's only
        cursor.setTypes(BestBidOfferMessage.CLASS_NAME);
        
        cursor.reset (System.currentTimeMillis());          // read from current time
        cursor.subscribeToAllEntities ();

        //
        //  Measure read performance
        //
        long                    startTime = TimeKeeper.currentTime;
        long                    count = 0;        
        
        try {
            while (cursor.next ()) {
                InstrumentMessage   msg = cursor.getMessage ();

                if (msg instanceof BestBidOfferMessage) {
                    BestBidOfferMessage      bbo = (BestBidOfferMessage) msg;

                    PriceInfo info = prices.get (bbo);
                    
                    if (info == null) {
                        // System.out.println ("Creating a new price info for " + bbo.symbol + " ...");
                        
                        info = new PriceInfo ();
                        prices.put (bbo, info);
                    }
                    
                    info.process (bbo);

                    // waiting for both sides of BBOs
                    // unsubscribe when both side recieved
                    if (info.isComplete()) {
                        cursor.removeEntity(bbo);
                        System.out.println ("Removing instrument from subscription " + bbo.getSymbol() + " ...");
                    }

                }

                count++;
                
                long            now = TimeKeeper.currentTime;
                long            elapsed = now - startTime;
                
                if (elapsed >= REPORT_INTERVAL) {
                    System.out.println (String.format("%,9.0f messages/s\r", count * 1000.0 / elapsed));
                    startTime = now;
                    count = 0;
                }
            }
        } finally {
            cursor.close ();
        }

        System.out.println ("\nDone.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0)
            args = new String [] { "dxtick://localhost", "playback" };

        DXTickDB    db = TickDBFactory.createFromUrl (args [0]);

        db.open (true);

        try {
            readData (db, args [1]);
        } finally {
            db.close ();
        }
    }
}
