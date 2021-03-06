package deltix.samples.timebase.basics;

import deltix.qsrv.hf.pub.*;
import deltix.qsrv.hf.pub.md.*;
import deltix.qsrv.hf.tickdb.pub.*;
import deltix.qsrv.hf.tickdb.pub.query.*;
import deltix.timebase.api.messages.securities.GenericInstrument;
import deltix.timebase.messages.InstrumentMessage;

/**
 *  This sample illustrates the process of querying security metadata while
 *  applying QQL (QuantServer Query Language) filters. To prepare for running
 *  this test, start TimeBase with the sample database (available as an
 *  installation package).
 */
public class Step5_QueryData {
    static class QueryResult extends InstrumentMessage {
        public String               name;
    }

    /**
     *  List futures names, whose root symbol equals "ES". The string literal
     *  "ES" is passed inline in the where clause of the select statement.
     *  The result is received using the Bound API, i.e. in the form of native
     *  Java objects.
     */
    public static void listFuturesNamesByRootSymbolInline (DXTickDB db) {
        System.out.println ("listFuturesNamesByRootSymbolInline:");

        SelectionOptions            options = new SelectionOptions ();

        options.typeLoader = new SimpleTypeLoader (null, QueryResult.class);

        InstrumentMessageSource     cursor =
            db.executeQuery (
                "select name from securities where rootsymbol = 'ES'",
                options
            );

        try {
            while (cursor.next ()) {
                QueryResult         msg = (QueryResult) cursor.getMessage ();

                System.out.println ("    symbol: " + msg.getSymbol() + " name: " +  msg.name);
            }
        } finally {
            cursor.close ();
        }
    }

    /**
     *  List futures names, whose root symbol equals "NQ". The string literal
     *  "ES" is passed as a query parameter. This allows cleaner code,
     *  avoids the necessity to escape special characters, such as quotes, and
     *  allows the query engine to cache the statement.
     *  The result is received using the Bound API, i.e. in the form of native
     *  Java objects.
     */
    public static void listFuturesNamesByRootSymbolParam (DXTickDB db, String root) {
        System.out.println ("listFuturesNamesByRootSymbolParam (" + root + "):");

        SelectionOptions            options = new SelectionOptions ();

        options.typeLoader = new SimpleTypeLoader (null, QueryResult.class);

        Parameter                   rootSymbolParam =
            new Parameter ("rootSymbolParam", StandardTypes.CLEAN_VARCHAR);

        rootSymbolParam.value.writeString (root);

        InstrumentMessageSource     cursor =
            db.executeQuery (
                "select name from securities where rootsymbol = rootSymbolParam",
                options,
                rootSymbolParam
            );

        try {
            while (cursor.next ()) {
                QueryResult         msg = (QueryResult) cursor.getMessage ();

                System.out.println ("    symbol: " + msg.getSymbol() + " name: " +  msg.name);
            }
        } finally {
            cursor.close ();
        }
    }

    public static void listFuturesNamesByRollDateParam(DXTickDB db, long rollDate) {
        System.out.println ("listFuturesNamesByRollDateParam (" + rollDate + "):");

        SelectionOptions            options = new SelectionOptions ();

        options.typeLoader = new SimpleTypeLoader (null, QueryResult.class);

        Parameter                   rollDateParam =
                new Parameter ("rollDateParam", StandardTypes.CLEAN_INTEGER);

        rollDateParam.value.writeLong (rollDate);

        InstrumentMessageSource     cursor =
                db.executeQuery (
                        "select name from securities where rollDate > rollDateParam",
                        options,
                        rollDateParam
                );

        try {
            while (cursor.next ()) {
                QueryResult         msg = (QueryResult) cursor.getMessage ();

                System.out.println ("    symbol: " + msg.getSymbol() + " name: " +  msg.name);
            }
        } finally {
            cursor.close ();
        }
    }

    /**
     *  Find a specific security object by symbol and return it in its entirety,
     *  as opposed to a subset of its fields. This is the effect of <tt>select *</tt>.
     *  The result is received using the Bound API, i.e. in the form of a
     *  native Java object.
     */
    public static void listFuturesBySymbol (DXTickDB db, String symbol) {
        System.out.println ("listFuturesBySymbol (" + symbol + "):");
        
        Parameter                   symbolParam =
            new Parameter ("symbolParam", StandardTypes.CLEAN_VARCHAR);

        symbolParam.value.writeString (symbol);

        InstrumentMessageSource     cursor =
            db.executeQuery (
                "select * from securities where symbol = symbolParam",
                symbolParam
            );

        try {
            while (cursor.next ()) {
                GenericInstrument msg = (GenericInstrument) cursor.getMessage ();

                System.out.println ("    " + msg);
            }
        } finally {
            cursor.close ();
        }
    }

    public static void      main (String [] args) {
        if (args.length == 0)
            args = new String [] { "dxtick://localhost:8012" };

        DXTickDB    db = TickDBFactory.createFromUrl (args [0]);
        
        db.open (true);

        try {
            listFuturesNamesByRootSymbolInline (db);
            listFuturesNamesByRootSymbolParam(db, "NQ");
            listFuturesNamesByRollDateParam(db, System.currentTimeMillis());
            listFuturesBySymbol (db, "ESZ11");
        } finally {
            db.close ();
        }
    }
}
