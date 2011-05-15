/*
 * Copyright (c) 2009, 2011 Andrew Wilkins <axwalk@gmail.com>
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
import java.lang.ref.WeakReference;
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

    private static final Integer MARSHAL_TUPLE = new Integer(0);
    private static final Integer MARSHAL_ORIGIN = new Integer(1);
    private static final Integer MARSHAL_PROXY = new Integer(2);

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
    private Map proxiedObjects = new HashMap();
    private Map proxies = new HashMap();
    private Map pendingDeletes = new HashMap();
    private ThreadLocal threadRequestCount = new ThreadLocal();
    private ThreadLocal peerThread = new ThreadLocal();
    private boolean gcEnabled = true;
    private int gcIntervalMillis = 5000; // 5 seconds
    private long gcLastDeleteMillis = System.currentTimeMillis();

    protected BaseConnection(java.io.InputStream istream,
                             java.io.OutputStream ostream)
    {
        this.istream = istream;
        this.ostream = ostream;
    }

    /**
     * Determine whether or not garbage collection of proxies is enabled.
     *
     * If garbage collection is enabled, weak references will be created for
     * proxies. This is the default.
     */
    public boolean isGCEnabled()
    {
        return gcEnabled;
    }

    /**
     * Enable or disable garbage collection of proxies.
     */
    public void setGCEnabled(boolean enabled)
    {
        gcEnabled = enabled;
    }

    /**
     * Get the garbage collection interval, in milliseconds.
     *
     * This is the amount of time in between sending of "delete" messages.
     */
    public int getGCIntervalMillis()
    {
        return gcIntervalMillis;
    }

    /**
     * Set the garbage collection interval, in milliseconds.
     */
    public void setGCIntervalMillis(int millis)
    {
        gcIntervalMillis = millis;
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
        catch (Throwable e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
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

    /**
     * Handles a delete message.
     */
    private void handleDelete(Object[] ids)
    {
        synchronized (proxiedObjects)
        {
            for (int i = 0; i < ids.length; ++i)
            {
                int[] id_and_version = (int[])ids[i];
                Integer id = new Integer(id_and_version[0]);
                ExportedObject eo = (ExportedObject)proxiedObjects.get(id);
                if (eo.getVersion() == id_and_version[1])
                    proxiedObjects.remove(id);
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

            Long threadId = new Long(ThreadId.getThreadId());
            handler = (ResponseHandler)responseHandlers.get(threadId);
            if (handler == null)
            {
                handler = new ResponseHandler();
                responseHandlers.put(new Long(handler.getThreadId()), handler);
            }

            if (getThreadRequestCount() > 0)
            {
                if (processingCount == ++waitingCount)
                    processingCondition.notify();
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
            // See if there are any proxy objects that have been garbage
            // collected. If there are, send a delete message first.
            sendPendingDeletes();

            // Send the original message.
            logger.log(
                Level.FINEST, "Sending message: {0}", new Object[]{msg});
            msg.pack(ostream);
            ostream.flush();
        }
    }

    /**
     * Sends a message to the peer to release exported objects that are no
     * longer referenced by this JVM.
     */
    private void sendPendingDeletes() throws IOException
    {
        synchronized (pendingDeletes)
        {
            if (pendingDeletes.isEmpty())
                return;

            // Check if the garbage collection timer has expired.
            long timeNowMillis = System.currentTimeMillis();
            if ((timeNowMillis - gcLastDeleteMillis) < gcIntervalMillis)
                return;
            gcLastDeleteMillis = timeNowMillis;

            try
            {
                // Convert the map into an array of pairs.
                Object[] pendingItems = new Object[pendingDeletes.size()];
                Iterator iter = pendingDeletes.entrySet().iterator();
                for (int i = 0; iter.hasNext(); ++i)
                {
                    Map.Entry entry = (Map.Entry)iter.next();
                    pendingItems[i] =
                        new Object[]{entry.getKey(), entry.getValue()};
                }

                // Send a "delete" message.
                logger.log(Level.FINEST, "Sending deleting message");
                byte[] payload = Marshal.dump(pendingItems);
                Message msg = new Message(Message.Type.delete_, payload, 0, 0);
                msg.pack(ostream);
                ostream.flush();
            }
            finally
            {
                pendingDeletes.clear();
            }
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
        Message message;
        synchronized (istream)
        {
            message = Message.unpack(istream);
            while (message.getType() == Message.Type.delete_)
            {
                logger.log(Level.FINEST, "Received message: {0}",
                    new Object[]{message});
                handleDelete((Object[])Marshal.load(message.getPayload()));
                message = Message.unpack(istream);
            }
        }
        return message;
    }

    private byte[] marshal(Object value) throws IOException
    {
        return Marshal.dump(makeMarshallable(value));
    }

    private Object makeMarshallable(Object value) throws IOException
    {
        boolean isArray = value==null ? false : value.getClass().isArray();

        // Simple type?
        if (!isArray && Marshal.isMarshallable(value))
            return value;

        if (!(value instanceof PushyObject))
            logger.log(Level.FINEST, "Marshalling object: {0}", value);

        // Marshal array in the same way as Python tuples.
        if (isArray)
        {
            int length = Array.getLength(value);
            Object[] elements = new Object[length];
            for (int i = 0; i < length; ++i)
                elements[i] = makeMarshallable(Array.get(value, i));
            return new Object[]{MARSHAL_TUPLE, elements};
        }

        // If it's a proxy object and it belongs to this connection, then
        // shortcut the proxy ID lookup.
        if (value instanceof ProxyObject)
        {
            ProxyObject proxy = (ProxyObject)value;
            if (proxy.getConnection() == this)
                return new Object[]{MARSHAL_ORIGIN, proxy.getId()};
        }

        // If it's a previously proxied object and belongs to this connection,
        // then shortcut the export step.
        if (value instanceof ExportedObject)
        {
            ExportedObject eo = (ExportedObject)value;
            if (eo.getConnection() == this)
            {
                // Increment the version, and remarshal.
                return new Object[]{MARSHAL_PROXY,
                                    eo.getMarshallableRepresentation(),
                                    new Integer(eo.incrementVersion())};
            }
        }

        synchronized (proxiedObjects)
        {
            // XXX this only works at the moment because we don't
            //     discard objects from the proxied objects map.
            Number id = new Integer(proxiedObjects.size());
            Proxy.Type type = Proxy.getType(value);
            Number operators = Proxy.getOperators(value);
            Object proxyArg = Proxy.getArgument(value, type);
            Integer typeCode = new Integer(type.getCode());

            // Create the "exported object"
            ExportedObject eo = createExportObject(id, type, value);
            proxiedObjects.put(id, eo);

            // Create the marshallable result, and record it on the object.
            Object[] marshallable;
            if (proxyArg == null)
                marshallable = new Object[]{id, operators, typeCode};
            else
                marshallable = new Object[]{id, operators, typeCode, proxyArg};
            eo.setMarshallableRepresentation(marshallable);
            return new Object[]{MARSHAL_PROXY, marshallable, new Integer(0)};
        }
    }

    private Object unmarshal(byte[] bytes) throws IOException
    {
        return reconstruct(Marshal.load(bytes));
    }

    /**
     * Reconstruct an unmarshalled object that was created by makeMarshallable.
     */
    private Object reconstruct(Object marshallable) throws IOException
    {
        if (marshallable == null || !marshallable.getClass().isArray())
            return marshallable;

        Number type = (Number)Array.get(marshallable, 0);
        if (type.equals(MARSHAL_TUPLE))
        {
            Object values = Array.get(marshallable, 1);
            int length = Array.getLength(values);
            List items = new ArrayList(length);
            for (int i = 0; i < length; ++i)
                items.add(reconstruct(Array.get(values, i)));
            return Marshal.createArray(items);
        }
        else if (type.equals(MARSHAL_ORIGIN))
        {
            Object id = Array.get(marshallable, 1);
            synchronized (proxiedObjects)
            {
                return (ExportedObject)proxiedObjects.get(id);
            }
        }
        else if (type.equals(MARSHAL_PROXY))
        {
            Object description = Array.get(marshallable, 1);
            Number version = (Number)Array.get(marshallable, 2);

            // Get the object ID and check if we have received it before.
            Number id = (Number)Array.get(description, 0);
            synchronized (proxies)
            {
                Object proxy_ = proxies.get(id);
                if (proxy_ != null)
                {
                    if (proxy_ instanceof WeakReference)
                        proxy_ = (ProxyObject)((WeakReference)proxy_).get();
                    ProxyObject proxy = (ProxyObject)proxy_;
                    if (proxy != null)
                    {
                        proxy.setVersion(version.intValue());
                        return proxy;
                    }
                }
            }

            // Split apart the rest of the description.
            Object arg = null;
            Number opmask = (Number)Array.get(description, 1);
            Integer objectType = (Integer)Array.get(description, 2);
            if (Array.getLength(description) > 3)
                arg = Array.get(description, 3);

            // Create the proxy object.
            ProxyObject proxy = createProxy(id, opmask, objectType, arg);
            proxy.setVersion(version.intValue());

            // Add the proxy.
            synchronized (proxies)
            {
                if (isGCEnabled())
                    proxies.put(id, new WeakReference(proxy));
                else
                    proxies.put(id, proxy);
            }
            return proxy;
        }
        else
        {
            logger.severe("Unhandled type: " + type);
            return null;
        }
    }

    /**
     * This method should be called on proxy objects when they are garbage
     * collected.
     */
    void deleted(ProxyObject proxy)
    {
        synchronized (proxies)
        {
            synchronized (pendingDeletes)
            {
                proxies.remove(proxy.getId());
                pendingDeletes.put(
                    proxy.getId(), new Integer(proxy.getVersion()));
            }
        }
    }

    /**
     * Create a proxy object, with the given ID, type, a bitmask describing the
     * operators the remote object contains, and optionally some arguments for
     * the object's constructor.
     */
    protected abstract ProxyObject
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

