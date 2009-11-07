/*
 * Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package pushy.internal;

import pushy.PushyObject;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for Pushy connections, defining generic protocol and message
 * handling procedures.
 */
public abstract class BaseConnection
{
    private static final Logger logger =
        Logger.getLogger(BaseConnection.class.getName());

    private java.io.InputStream istream;
    private java.io.OutputStream ostream;
    private Object processingCondition = new Object();
    private boolean receiving = false;
    private boolean open = true;
    private int waitingCount = 0;
    private int processingCount = 0;
    private int responseCount = 0;
    private Map responseHandlers = new HashMap();
    private List requests = new ArrayList();
    private Map proxiedObjects = new IdentityHashMap();
    private Map proxiedObjectIds = new HashMap();
    private Map proxies = new HashMap();
    private Map proxyIds = new IdentityHashMap();
    private ThreadLocal threadRequestCount = new ThreadLocal();
    private ThreadLocal peerThread = new ThreadLocal();

    protected BaseConnection(java.io.InputStream istream,
                             java.io.OutputStream ostream)
    {
        this.istream = istream;
        this.ostream = ostream;
    }

    /**
     * Handle a request or response message.
     */
    private Object handle(Message message) throws IOException
    {
        // Track the number of requests being processed in this thread. May be
        // greater than one, if there is to-and-fro. We need to track this so
        // we know when to set the 'peerThread'.
        boolean isRequest = !message.getType().isResponse();
        if (isRequest)
        {
            int threadRequestCount = getThreadRequestCount();
            setThreadRequestCount(threadRequestCount + 1);
            if (threadRequestCount == 0)
                setPeerThread(message.getSource());
        }

        try
        {
            Object value = unmarshal(message.getPayload());
            Object result = handleInternal(message.getType(), value);

            // Before returning, return the "real" object for an exported
            // object. Until now, the wrapper object was being passed around.
            if (result instanceof ExportedObject)
            {
                ExportedObject eo = (ExportedObject)result;
                if (eo.getConnection() == this)
                    result = eo.getObject();
            }

            if (isRequest)
                sendResponse(result);
            return result;
        }
        catch (RuntimeException e)
        {
            // An exception raised while handling an exception message
            // should be sent up to the caller.
            if (message.getType().equals(Message.Type.exception))
                throw e;

            // Allow the message receiving thread to proceed.
            synchronized (processingCondition)
            {
                if (--processingCount == 0)
                    processingCondition.notifyAll();
            }

            sendMessage(Message.Type.exception, e);
            return null;
        }
        finally
        {
            if (isRequest)
            {
                int threadRequestCount = getThreadRequestCount();
                setThreadRequestCount(threadRequestCount - 1);
                if (threadRequestCount == 1)
                    setPeerThread(0);
            }
        }
    }

    protected Object handleInternal(Message.Type type, Object arg)
    {
        if (type.equals(Message.Type.response))
        {
            return arg;
        }
        else if (type.equals(Message.Type.exception))
        {
            if (arg instanceof ExportedObject)
                arg = ((ExportedObject)arg).getObject();

            if (arg instanceof RuntimeException)
                throw (RuntimeException)arg;
            else
                throw new RemoteException((PushyObject)arg);
        }
        else
        {
            return handle(type, arg);
        }
    }

    /**
     * Handle a message.
     */
    protected abstract Object handle(Message.Type type, Object arg);

    /**
     * Serve asynchronous requests from the peer forever.
     */
    public void serve()
    {
        while (open)
        {
            try
            {
                Message m = getRequest();
                if (m != null && open)
                    handle(m);
            }
            catch (java.io.IOException e)
            {
                return;
            }
        }
    }

    /**
     * Send a request to the peer, and wait for and return the result.
     */
    protected Object
    sendRequest(Message.Type type, Object arg) throws IOException
    {
        ResponseHandler handler = null;

        // If a request is being processed, increase the "waiting" count, so
        // other threads may attempt to receive messages.
        synchronized (processingCondition)
        {
            if (!open)
                throw new RuntimeException("Connection is closed");

            if (getThreadRequestCount() > 0)
            {
                handler = (ResponseHandler)responseHandlers.get(
                              new Long(ThreadId.getThreadId()));
                if (processingCount == ++waitingCount)
                    processingCondition.notify();
            }
            else
            {
                handler = new ResponseHandler();
                assert !responseHandlers.containsKey(
                           new Long(handler.getThreadId()));
                responseHandlers.put(new Long(handler.getThreadId()), handler);
            }
        }

        // Send the message.
        sendMessage(type, arg);

        // Wait for the response handler to be signalled.
        Message m = null;
        try
        {
            m = getResponse(handler);
            while (open && (m == null || !m.getType().isResponse()))
            {
                if (m != null)
                    handle(m);
                m = getResponse(handler);
            }
        }
        finally
        {
            if (getThreadRequestCount() == 0)
                responseHandlers.remove(new Long(handler.getThreadId()));
        }
        return handle(m);
    }

    /**
     * Send a message as a response to a request.
     */
    protected void sendResponse(Object result) throws IOException
    {
        synchronized (processingCondition)
        {
            if (--processingCount == 0)
                processingCondition.notifyAll();
        }
        sendMessage(Message.Type.response, result);
    }

    /**
     * Send a message.
     */
    private void
    sendMessage(Message.Type type, Object value) throws IOException
    {
        Message msg = new Message(type, marshal(value), getPeerThread());
        synchronized (ostream)
        {
            logger.log(
                Level.FINEST, "Sending message: {0}", new Object[]{msg});
            msg.pack(ostream);
            ostream.flush();
        }
    }

    /**
     * Get the current thread's peer thread.
     */
    private long getPeerThread()
    {
        Long peer = (Long)peerThread.get();
        if (peer == null)
            return 0;
        return peer.longValue();
    }

    /**
     * Set the current thread's peer thread.
     */
    private void setPeerThread(long peer)
    {
        peerThread.set(new Long(peer));
    }

    /**
     * Get the current thread's request count.
     */
    private int getThreadRequestCount()
    {
        Integer count = (Integer)threadRequestCount.get();
        if (count == null)
            return 0;
        return count.intValue();
    }

    /**
     * Set the current thread's request count.
     */
    private void setThreadRequestCount(int count)
    {
        threadRequestCount.set(new Integer(count));
    }

    /**
     * Wait for a request message.
     */
    private Message getRequest() throws IOException
    {
        synchronized (processingCondition)
        {
            // Wait until we're allowed to read from the input stream, or
            // another thread has enqueued a request for us.
            while ((open && requests.isEmpty()) &&
                   (receiving ||
                    responseCount > 0 ||
                    (processingCount > 0 &&
                     (processingCount > waitingCount))))
            {
                processingCondition.notify();
                try
                {
                    processingCondition.wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }

            // Check if the connection is still open.
            if (!open)
                return null;

            // Check if another thread received a request message.
            if (!requests.isEmpty())
            {
                Message request = (Message)requests.remove(0);
                ++processingCount;
                processingCondition.notify();
                return request;
            }

            // Release the processing condition and wait for a message.
            receiving = true;
        }

        // Wait for a message.
        boolean notifyAll = true;
        try
        {
            Message message = getMessage();
            if (message.getTarget() != 0)
            {
                ResponseHandler handler =
                    (ResponseHandler)responseHandlers.get(
                        new Long(message.getTarget()));
                handler.setMessage(message);
                ++responseCount;
                return null;
            }
            else
            {
                // We got a request, so return it. Wake up one other thread
                // waiting to receive a message.
                if (open)
                    ++processingCount;
                notifyAll = false;
                return message;
            }
        }
        finally
        {
            synchronized (processingCondition)
            {
                if (notifyAll)
                    processingCondition.notifyAll();
                else
                    processingCondition.notify();
                receiving = false;
            }
        }
    }

    /**
     * Wait for a response message, or a request message in response to the
     * initial request.
     */
    private Message
    getResponse(ResponseHandler handler) throws IOException
    {
        synchronized (processingCondition)
        {
            // Wait until we're allowed to read from the input stream, or
            // another thread has enqueued a request for us.
            while ((open && handler.getMessage() == null) &&
                   (receiving ||
                    (processingCount > 0 &&
                     (processingCount > waitingCount))))
            {
                processingCondition.notify();
                try
                {
                    processingCondition.wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }

            // If we don't have a message yet, then we'll need to release the
            // lock and then receive a message.
            if (open)
            {
                if (handler.getMessage() == null)
                {
                    receiving = true;
                }
                else
                {
                    --responseCount;
                }
            }
        }

        // Receive a message.
        if (open && handler.getMessage() == null)
        {
            Message message = getMessage();
            if (message.getTarget() == 0)
            {
                requests.add(message);
            }
            else
            {
                ResponseHandler targetResponseHandler =
                    (ResponseHandler)responseHandlers.get(
                        new Long(message.getTarget()));
                targetResponseHandler.setMessage(message);
                if (message.getTarget() != handler.getThreadId())
                    ++responseCount;
            }
        }

        // Stopped receiving: acquire the lock again, and update the state.
        synchronized (processingCondition)
        {
            receiving = false;
            try
            {
                Message message = handler.getMessage();
                if (message != null)
                {
                    if (!message.getType().isResponse())
                        ++processingCount;
                    else if (getThreadRequestCount() > 0)
                        --waitingCount;
                }
                else if (!open)
                {
                    throw new RuntimeException("Connection is closed");
                }
                return message;
            }
            finally
            {
                handler.setMessage(null);
                processingCondition.notifyAll();
            }
        }
    }

    /**
     * Receive a message from the input stream.
     */
    private Message getMessage() throws IOException
    {
        synchronized (istream)
        {
            Message message = Message.unpack(istream);
            logger.log(
                Level.FINEST, "Received message: {0}", new Object[]{message});
            return message;
        }
    }

    private byte[] marshal(Object value) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        marshal(value, stream);
        return stream.toByteArray();
    }

    private void marshal(Object value, OutputStream stream) throws IOException
    {
        // Simple type?
        if (Marshal.isMarshallable(value))
        {
            stream.write('s');
            Marshal.dump(value, stream);
            return;
        }

        // Marshal array in the same way as Python tuples.
        if (value.getClass().isArray())
        {
            int length = Array.getLength(value);
            stream.write('t');
            if (length > 0)
            {
                ByteArrayOutputStream partStream =
                    new ByteArrayOutputStream();
                for (int i = 0; i < length; ++i, partStream.reset())
                {
                    marshal(Array.get(value, i), partStream);
                    putInt32(partStream.size(), stream);
                    partStream.writeTo(stream);
                }
            }
            return;
        }

        // If it's a proxy object and it belongs to this connection, then
        // shortcut the proxy ID lookup.
        if (value instanceof PushyObjectImpl)
        {
            PushyObjectImpl proxy = (PushyObjectImpl)value;
            if (proxy.getConnection() == this)
            {
                stream.write('o');
                Marshal.dump(proxy.getId(), stream);
                return;
            }
        }
        // If the object exists in the proxies map, then return the
        // identifier to the originator of the object.
        synchronized (proxies)
        {
            Number id = (Number)proxyIds.get(value);
            if (id != null)
            {
                stream.write('o');
                Marshal.dump(id, stream);
                return;
            }
        }

        // If it's a previously proxied object and belongs to this connection,
        // then shortcut the export step.
        if (value instanceof ExportedObject)
        {
            ExportedObject eo = (ExportedObject)value;
            if (eo.getConnection() == this)
            {
                stream.write('p');
                marshal(((ExportedObject)value).getId(), stream);
                return;
            }
        }
        // If the object exists in the proxiedObjects map, then send the
        // previously generated identifier to the peer.
        synchronized (proxiedObjects)
        {
            ExportedObject eo = (ExportedObject)proxiedObjects.get(value);
            if (eo != null && eo.getConnection() == this)
            {
                stream.write('p');
                marshal(eo.getId(), stream);
                return;
            }
            else
            {
                // XXX this only works at the moment because we don't
                //     discard objects from the proxied objects map.
                Number id = new Integer(proxiedObjects.size());
                Proxy.Type type = Proxy.getType(value);
                Number operators = Proxy.getOperators(value);
                Object proxyArg = Proxy.getArgument(value, type);
                Integer typeCode = new Integer(type.getCode());

                eo = createExportObject(id, type, value);
                proxiedObjects.put(value, eo);
                proxiedObjectIds.put(id, eo);

                Object[] args;
                if (proxyArg == null)
                    args = new Object[]{id, operators, typeCode};
                else
                    args = new Object[]{id, operators, typeCode, proxyArg};

                stream.write('p');
                marshal(args, stream);
                return;
            }
        }
    }

    private Object unmarshal(byte[] bytes) throws IOException
    {
        InputStream stream = new ByteArrayInputStream(bytes);
        return unmarshal(stream);
    }

    /**
     * Unmarshal an object from its network representation.
     */
    private Object unmarshal(InputStream stream) throws IOException
    {
        int type = stream.read();
        switch (type)
        {
            case 's': // Simple marshallable type
            {
                return Marshal.load(stream);
            }
            case 't': // Tuple
            {
                List items = new ArrayList();
                while (stream.available() > 0)
                {
                    int size = getInt32(stream);
                    byte[] bytes = readBytes(stream, size);
                    items.add(unmarshal(bytes));
                }
                return Marshal.createArray(items);
            }
            case 'o': // Proxy object that originated here.
            {
                Integer id = (Integer)Marshal.load(stream);
                synchronized (proxiedObjects)
                {
                    ExportedObject eo =
                        (ExportedObject)proxiedObjectIds.get(id);
                    return eo;
                }
            }
            case 'p': // Remote proxy object.
            {
                Object value = unmarshal(stream);
                if (value instanceof Object[])
                {
                    Object[] idParts = (Object[])value;

                    // Split apart the id.
                    Object arg = null;
                    Number id = (Number)idParts[0];
                    Number opmask = (Number)idParts[1];
                    Integer objectType = (Integer)idParts[2];
                    if (idParts.length > 3)
                        arg = idParts[3];

                    // Create the proxy object.
                    Object proxy = createProxy(id, opmask, objectType, arg);

                    // Add the proxy and wake up anyone waiting for it to be
                    // registered.
                    synchronized (proxies)
                    {
                        proxies.put(id, proxy);
                        proxyIds.put(proxy, id);
                        proxies.notifyAll();
                    }
                    return proxy;
                }
                else
                {
                    Number id = (Number)value;
                    synchronized (proxies)
                    {
                        Object proxy = proxies.get(id);
                        while (proxy == null)
                        {
                            try {
                                wait();
                            } catch (InterruptedException e) {}
                            proxy = proxies.get(id);
                        }
                        return proxy;
                    }
                }
            }
            default:
                logger.severe("Unhandled type: " + (char)type);
        }
        return null;
    }

    /**
     * Create a proxy object, with the given ID, type, a bitmask describing the
     * operators the remote object contains, and optionally some arguments for
     * the object's constructor.
     */
    protected abstract Object
    createProxy(Number id, Number opmask, Integer type, Object args);

    /**
     * Create an export object, with the given ID, type and wrapped object.
     */
    protected abstract ExportedObject
    createExportObject(Number id, Proxy.Type type, Object object);

    // Write a big-endian 32-bit integer to the stream.
    private void putInt32(int value, OutputStream stream) throws IOException
    {
        stream.write((byte)(value >> 24));
        stream.write((byte)(value >> 16));
        stream.write((byte)(value >> 8));
        stream.write((byte)value);
    }

    // Read a big-endian 32-bit integer from the stream.
    private int getInt32(InputStream stream) throws IOException
    {
        return ((stream.read() << 24) | (stream.read() << 16) |
                (stream.read() << 8)  | (stream.read()));
    }

    private byte[] readBytes(InputStream stream, int size) throws IOException
    {
        byte[] bytes = new byte[size];
        readBytes(stream, bytes);
        return bytes;
    }

    private void readBytes(InputStream stream, byte[] buf) throws IOException
    {
        int nread = 0;
        do
        {
            int partial = stream.read(buf, nread, buf.length-nread);
            if (partial == -1)
                throw new java.io.EOFException();
            nread += partial;
        } while (nread < buf.length);
    }

    // A class that holds the result of a request.
    private static class ResponseHandler
    {
        private Message message;
        private long threadId;

        private ResponseHandler()
        {
            threadId = ThreadId.getThreadId();
        }

        public long getThreadId()
        {
            return threadId;
        }

        public Message getMessage()
        {
            return message;
        }

        public void setMessage(Message message)
        {
            this.message = message;
        }
    }
}

