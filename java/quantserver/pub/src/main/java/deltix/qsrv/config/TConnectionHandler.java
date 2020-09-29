package deltix.qsrv.config;

import deltix.util.collections.generated.IntegerToObjectHashMap;
import deltix.util.lang.Util;
import deltix.util.tomcat.ConnectionHandler;
import deltix.util.tomcat.ConnectionHandshakeHandler;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/*
   Handlers mapped by fist byte
 */
public class TConnectionHandler implements ConnectionHandler {

    private final IntegerToObjectHashMap<ConnectionHandshakeHandler> handlers = new IntegerToObjectHashMap<ConnectionHandshakeHandler>();

    @Override
    public int          getMarkLimit () {
        return (1);
    }

    public void         addHandler(byte mark, ConnectionHandshakeHandler handler) {
        if (!handlers.put(mark, handler))
            throw new IllegalStateException("Handler for " + mark + " is already registered");
    }

    public boolean      removeHandler(byte mark) {
        return handlers.remove(mark);
    }

    @Override
    public boolean      handleConnection (
            Socket socket,
            BufferedInputStream bis,
            OutputStream os
    )
            throws IOException
    {
        bis.mark(getMarkLimit());
        int firstByte = bis.read ();  // ConnectionInterceptor marked InputStream to allow reset (-1)
        bis.reset();

        ConnectionHandshakeHandler handler = handlers.get(firstByte, null);

//        switch (firstByte) {
//            case 0:
//                handler = framework;
//                break;
//
//            case 48:    // BER.SEQUENCE
//                handler = snmp;
//                break;
//
//            case 24:
//                handler = rest;
//                break;
//
////            case 'F':    // FIX.4.?
////                handler = fix;
////                break;
//
//            default:
//                return (false);
//        }

        if (handler != null) {
            if (!handler.handleHandshake (socket, bis, os))
                socket.close();

            return (true);
        }

        return (false);
    }

    @Override
    public void             close() {

        for (ConnectionHandshakeHandler handler : handlers) {
            if (handler instanceof Closeable)
                Util.close((Closeable)handler);
        }

        handlers.clear();
    }
}
