package deltix.util.io.aeron;

import deltix.util.io.PollingThread;
import deltix.util.io.waitstrat.ParkWaitStrategy;
import deltix.util.io.waitstrat.WaitStrategy;
import deltix.util.lang.Pollable;
import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;

import java.io.EOFException;
import java.io.IOException;

@Deprecated
public class AeronPollableInputStream extends AeronInputStream implements Pollable {

    static final PollingThread          POLLER;

    static {
        synchronized (AeronPollableInputStream.class) {
            (POLLER = new PollingThread(new deltix.util.io.idlestrat.BusySpinIdleStrategy())).start();
        }
    }

    private final WaitStrategy          waitStrategy;

    AeronPollableInputStream(Aeron aeron, String channel, int streamId) {
        this(aeron, channel, streamId, new ParkWaitStrategy());
    }

    AeronPollableInputStream(
        Aeron aeron,
        String channel, int streamId,
        WaitStrategy waitStrategy)
    {
        super(aeron, channel, streamId);
        this.waitStrategy = waitStrategy;
    }

    @Override
    protected FragmentHandler           createSubscribeHandler() {
        return (buffer, offset, length, header) -> {
            putBytes(buffer, offset, length);
            wakeUp();
        };
    }

    public void                         poll() {
        subscription.poll(subscribeHandler, Integer.MAX_VALUE);
    }

    @Override
    public int                          read() throws IOException {
        while (getDataSize() == 0) {
            if (subscription.isClosed())
                throw new EOFException("Stream is closed");

            waitUnchecked();
        }

        return getByte();
    }

    @Override
    public int                          read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        while (getDataSize() == 0) {
            if (subscription.isClosed())
                throw new EOFException("Stream is closed");

            waitUnchecked();
        }

        return getBytes(b, off, len);
    }

    private void                        waitUnchecked() {
        try {
            waitStrategy.waitSignal();
        } catch(InterruptedException e){ }
    }

    private void                        wakeUp() {
        waitStrategy.signal();
    }

    @Override
    public void                         close() {
        super.close();
        POLLER.remove(this);
        wakeUp();
    }
}