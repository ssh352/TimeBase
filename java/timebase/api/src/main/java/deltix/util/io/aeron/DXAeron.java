package deltix.util.io.aeron;

import deltix.util.io.waitstrat.ParkWaitStrategy;
import deltix.util.vsocket.VSProtocol;
import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SigIntBarrier;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.logging.Level;

@Deprecated
public final class DXAeron {
    private static MediaDriver              DRIVER;
    private static Aeron                    AERON;
    private static String                   AERON_DIR;

    private static int                      usages = 0;

    private DXAeron() {
        throw new RuntimeException("Not for you!");
    }

    public synchronized static void         start(String aeronDir, boolean startMediaDriver) {
        ++usages;

        if (startMediaDriver)
            startMediaDriver(aeronDir);

        startAeron(aeronDir);

        DXAeron.AERON_DIR = aeronDir;
    }

    public synchronized static InputStream  createInputStream(int id) {
        if (AERON == null)
            throw new IllegalStateException("Aeron not connected.");

        AeronPollableInputStream is =
            new AeronPollableInputStream(
                AERON, CommonContext.IPC_CHANNEL, id, new ParkWaitStrategy());
        AeronPollableInputStream.POLLER.add(is);
        return is;
        //return new AeronInputStream(DXAeron.getAeron(), CommonContext.IPC_CHANNEL, id, new NoOpIdleStrategy());
    }

    public synchronized static String       getAeronDir() {
        return AERON_DIR;
    }

    public synchronized static OutputStream createOutputStream(int id) {
        if (AERON == null)
            throw new IllegalStateException("Aeron not connected.");

        return new AeronOutputStream(AERON, CommonContext.IPC_CHANNEL, id);
    }

    private static void                     startMediaDriver(String aeronDir) {
        if (DRIVER == null) {
            final MediaDriver.Context context = new MediaDriver.Context();

            //* min latency
            context.threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .conductorIdleStrategy(new BackoffIdleStrategy(1, 1, 1, 1))
                .receiverIdleStrategy(new NoOpIdleStrategy())
                .senderIdleStrategy(new NoOpIdleStrategy())
                .sharedIdleStrategy(new NoOpIdleStrategy());
            //*/

            context.aeronDirectoryName(aeronDir);
            DRIVER = MediaDriver.launchEmbedded(context);
        }
    }

    private static void                     startAeron(String aeronDir) {
        if (AERON == null) {
            Aeron.Context context = new Aeron.Context();
            context.aeronDirectoryName(aeronDir);
            AERON = Aeron.connect(context);
        }
    }

    public synchronized static void         shutdown() {
        --usages;

        if (usages < 0)
            VSProtocol.LOGGER.log(Level.SEVERE, "Aeron usages violated: " + usages, new Exception());

        assert usages >=0;

        if (usages <= 0) {
            if (AERON != null) {
                AERON.close();
                AERON = null;
            }
            if (DRIVER != null) {
                DRIVER.close();
                DRIVER = null;
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        DXAeron.start(Paths.get("aeron").toAbsolutePath().toString(), true);
        new SigIntBarrier().await();
        DXAeron.shutdown();
    }
}
