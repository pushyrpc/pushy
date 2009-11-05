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

    protected BaseConnection(java.io.InputStream istream,
                             java.io.OutputStream ostream)
    {
        this.istream = istream;
        this.ostream = ostream;
    }

    /**
     * Handle a request or response message.
     */
    protected Object handle(Message message)
    {
        return null; // TODO
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
    sendRequest(Message.Type type, Object[] args) throws java.io.IOException
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
        sendMessage(type, args);

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
        return -1; // TODO
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
    private Message getResponse(ResponseHandler handler)
    {
        synchronized (processingCondition)
        {
            return null; // TODO
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

    private int getThreadRequestCount()
    {
        return -1; // TODO
    }

    private byte[] marshal(Object value)
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

