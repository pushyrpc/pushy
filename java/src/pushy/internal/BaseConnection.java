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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for Pushy connections, defining generic protocol and message
 * handling procedures.
 */
public class BaseConnection
{
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
    private Object handle(Message message) throws java.io.IOException
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
            Object result = handle(message.getType(), value);
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

    protected Object handle(Message.Type type, Object arg)
    {
        if (type.equals(Message.Type.response))
        {
            return arg;
        }
        else if (type.equals(Message.Type.exception))
        {
            if (arg instanceof RuntimeException)
                throw (RuntimeException)arg;
            else
                throw new RemoteException((PushyObject)arg);
        }
        return null;
    }

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
    sendRequest(Message.Type type, Object arg) throws java.io.IOException
    {
        ResponseHandler handler = new ResponseHandler();

        // If a request is being processed, increase the "waiting" count, so
        // other threads may attempt to receive messages.
        synchronized (processingCondition)
        {
            if (!open)
                throw new RuntimeException("Connection is closed");
            if (getThreadRequestCount() > 0)
            {
                if (processingCount == ++waitingCount)
                    processingCondition.notify();
            }

            // Register the response handler.
            responseHandlers.put(new Long(handler.getThreadId()), handler);
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
            responseHandlers.remove(new Long(handler.getThreadId()));
        }
        return handle(m);
    }

    /**
     * Send a message as a response to a request.
     */
    protected void sendResponse(Object result) throws java.io.IOException
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
    sendMessage(Message.Type type, Object value) throws java.io.IOException
    {
        Message message = new Message(type, marshal(value), getPeerThread());
        synchronized (ostream)
        {
            message.pack(ostream);
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
    private Message getRequest() throws java.io.IOException
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
    getResponse(ResponseHandler handler) throws java.io.IOException
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
    private Message getMessage() throws java.io.IOException
    {
        synchronized (istream)
        {
            return Message.unpack(istream);
        }
    }

    private byte[] marshal(Object value)
    {
        return null; // TODO
    }

    private Object unmarshal(byte[] bytes)
    {
        return null; // TODO
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

