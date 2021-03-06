package deltix.qsrv.testsetup;

import deltix.timebase.messages.InstrumentMessage;


import java.util.GregorianCalendar;

public class DataGenerator extends BaseGenerator<InstrumentMessage> {
    protected int count;
    protected deltix.timebase.api.messages.TradeMessage trade = new deltix.timebase.api.messages.TradeMessage();
    protected deltix.timebase.api.messages.BestBidOfferMessage bbo1 = new deltix.timebase.api.messages.BestBidOfferMessage();
    protected deltix.timebase.api.messages.BestBidOfferMessage bbo2 = new deltix.timebase.api.messages.BestBidOfferMessage();

    public DataGenerator(GregorianCalendar calendar, int interval, String ... symbols) {
        super(calendar, interval, symbols);

        trade.setPrice(nextDouble());
        trade.setSize(rnd.nextInt(1000));
        trade.setCurrencyCode((short)840);

        bbo1.setBidPrice(nextDouble());
        bbo1.setBidSize(rnd.nextInt(1000));

        bbo2.setOfferPrice(nextDouble());
        bbo2.setOfferSize(rnd.nextInt(1000));
    }

    public boolean next() {

        InstrumentMessage message;

        if (count % 10 == 0) {
            message = trade;
        } else {
            if (count % 2 == 0)
                message = bbo1;
            else
                message = bbo2;
        }

        message.setSymbol(symbols.next());
        message.setTimeStampMs(symbols.isReseted() ? getNextTime() : getActualTime());

        current = message;
        count++;
        return true;
    }

    public boolean isAtEnd() {
        return false;
    }
}
