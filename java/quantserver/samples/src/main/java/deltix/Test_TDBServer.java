package deltix;

import deltix.qsrv.hf.pub.md.*;
import deltix.qsrv.hf.tickdb.comm.client.TickDBClient;
import deltix.qsrv.hf.tickdb.lang.pub.Token;
import deltix.qsrv.hf.tickdb.pub.*;
import deltix.qsrv.hf.tickdb.pub.query.InstrumentMessageSource;
import deltix.qsrv.hf.tickdb.pub.query.Parameter;
import deltix.qsrv.hf.tickdb.pub.task.SchemaChangeTask;
import deltix.qsrv.hf.tickdb.pub.task.StreamChangeTask;
import deltix.qsrv.hf.tickdb.schema.MetaDataChange;
import deltix.qsrv.hf.tickdb.schema.SchemaAnalyzer;
import deltix.qsrv.hf.tickdb.schema.StreamMetaDataChange;

import deltix.timebase.messages.*;
import deltix.timebase.messages.trade.OrderType;
import deltix.timebase.messages.trade.TimeInForce;
import deltix.util.concurrent.CursorIsClosedException;
import deltix.util.lang.Util;
import deltix.util.parsers.CompilationException;
import deltix.util.time.Interval;
import deltix.util.time.Periodicity;
import deltix.util.time.TimeUnit;
import org.junit.*;

import java.io.IOException;
import java.util.*;


/**
 *  A number of remote TickDB tests.
 */
public class Test_TDBServer {

    static {
        //System.setProperty("jaxb.debug", "1");
    }

    static class Counter {

        private long count = 0;

        public synchronized long increment(long v) {
            return count += v;
        }

        public synchronized long value() {
            return count;
        }
    }

    private static DXTickDB db;

    @BeforeClass
    public static void start() throws Throwable {
        db = TickDBFactory.createFromUrl("dxtick://localhost:8011");
        db.open(false);
    }

    @AfterClass
    public static void stop() throws Throwable {
        db.close();
    }

    DXTickDB        getTickDb() {
        return db;
    }

    private DXTickStream    getBars () {
        DXTickDB tickdb = getTickDb();
        DXTickStream tickStream = tickdb.getStream(TickDBCreator.BARS_STREAM_KEY);
        if (tickStream == null)
            tickStream = TickDBCreator.createBarsStream (tickdb, TickDBCreator.BARS_STREAM_KEY);
        return tickStream;
    }

    private int             countMessages (TickCursor cursor) {
        try {
            int                 count = 0;

            while (cursor.next ())
                count++;

            return (count);
        } finally {
            Util.close(cursor);
        }
    }

//    @Test
//    public void testStreamCopy() throws InterruptedException {
//        DXTickDB tickdb = getTickDb();
//
//        DXTickStream bars = getBars();
//        StreamOptions options = bars.getStreamOptions();
//        options.name = "bgcopy";
//        DXTickStream stream = tickdb.createStream (options.name, options);
//
//        MetaDataChange change = Test_SchemaConverter.getChanges(stream, bars);
//
//        StreamCopyTask task = new StreamCopyTask(change);
//        task.sources = new String[] {"bars"};
//        stream.execute(task);
//
//        BackgroundProcessInfo process;
//        while ((process = stream.getBackgroundProcess()) != null && !process.isFinished())
//            Thread.sleep(100);
//
//        TickCursor cursor1 = null;
//        TickCursor cursor2 = null;
//        try {
//            cursor1 = bars.select(0, new SelectionOptions(false, false));
//            cursor2 = stream.select(0, new SelectionOptions(false, false));
//
//            while (true) {
//                if (cursor1.next() && cursor2.next())
//                    Test_SchemaConverter.checkEquals(cursor1.getMessage(), cursor2.getMessage());
//                else
//                    break;
//            }
//        } finally {
//            Util.close(cursor1);
//            Util.close(cursor2);
//        }
//    }

    @Test
    public void testRename() {
        DXTickDB tickdb = getTickDb();
        StreamOptions options = StreamOptions.fixedType(StreamScope.TRANSIENT, "2Rename", null, 0, StreamConfigurationHelper.mkUniversalBarMessageDescriptor());
        options.unique = true;

        DXTickStream stream = tickdb.createStream(options.name, options);


        TickLoader      loader = stream.createLoader ();
        GregorianCalendar calendar = new GregorianCalendar(2008, 1, 1);

        try {
            Random rnd = new Random();
            for(int i = 0; i < 5; i++)
            {
                BarMessage message = new BarMessage();
                message.setSymbol("ES1");
                calendar.add(Calendar.MINUTE, -1);
                message.setTimeStampMs(calendar.getTimeInMillis());

                message.setHigh(rnd.nextDouble()*100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        } finally {
            Util.close (loader);
        }

        stream.setName("zz");
        stream.rename("zz");

        stream = tickdb.createStream(options.name, options);
        System.out.println(stream.getName());
    }

    @Test
    public void testDeleteSymbols() {

        DXTickDB tickdb = getTickDb();
        StreamOptions options = StreamOptions.fixedType(StreamScope.DURABLE, "testDeleteSymbols", null, 0, StreamConfigurationHelper.mkUniversalBarMessageDescriptor());
        options.version = "5.0";

        DXTickStream stream = tickdb.createStream(options.name, options);

        GregorianCalendar calendar = new GregorianCalendar(2008, 1, 1);

        try (TickLoader      loader = stream.createLoader ()) {
            BarMessage message = new BarMessage();

            Random rnd = new Random();
            for(int i = 0; i < 5; i++)
            {
                message.setSymbol("ES" + i);
                calendar.add(Calendar.MINUTE, -1);
                message.setTimeStampMs(calendar.getTimeInMillis());

                message.setHigh(rnd.nextDouble()*100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        }

        stream.clear(new ConstantIdentityKey("ES3"));

        try (TickLoader      loader = stream.createLoader ()) {
            BarMessage message = new BarMessage();

            Random rnd = new Random();
            for(int i = 0; i < 5; i++)
            {
                message.setSymbol("ES" + i);

                message.setHigh(rnd.nextDouble()*100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        }

        IdentityKey[] entities = stream.listEntities();

        Optional<IdentityKey> id = Arrays.stream(entities).
                filter(x -> "ES3".equals(x.getSymbol().toString())).findFirst();
        Assert.assertTrue(id.isPresent());
    }

//    @Test
//    public void     testFlush() throws IOException, InterruptedException {
//        DXTickDB tickdb = getTickDb();
//        StreamOptions options = StreamOptions.fixedType(StreamScope.DURABLE, "flushme", null, 0,
//                StreamConfigurationHelper.mkUniversalBarMessageDescriptor());
//        options.version = "5.0";
//
//        DXTickStream stream = tickdb.createStream(options.name, options);
//
//        TickLoader      loader = stream.createLoader ();
//        GregorianCalendar calendar = new GregorianCalendar(2008, 1, 1);
//
//        try {
//            Random rnd = new Random();
//            BarMessage message = new BarMessage();
//            message.setSymbol("ES1");
//
//            for(int i = 0; i < 50_000; i++)
//            {
//                calendar.add(Calendar.MINUTE, 1);
//                message.setTimeStampMs(calendar.getTimeInMillis());
//
//                message.setHigh(rnd.nextDouble()*100);
//                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
//                message.setClose(message.getHigh() - rnd.nextDouble()*10);
//                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
//                message.setVolume(rnd.nextInt(10000));
//                message.setCurrencyCode((short)840);
//                loader.send(message);
//
//                if (i % 10000 == 0 && i > 0)
//                    ((Flushable)loader).flush();
//            }
//
//            File file = new File(runner.getLocation()).getParentFile();
//            Path data = Paths.get(file.getAbsolutePath(), "timebase", "flushme", "data");
//            if (Files.exists(data, LinkOption.NOFOLLOW_LINKS))
//                assertTrue(Files.exists(Paths.get(data.toString(), "z0000.dat"), LinkOption.NOFOLLOW_LINKS));
//            else
//                System.out.println("For Timebase 5.0 engine only");
//
//        } finally {
//            // do not close loader
//        }
//    }

    @Test
    public void testCompression() throws InterruptedException {
        TickDBClient client = new TickDBClient("localhost", 8011);
        client.setCompression(true);
        client.open(true);
        DXTickStream bars = client.getStream("bars");

        TickCursor cursor = bars.select(0, new SelectionOptions(true, false));
        while (cursor.next()) {
            cursor.getMessage();
        }

        cursor.close();
        client.close();
    }

    @Test
    public void testMessageOrder() {
        DXTickStream bars = getBars();

        SelectionOptions options = new SelectionOptions(false, false);
        options.ordered = true;

        IdentityKey[] keys1 = new IdentityKey[] {
            new ConstantIdentityKey("ORCL"),
            new ConstantIdentityKey("MSFT"),
            new ConstantIdentityKey("IBM"),
            new ConstantIdentityKey( "AAPL"),
        };

        IdentityKey[] keys2 = new IdentityKey[] {
            new ConstantIdentityKey("IBM"),
            new ConstantIdentityKey("MSFT"),
            new ConstantIdentityKey("ORCL"),
            new ConstantIdentityKey("AAPL"),
        };

        TickCursor cursor1 = bars.select(Long.MIN_VALUE, options, null, keys1);
        TickCursor cursor2 = bars.select(Long.MIN_VALUE, options, null, keys2);

        try {
            while (true) {
                if (cursor1.next() && cursor2.next())
                    Assert.assertEquals(cursor1.getMessage().toString(), cursor2.getMessage().toString());
                else
                    break;
            }
        } finally {
            Util.close(cursor1);
            Util.close(cursor2);
        }
    }

    @Test
    public void testGetStreams() {

        TickDBClient client = (TickDBClient) getTickDb();
        Assert.assertEquals(null, client.getStream("sdsd"));
        Assert.assertEquals(null, client.getStream("sdsd2"));
        Assert.assertEquals(null, client.getStream("sdsd3"));
    }

    public void testBGProcessSpeed() {
        TickDBClient client = (TickDBClient) getTickDb();

        String name = "bgtestme";
        DXTickStream stream = client.createStream (name, name, name, 0);

        for (int i = 0; i < 1000; i++) {
            BackgroundProcessInfo process = stream.getBackgroundProcess();
            if (process != null)
                System.out.println(process);
        }
    }

    @Test
    public void testTimeRange() {
        long[] range = getBars().getTimeRange(
            new ConstantIdentityKey("AA"),
            new ConstantIdentityKey("SSS")
        );

        Assert.assertTrue(range == null);
    }

    @Test
    public void testMD()
    {
        DXTickDB tdb = getTickDb();
        DXTickStream bars = tdb.getStream("bars");
        StreamOptions options = bars.getStreamOptions();
        options.name = "aaa";
        DXTickStream aaa = tdb.createStream("aaa", options);

        RecordClassDescriptor cd = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        aaa.setFixedType(cd);

        Assert.assertTrue(!bars.getFixedType().equals(cd));
    }

    @Test
    public void testRenameStreams()
    {
        DXTickDB tdb = getTickDb();

        DXTickStream stream = TickDBCreator.createBarsStream (tdb, "bars2");
        TickCursor cursor = null;

        try {
            cursor = stream.select(0, null);
            while (cursor.next());
        } catch (Exception e) {

        } finally {
            if (cursor != null)
                cursor.close();
        }

        stream.rename("T2estBars2");
        stream.setName("test");

        Assert.assertEquals("T2estBars2", tdb.getStream("T2estBars2").getKey());

        Assert.assertNotEquals(tdb.getStream("T2estBars2"), stream);

        try {
            cursor = stream.select(0, null);
            while (cursor.next());
        } catch (Exception e) {

        } finally {
            if (cursor != null)
                cursor.close();
        }

        tdb.createStream("bars2", new StreamOptions(StreamScope.DURABLE, "bars2", null, 0));

        stream.delete();
    }

    @Test
    public void testStreamChange() throws InterruptedException {
        DXTickDB tdb = getTickDb();

        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        String name = "toChange";

        DXTickStream stream = tdb.createStream(name,
            StreamOptions.fixedType(StreamScope.DURABLE, name, null, 0, classDescriptor));

        RecordClassDescriptor cd = StreamConfigurationHelper.mkBBOMessageDescriptor(
            null, false, null, null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        StreamChangeTask task = new StreamChangeTask();
        task.name = "toChange1";
        task.description = "toChange1 description";
        //options.highAvailability = true;
        task.periodicity = Periodicity.parse(Interval.create(1, TimeUnit.DAY));

        task.change = SchemaAnalyzer.DEFAULT.getChanges(stream.getStreamOptions().getMetaData(), MetaDataChange.ContentType.Fixed,
            new RecordClassSet(new RecordClassDescriptor[] {cd}), MetaDataChange.ContentType.Fixed);

        task.background = false;

        stream.execute(task);

        Thread.sleep(500);

        Assert.assertEquals(task.name, stream.getName());
        Assert.assertEquals(task.description, stream.getDescription());

        Assert.assertEquals(task.periodicity.toString(), stream.getPeriodicity().toString());
    }


    @Test
    public void testPeriodicityChange()
    {
        DXTickDB tdb = getTickDb();

        String name = "changePeriodicity";
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );
        StreamOptions options =  StreamOptions.fixedType(StreamScope.DURABLE, name, "periodicityTest", 0, classDescriptor);
        options.periodicity = Periodicity.mkStatic();
        DXTickStream stream = tdb.createStream(name, options);

        //check null interval
        Assert.assertEquals(null, stream.getPeriodicity().getInterval());

        //set 1D interval and check it
        stream.setPeriodicity(Periodicity.mkRegular(Interval.create(1, TimeUnit.DAY)));
        Assert.assertEquals("1D", stream.getPeriodicity().toString());

        //set irregular periodicity and check it
        stream.setPeriodicity(Periodicity.mkIrregular());
        Assert.assertEquals(null, stream.getPeriodicity().getInterval());
        Assert.assertEquals(Periodicity.Type.IRREGULAR, stream.getPeriodicity().getType());

        //set static periodicity and check it
        stream.setPeriodicity(Periodicity.mkStatic());
        Assert.assertEquals(null, stream.getPeriodicity().getInterval());
        Assert.assertEquals(Periodicity.Type.STATIC, stream.getPeriodicity().getType());
    }

    @Test
    public void testDeleteStream() throws InterruptedException {
        TickDBClient client = (TickDBClient)getTickDb();
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        RecordClassDescriptor classDescriptor1 = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(8)",
            "DECIMAL(0)"
        );

        String name = "testDeleteStream";

        client.createStream(name,
            StreamOptions.fixedType(StreamScope.DURABLE, name, null, 0, classDescriptor));

        client.listStreams();

        DXTickStream serverStream = getTickDb().getStream(name);
        serverStream.delete();

        serverStream = getTickDb().createStream (name,
            StreamOptions.fixedType(StreamScope.TRANSIENT, name, null, 0, classDescriptor1));

        Thread.sleep(500);

        DXTickStream clientStream = client.getStream(name);
        while (clientStream == null)
            clientStream = client.getStream(name);

        Assert.assertEquals(serverStream.getScope(), clientStream.getScope());
        Assert.assertEquals(serverStream.getFixedType().getGuid(), clientStream.getFixedType().getGuid());
    }

    public DXTickStream     createStream(DXTickDB db, String key, StreamOptions so) {
        DXTickStream stream = db.getStream(key);
        if (stream != null)
            stream.delete();

        return db.createStream(key, so);
    }

    @Test
    public void testNonSortedBars()
    {
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        DXTickDB db = getTickDb();
        DXTickStream stream = createStream(db, "bars1",
            StreamOptions.fixedType(StreamScope.DURABLE, "bars1", null, 0, classDescriptor));

        final int[] errors = new int[1];

        final LoadingErrorListener listener = new LoadingErrorListener() {
            public void onError(LoadingError e) {
                errors[0]++;
            }
        };

        LoadingOptions options = new LoadingOptions();
        options.addErrorAction(OutOfSequenceMessageException.class,
            LoadingOptions.ErrorAction.NotifyAndContinue);

        TickLoader      loader = stream.createLoader (options);
        loader.addEventListener(listener);
        GregorianCalendar calendar = new GregorianCalendar(2008, 1, 1);

        try {
            Random rnd = new Random();
            for(int i = 0; i < 5; i++)
            {
                BarMessage message = new BarMessage();
                message.setSymbol("ES1");
                calendar.add(Calendar.MINUTE, -1);
                message.setTimeStampMs(calendar.getTimeInMillis());

                message.setHigh(rnd.nextDouble()*100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        } finally {
            Util.close (loader);
        }
        stream.delete();

        Assert.assertEquals (4, errors[0]);
    }

    @Test
    public void testNonSortedBars2()
    {
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        DXTickDB db = getTickDb();

        DXTickStream stream = createStream(db, "bars1",
            StreamOptions.fixedType(StreamScope.DURABLE, "bars1", null, 0, classDescriptor));

        final Counter errors = new Counter();

        final LoadingErrorListener listener = new LoadingErrorListener() {
            public void onError(LoadingError e) {
                errors.increment(1);
            }
        };

        LoadingOptions options = new LoadingOptions();
        options.addErrorAction(OutOfSequenceMessageException.class,
            LoadingOptions.ErrorAction.NotifyAndAbort);

        TickLoader      loader = stream.createLoader (options);
        loader.addEventListener(listener);
        GregorianCalendar calendar = new GregorianCalendar(2008, 1, 1);

        try {
            Random rnd = new Random();
            for(int i = 0; i < 5; i++)
            {
                BarMessage message = new BarMessage();
                message.setSymbol("ES1");
                calendar.add(Calendar.MINUTE, -1);
                message.setTimeStampMs(calendar.getTimeInMillis());

                message.setHigh(rnd.nextDouble()*100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        } finally {
            Util.close (loader);
        }
        stream.delete();

        Assert.assertEquals (1, errors.value());
    }

    @Test
    public void testNonSortedBars1()
    {
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        DXTickDB db = getTickDb();

        DXTickStream stream = createStream(db, "bars1",
            StreamOptions.fixedType(StreamScope.DURABLE, "bars1", null, 0, classDescriptor));

        final int[] errors = new int[1];

        final LoadingErrorListener listener = new LoadingErrorListener() {
            public void onError(LoadingError e) {
                errors[0]++;
            }
        };

        LoadingOptions options = new LoadingOptions();
        options.addErrorAction(OutOfSequenceMessageException.class,
            LoadingOptions.ErrorAction.NotifyAndContinue);

        TickLoader      loader = stream.createLoader (options);
        loader.addEventListener(listener);
        GregorianCalendar calendar = new GregorianCalendar(2011, 1, 1);

        try {
            Random rnd = new Random();
            for (int i = 0; i < 5; i++)
            {
                BarMessage message = new BarMessage();
                message.setSymbol("ES1");
                message.setTimeStampMs(calendar.getTimeInMillis());
                message.setNanoTime((5 - i) * 100);

                message.setHigh(rnd.nextDouble() * 100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        } finally {
            Util.close (loader);
        }

        Assert.assertEquals(4, errors[0]);
    }

    @Test
    public void testHashCode()
    {
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
                null, "", null,
                "DECIMAL(4)",
                "DECIMAL(0)"
        );

        String name = "bbbb";

        DXTickDB db = getTickDb();

        DXTickStream stream = createStream(db, name,
                StreamOptions.fixedType(StreamScope.DURABLE, name, null, 0, classDescriptor));

        LoadingOptions options = new LoadingOptions();
        options.addErrorAction(OutOfSequenceMessageException.class,
                LoadingOptions.ErrorAction.NotifyAndContinue);

        TickLoader      loader = stream.createLoader (options);

        try {

            BarMessage msg = new BarMessage();
            msg.setSymbol("CME EDM0");
            msg.setClose(0.02);
            loader.send(msg);

            msg = new BarMessage();
            msg.setSymbol("CME EDU5");
            msg.setClose(0.01);
            loader.send(msg);

        } finally {
            Util.close (loader);
        }

        try (TickCursor cursor = stream.select(0, null)) {
            while (cursor.next())
                System.out.println(cursor.getMessage());
        }
    }

    @Test
    public void testTicksComponent()
    {
        RecordClassDescriptor classDescriptor = StreamConfigurationHelper.mkBarMessageDescriptor(
            null, "", null,
            "DECIMAL(4)",
            "DECIMAL(0)"
        );

        DXTickDB db = getTickDb();

        DXTickStream stream = createStream(db, "bars1",
            StreamOptions.fixedType(StreamScope.DURABLE, "bars1", null, 0, classDescriptor));

        final int[] errors = new int[1];

        final LoadingErrorListener listener = new LoadingErrorListener() {
            public void onError(LoadingError e) {
                errors[0]++;
            }
        };

        LoadingOptions options = new LoadingOptions();
        options.addErrorAction(OutOfSequenceMessageException.class,
            LoadingOptions.ErrorAction.NotifyAndContinue);

        TickLoader      loader = stream.createLoader (options);
        loader.addEventListener(listener);
        GregorianCalendar calendar = new GregorianCalendar(2011, 1, 1);

        try {
            Random rnd = new Random();
            for (int i = 0; i < 5; i++)
            {
                BarMessage message = new BarMessage();
                message.setSymbol("ES1");
                message.setNanoTime(calendar.getTimeInMillis() * TimeStamp.NANOS_PER_MS+(i * 100));
//                message.setTimeStampMs(calendar.getTimeInMillis());
//                message.setNanoTime((i * 100));

                message.setHigh(rnd.nextDouble() * 100);
                message.setOpen(message.getHigh() - rnd.nextDouble()*10);
                message.setClose(message.getHigh() - rnd.nextDouble()*10);
                message.setLow(Math.min(message.getOpen(), message.getClose()) - rnd.nextDouble()*10);
                message.setVolume(rnd.nextInt(10000));
                message.setCurrencyCode((short)840);
                loader.send(message);
            }
        } finally {
            Util.close (loader);
        }

        TickCursor cursor = stream.select(0, null);
        long ticks = 0;
        while (cursor.next()) {
            long time = cursor.getMessage().getNanoTime();
            //System.out.println(cursor.getMessage());
            Assert.assertTrue(time > ticks);
            ticks = time;
        }
        cursor.close();

        stream.delete();
    }

    @Test
    public void testResetSelect() {
        ConstantIdentityKey[] keys = new ConstantIdentityKey[] {
            new ConstantIdentityKey("ORCL"),
            new ConstantIdentityKey("IBM")
        };
        TickCursor cursor = getBars().select(0, null, null, keys);

        for (int i = 0; i < 100; i++) {
            cursor.next();
        }

        cursor.removeEntity(new ConstantIdentityKey("ORCL"));
        Assert.assertTrue(cursor.next());

        cursor.removeEntity(new ConstantIdentityKey("IBM"));

        cursor.reset(0);
        Assert.assertTrue(!cursor.next());

        cursor.close();
    }

    @Test
    public void testDelete() {
        ConstantIdentityKey[] keys = new ConstantIdentityKey[] {
                new ConstantIdentityKey("ORCL"),
                new ConstantIdentityKey("IBM")
        };
        DXTickStream bars = getBars();

        TickCursor cursor = bars.createCursor(new SelectionOptions(true, false));
        cursor.reset(0);

        bars.delete();

        try {
            cursor.addEntities(keys, 0, 2);
            cursor.next();
            Assert.assertFalse(true);
        } catch (CursorIsClosedException ex) {
            // valid case
        }

        cursor.close();
    }

    @Test
    public void testSelect() {
        DXTickDB db = getTickDb();
        DXTickStream stream1 = getBars();

        DXTickStream stream2 = createStream(db, "testSelect",
            StreamOptions.fixedType(StreamScope.DURABLE, "testSelect", "testSelect", 1, stream1.getFixedType()));

        Assert.assertTrue(stream1 != null);
        Assert.assertTrue(stream2 != null);

        TickCursor cursor = null;
        try {
            cursor = db.select(0, new SelectionOptions(), stream1, stream2);
            while (cursor.next());

            cursor.close();
            cursor = null;
        } finally {
            Util.close(cursor);
        }
    }

    @Test
    public void testSelect2() {
        DXTickDB db = getTickDb();

        RecordClassDescriptor base =
            StreamConfigurationHelper.mkMarketMessageDescriptor(840);

        RecordClassDescriptor rd1 = StreamConfigurationHelper.mkBBOMessageDescriptor(base,
            true, "", 840, FloatDataType.ENCODING_FIXED_DOUBLE, FloatDataType.ENCODING_FIXED_DOUBLE);
        RecordClassDescriptor rd2 = StreamConfigurationHelper.mkTradeMessageDescriptor(base,
            "", 840, FloatDataType.ENCODING_FIXED_DOUBLE, FloatDataType.ENCODING_FIXED_DOUBLE);

        String name = "testSelect2.stream1";
        DXTickStream stream1 = createStream(db, name,
            StreamOptions.polymorphic(StreamScope.DURABLE, name, null, 1, rd1, rd2));

        name = "testSelect2.stream2";
        DXTickStream stream2 = createStream(db, name,
            StreamOptions.polymorphic(StreamScope.DURABLE, name, null, 1, rd1, rd2));

        TDBRunner.BBOGenerator gn1 = new TDBRunner.BBOGenerator(null, 1000, 1000, "MSFT", "ORCL");

        Assert.assertTrue(stream1 != null);
        TickLoader loader1 = stream1.createLoader();
        while (gn1.next()) {
            loader1.send(gn1.getMessage());
        }
        loader1.close();

        TDBRunner.TradesGenerator gn2 =
            new TDBRunner.TradesGenerator(null, 1000, 1000, "MSFT", "ORCL");

        TickLoader loader2 = stream2.createLoader();
        while (gn2.next()) {
            loader2.send(gn2.getMessage());
        }
        loader2.close();

        TickCursor cursor = null;
        try {
            cursor = db.select(0, new SelectionOptions(), stream1, stream2);
            while (cursor.next());

            cursor.close();
            cursor = null;
        } finally {
            Util.close(cursor);
        }
    }

    @Test
    public void testSelect4() {
        DXTickDB db = getTickDb();

        RecordClassDescriptor base =
                StreamConfigurationHelper.mkMarketMessageDescriptor(840);

        RecordClassDescriptor rd1 = StreamConfigurationHelper.mkBBOMessageDescriptor(base,
                true, "", 840, FloatDataType.ENCODING_FIXED_DOUBLE, FloatDataType.ENCODING_FIXED_DOUBLE);
        RecordClassDescriptor rd2 = StreamConfigurationHelper.mkTradeMessageDescriptor(base,
                "", 840, FloatDataType.ENCODING_FIXED_DOUBLE, FloatDataType.ENCODING_FIXED_DOUBLE);

        String name = "testSelect2.stream1";
        DXTickStream stream1 = createStream(db, name,
                StreamOptions.polymorphic(StreamScope.DURABLE, name, null, 1, rd1, rd2));


        TDBRunner.BBOGenerator gn1 = new TDBRunner.BBOGenerator(null, 100, 100, "MSFT", "ORCL");

        Assert.assertTrue(stream1 != null);
        TickLoader loader1 = stream1.createLoader();
        while (gn1.next()) {
            BestBidOfferMessage msg = (BestBidOfferMessage) gn1.getMessage();
            msg.setBidPrice(Double.NaN);
            loader1.send(msg);
        }
        loader1.close();

        TickCursor cursor = null;
        try {
            cursor = db.select(0, new SelectionOptions(), stream1);
            while (cursor.next()) {
                BestBidOfferMessage msg = (BestBidOfferMessage) cursor.getMessage();
                Assert.assertTrue(Double.isNaN(msg.getBidPrice()));
            }

            cursor.close();
            cursor = null;
        } finally {
            Util.close(cursor);
        }
    }

//    @Ignore
//    public void             testStreamCache () throws InterruptedException {
//        long                firstVersion = runner.getServerDb().getStreamVersion ();
//
//        DXTickDB tdb = getTickDb();
//
//        ((TickDBClient) tdb).setStreamCacheTimeout (-1);
//
//        try {
//            assertEquals (firstVersion, tdb.getStreamVersion ());
//
//            TickStream []       ss = tdb.listStreams ();
//
//            int streamsCount = ss.length;
//            //assertEquals (1, ss.length);
//
//            //  Now create a stream on server
//            runner.getServerDb().createStream ("test", null, null, 1);
//
//            long                newVersion = runner.getServerDb().getStreamVersion ();
//
//            assertTrue (newVersion > firstVersion);
//
//            assertEquals (newVersion, tdb.getStreamVersion ());
//
//            ss = tdb.listStreams ();
//
//            assertEquals (streamsCount + 1, ss.length);
//
//            for (TickStream s : ss) {
//                if (Util.equals(s.getKey(), "test"))
//                    return;
//            }
//            assertTrue("Stream test is not found", true);
//        } finally {
//            ((TickDBClient) tdb).setStreamCacheTimeout (0);
//        }
//    }

    @Test
    public void             smoke () throws InterruptedException {
        TickCursor cursor = TickCursorFactory.create(getBars(), Long.MIN_VALUE, "GOOG", "AAPL");

        Assert.assertEquals (47450, countMessages (cursor));
    }

    @Test
    public void             testResetRightAway () throws InterruptedException {
        TickStream              s = getBars ();
        IdentityKey[]   ents = s.listEntities ();
        ArrayList <String>      symbols = new ArrayList <String> (ents.length);
        Random                  r = new Random (2009);
        //FeedFilter              filter = FeedFilter.createUnrestrictedNoSymbols ();
        TickCursor              c = s.select (0, null, null, new IdentityKey[0]);

        try {
            outer: for (;;) {
                symbols.clear ();

                for (IdentityKey id : ents)
                    symbols.add (id.getSymbol ().toString ());

                int                 num = symbols.size () / 2;

                c.clearAllEntities();

                IdentityKey[] ids = new IdentityKey[num];
                for (int ii = 0; ii < num; ii++)
                    ids[ii] = new ConstantIdentityKey(symbols.remove (r.nextInt (symbols.size ())));

                c.addEntities (ids, 0, ids.length);

                for (int ii = 0; ii < 2000; ii++) {
                    if (!c.next ())
                        break outer;
                }
            }
        } finally {
            c.close ();
        }
    }

    @Test
    public void testSelectAPI() {
        DXTickDB db = getTickDb();
        DXTickStream stream = getBars();

        IdentityKey[] ids = new IdentityKey[] {
            new ConstantIdentityKey("AAPL")
        };

        TickCursor cursor1 = db.select(0,
            new SelectionOptions(),
            new String[] {BarMessage.class.getName()}, new CharSequence[] { "AAPL"}, stream);

        TickCursor cursor2 = db.createCursor(new SelectionOptions(), stream);
        cursor2.setTypes(BarMessage.class.getName());
        cursor2.addEntities(ids, 0, ids.length);
        cursor2.reset(0);

        try {
            Assert.assertTrue(cursor1.next());
            Assert.assertTrue(cursor2.next());
            while (cursor1.next() && cursor2.next()) {
                Assert.assertEquals(cursor1.getMessage().toString(), cursor2.getMessage().toString());
            }
        } finally {
            Util.close(cursor1);
            Util.close(cursor2);
        }
    }

    @Test
    public void testQueriesCache() {
        DXTickDB db = getTickDb();

        DXTickStream bars = getBars();
        StreamOptions options = bars.getStreamOptions();
        options.name = "QueriesCache";
        DXTickStream stream = db.createStream (options.name, options);

        InstrumentMessageSource source = null;
        try {
            source = db.executeQuery("select * from " + options.name);
        } finally {
            Util.close(source);
        }

        stream.delete();
        db.createStream (options.name, options);

        try {
            source = db.executeQuery("select * from " + options.name);
        } finally {
            Util.close(source);
        }
    }

    @Test
    public void test1SelectAPI() {
        DXTickDB db = getTickDb();
        DXTickStream stream = getBars();

        TickCursor cursor1 = db.select(0,
                new SelectionOptions(),
                new String[] {BarMessage.class.getName()}, stream);

        TickCursor cursor2 = db.createCursor(new SelectionOptions(), stream);
        cursor2.reset(0);
        cursor2.setTypes(BarMessage.class.getName());
        cursor2.subscribeToAllEntities();

        try {
            Assert.assertTrue(cursor1.next());
            Assert.assertTrue(cursor2.next());
            while (cursor1.next() && cursor2.next()) {
                Assert.assertEquals(cursor1.getMessage().toString(), cursor2.getMessage().toString());
            }
        } finally {
            Util.close(cursor1);
            Util.close(cursor2);
        }
    }

    @Test
    public void test2SelectAPI() {
        DXTickDB db = getTickDb();
        DXTickStream stream = getBars();

        TickCursor cursor1 = db.select(0,
                new SelectionOptions(),
                stream);

        TickCursor cursor2 = db.createCursor(new SelectionOptions(), stream);
        cursor2.reset(0);
        cursor2.subscribeToAllTypes();
        cursor2.subscribeToAllEntities();

        try {
            Assert.assertTrue(cursor1.next());
            Assert.assertTrue(cursor2.next());
            while (cursor1.next() && cursor2.next()) {
                Assert.assertEquals(cursor1.getMessage().toString(), cursor2.getMessage().toString());
            }
        } finally {
            Util.close(cursor1);
            Util.close(cursor2);
        }
    }

    @Test
    public void testClosedCursor() throws InterruptedException {

        TickDBClient client = new TickDBClient("localhost", 8011);
        client.open(false);

        StreamOptions so = getBars().getStreamOptions();
        String name = so.name = "live_test";
        DXTickStream stream = createStream(client, name, so);

        final TickCursor cursor = client.createCursor(new SelectionOptions(true, true), stream);

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    cursor.reset(0);
                    while (cursor.next())
                        System.out.println(cursor.getMessage());
                } catch (Throwable e) {
                    // ok
                }
            }
        };
        thread.start();

        client.close();

        cursor.close();

        thread.join();
    }

    @Test
    public void testHighAvailability() throws IOException {

        getBars().setHighAvailability(true);
        Assert.assertTrue(getBars().getHighAvailability());
    }

    @Test
    public void testSimpleSelect() {
        DXTickDB db = getTickDb();

        TDBRunner.BarsGenerator g = new TDBRunner.BarsGenerator(
                new GregorianCalendar(2017, 0, 1), 1000, 100, "AAPL", "ORCL");

        StreamOptions so = getBars().getStreamOptions();
        so.name = "simple";

        DXTickStream stream = db.createStream(so.name, so);

        try (TickLoader loader = stream.createLoader()) {
            while (g.next())
                loader.send(g.getMessage());
        }

        CharSequence[] keys = new CharSequence[] {
                 "ORCL","AAPL"
        };

        TickCursor cursor1 = db.select(Long.MIN_VALUE,
            new SelectionOptions(),
            new String[] {BarMessage.class.getName()},
            keys,
            stream);

        //cursor1.addEntities(keys, 0, 2);

        try {
            Assert.assertTrue(cursor1.next());
            Assert.assertEquals("AAPL", cursor1.getMessage().getSymbol());
            Assert.assertTrue(cursor1.next());
            Assert.assertEquals("ORCL", cursor1.getMessage().getSymbol());

        } finally {
            Util.close(cursor1);
        }
    }

    @Test
    public void testQQL() {

        DXTickDB tickdb = getTickDb();
        DXTickStream source = getBars();

        StreamOptions so = source.getStreamOptions();
        String name = "QQL_TEST";
        DXTickStream target = tickdb.getStream(name);

        if (target != null)
            target.delete();

        so.name = name;
        target = tickdb.createStream(so.name, so);
        InstrumentMessageSource cursor;

        cursor = tickdb.executeQuery("select close from " + name);
        cursor.close();

        System.out.print("Changing schema... ");
        processSchemaChange(source, target);
        System.out.println(" done.");

        cursor = tickdb.executeQuery("select close from " + name);
        cursor.close();
    }

    @Test
    public void testCompileQuery() {
        TickDBClient tickdb = (TickDBClient)getTickDb();
        DXTickStream source = getBars();

        try {
            tickdb.compileQuery("select * from " + source.getKey() + " where open > 1z", new ArrayList<Token>());
            Assert.assertTrue("CompilationException expected", false);
        } catch (CompilationException e) {
            System.out.println(e.location);
        }
    }

    @Test
    public void         testQueryWithParams() throws Throwable {
        TickDBClient tickdb = (TickDBClient)getTickDb();
        DXTickStream source = getBars();

        try (InstrumentMessageSource cursor = tickdb.executeQuery("select * from " + source.getKey() + " where open > n", Parameter.INTEGER("n", 60)) )
        {
            int count = 0;
            while (cursor.next())
                count++;
            Assert.assertEquals(60139, count);
        }

        try (InstrumentMessageSource cursor = tickdb.executeQuery("select * from " + source.getKey() + " where open > n", Parameter.FLOAT("n", 60)) )
        {
            int count = 0;
            while (cursor.next())
                count++;
            Assert.assertEquals(60139, count);
        }
    }

    private static void            processSchemaChange(DXTickStream source, DXTickStream target) {
        StreamMetaDataChange change = SchemaAnalyzer.getChanges(source, target);

        // we can convert data
        target.execute(new SchemaChangeTask(change));
        BackgroundProcessInfo process;
        while ((process = target.getBackgroundProcess()) != null && !process.isFinished()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        }
    }

    @Test
    public void buildSchema() throws Introspector.IntrospectionException {

        Introspector it = Introspector.createEmptyMessageIntrospector();
        EnumClassDescriptor cd1 = it.introspectEnumClass(TimeInForce.class);
        EnumClassDescriptor cd2 = it.introspectEnumClass(OrderType.class);

        RecordClassSet set = new RecordClassSet();
        set.addClasses(cd1, cd2);
        StreamOptions options = StreamOptions.polymorphic(StreamScope.DURABLE, "orders", null, 0);
        options.setMetaData(true, set);

        getTickDb().createStream("orders", options);
    }

}
