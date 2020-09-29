package deltix.qsrv.hf.tickdb.pub.query;

import deltix.qsrv.hf.tickdb.pub.TimeConstants;

/**
 *
 */
public interface TimeController {
    /**
     *  This method affects subsequent "add subscription" methods,
     *  such as, for instance, addEntity (). New subscriptions start at
     *  the specified time.
     *
     *  @param time     The time to use, or {@link TimeConstants#USE_CURRENT_TIME}
     *                  to use server's system time, or
     *                  {@link TimeConstants#USE_CURSOR_TIME} to use cursor's
     *                  last read time.
     */
    public void                 setTimeForNewSubscriptions (long time);

    /**
     *  Reposition the message source to a new point in time, while
     *  preserving current subscription.
     * 
     *  @param time     The new position in time.
     */
    public void                 reset (long time);
}
