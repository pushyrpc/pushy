# Copyright (c) 2008, 2009 Andrew Wilkins <axwalk@gmail.com>
# 
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following
# conditions:
# 
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
# OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
# WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.

import logging, marshal, os, struct, sys, thread, threading
from pushy.protocol.message import Message, MessageType, message_types
from pushy.protocol.proxy import Proxy, ProxyType, proxy_types
import pushy.util


# This collection should contain only immutable types. Builtin, mutable types
# such as list, set and dict need to be handled specially.
marshallable_types = [
    unicode, slice, frozenset, float, basestring, long, str, int, complex,
    bool, type(None)
]

# The 'buffer' type doesn't exist in Jython.
try:
    marshallable_types.append(buffer)
except NameError: pass


# Message types that may received in response to a request.
response_types = (
    MessageType.response, MessageType.exception
)
marshallable_types = tuple(marshallable_types)


class LoggingFile:
    def __init__(self, stream, log):
        self.stream = stream
        self.log = log
    def close(self):
        self.stream.close()
    def write(self, s):
        self.log.write(s)
        self.stream.write(s)
    def flush(self):
        self.log.flush()
        self.stream.flush()
    def read(self, n):
        data = self.stream.read(n)
        self.log.write(data)
        self.log.flush()
        return data


class ResponseHandler:
    def __init__(self, condition):
        self.condition   = condition
        self.message     = None
        self.thread      = thread.get_ident()


connection_count_lock = threading.Lock()
connection_count = 0
def get_connection_id():
    global connection_count
    connection_count_lock.acquire()
    try:
        connection_id = connection_count
        connection_count += 1
        return connection_id
    finally:
        connection_count_lock.release()


class BaseConnection(object):
    def __init__(self, istream, ostream, initiator=True):
        self.__open           = True
        self.__istream        = istream
        self.__ostream        = ostream
        self.__initiator      = initiator
        self.__istream_lock   = threading.Lock()
        self.__ostream_lock   = threading.Lock()
        self.__unmarshal_lock = threading.Lock()
        self.__connid         = get_connection_id()

        # Define message handlers (MessageType -> method)
        self.message_handlers = {
            MessageType.response:    self.__handle_response,
            MessageType.exception:   self.__handle_exception,
        }

        # Attributes required to track responses.
        self.__thread_local      = threading.local()
        self.__response_handlers = {}

        # Attributes required to track number of threads processing requests.
        # The following has to be true for the message receiving thread to be
        # allowed to attempt to receive a message:
        #     - There are no threads currently processing a request, and
        #       there are no requests pending.
        #  OR
        #     - There are threads currently processing requests, but they
        #       are all waiting on responses.
        self.__receiving  = False # Is someone calling self.__recv?
        self.__processing = 0  # How many requests are being processed.
        self.__waiting    = 0  # How many responses are pending.
        self.__responses  = 0
        self.__requests   = []
        self.__processing_condition = threading.Condition(threading.Lock())

        # Uncomment the following for debugging.
        if False:
            self.__istream = \
                LoggingFile(
                    istream,
                    open("%d-%d.in"% (os.getpid(), self.__connid), "wb"))
            self.__ostream = \
                LoggingFile(
                    ostream,
                    open("%d-%d.out" % (os.getpid(), self.__connid),"wb"))

        # (Client) Contains mapping of id(obj) -> proxy
        self.__proxies = {}
        # (Client) Contains mapping of id(obj) -> threading.Event, which
        # __unmarshal will use to synchronise the order of messages.
        self.__pending_proxies = {}
        # (Client) Contains mapping of id(proxy) -> id(obj)
        self.__proxy_ids = {}
        # (Server) Contains mapping of id(obj) -> obj
        self.__proxied_objects = {}


    def __del__(self):
        if self.__open:
            self.close()


    def __log_state(self):
        state_format = """
Connection State
------------------------
ID:                   %r
Open:                 %r
Receiving:            %r
Processing Count:     %r
Waiting Count:        %r
Response Count:       %r
Request Count:        %r
Thread Request Count: %r
Peer Thread:          %r
        """.strip()
        state_args = (self.__connid, self.__open, self.__receiving,
                      self.__processing, self.__waiting, self.__responses,
                      len(self.__requests), self.__thread_request_count,
                      self.__peer_thread)
        pushy.util.logger.debug("\n"+state_format, *state_args)


    # Property for determining the number of requests the current thread is
    # processing.
    def __get_thread_request_count(self):
        return getattr(self.__thread_local, "request_count", 0)
    def __set_thread_request_count(self, value):
        self.__thread_local.request_count = value
    __thread_request_count = \
        property(__get_thread_request_count, __set_thread_request_count)


    # Property for getting the current thread's peer thread.
    def __get_peer_thread(self):
        return getattr(self.__thread_local, "peer_thread", 0)
    def __set_peer_thread(self, value):
        self.__thread_local.peer_thread = value
    __peer_thread = property(__get_peer_thread, __set_peer_thread)


    def close(self):
        try:
            if not self.__open:
                return

            # Flag the connection as closed, and wake up all request
            # handlers. We'll then wait until there are no more
            # response handlers waiting.
            self.__open = False
            self.__processing_condition.acquire()
            try:
                # Wake up request/response handlers.
                self.__processing_condition.notifyAll()
            finally:
                self.__processing_condition.release()

            self.__ostream_lock.acquire()
            try:
                self.__ostream.close()
                pushy.util.logger.debug("Closed ostream")
            finally:
                self.__ostream_lock.release()
            self.__istream_lock.acquire()
            try:
                self.__istream.close()
                pushy.util.logger.debug("Closed istream")
            finally:
                self.__istream_lock.release()
        except:
            import traceback
            traceback.print_exc()
            pushy.util.logger.debug(traceback.format_exc())


    def serve_forever(self):
        "Serve asynchronous requests from the peer forever."
        try:
            while self.__open:
                try:
                    m = self.__waitForRequest()
                    if m is not None and self.__open:
                        self.__handle(m)
                except IOError:
                    return
        finally:
            pushy.util.logger.debug("Leaving serve_forever")


    def send_request(self, message_type, args):
        "Send a request message and wait for a response."

        # If a request is being processed, then increase the 'waiting' count,
        # so other threads may attempt to receive messages.
        self.__processing_condition.acquire()
        try:
            if not self.__open:
                raise Exception, "Connection is closed"

            handler = self.__response_handlers.get(thread.get_ident(), None)
            if handler is None:
                handler = ResponseHandler(self.__processing_condition)
                self.__response_handlers[handler.thread] = handler

            if self.__thread_request_count > 0:
                self.__waiting += 1
                if self.__processing == self.__waiting:
                    self.__processing_condition.notify()
        finally:
            self.__processing_condition.release()

        # Send the message.
        self.__send_message(message_type, args)

        # Wait for the response handler to be signalled.
        try:
            m = self.__waitForResponse(handler)
            while self.__open and (m is None or m.type not in response_types):
                if m is not None:
                    self.__handle(m)
                m = self.__waitForResponse(handler)
        finally:
            if self.__thread_request_count == 0:
                del self.__response_handlers[handler.thread]
        return self.__handle(m)


    def __send_response(self, result):
        # Allow the message receiving thread to proceed. We must do this
        # *before* sending the message, in case the other side is
        # attempting to send a message at the same time.
        self.__processing_condition.acquire()
        try:
            self.__processing -= 1
            if self.__processing == 0:
                self.__processing_condition.notifyAll()
        finally:
            self.__processing_condition.release()

        # Now send the message.
        self.__send_message(MessageType.response, result)


    def __waitForRequest(self):
        pushy.util.logger.debug("Enter waitForRequest")
        # Wait for a request message. If a response message is received first,
        # then set the relevant response handler and wait until we're allowed
        # to read a message before proceeding.
        self.__processing_condition.acquire()
        try:
            # Wait until we're allowed to read from the input stream, or
            # another thread has enqueued a request for us.
            while (self.__open and (len(self.__requests) == 0)) and \
                   (self.__receiving or \
                    self.__responses > 0 or \
                     (self.__processing > 0 and \
                      (self.__processing > self.__waiting))):
                self.__log_state()
                self.__processing_condition.notify()
                self.__processing_condition.wait()
            self.__log_state()

            # Check if the connection is still open.
            if not self.__open:
                return None

            # Check if another thread received a request message.
            if len(self.__requests) > 0:
                request = self.__requests.pop()
                self.__processing += 1
                self.__processing_condition.notify()
                return request

            # Release the processing condition, and wait for a message.
            self.__receiving = True
            self.__processing_condition.release()
            notifyAll = True
            try:
                m = self.__recv()
                if m.target != 0:
                    self.__responses += 1
                    self.__response_handlers[m.target].message = m
                else:
                    # We got a request, so return it. Wake up one other thread
                    # waiting to receive a message.
                    if self.__open:
                        self.__processing += 1
                    notifyAll = False
                    return m
            finally:
                self.__processing_condition.acquire()
                self.__receiving = False
                if notifyAll:
                    self.__processing_condition.notifyAll()
                else:
                    self.__processing_condition.notify()
        finally:
            self.__processing_condition.release()
            pushy.util.logger.debug("Leave waitForRequest")


    def __waitForResponse(self, handler):
        pushy.util.logger.debug("Enter waitForResponse")
        self.__processing_condition.acquire()
        try:
            # Wait until we're allowed to read from the input stream, or
            # another thread has enqueued a request for us.
            while (self.__open and handler.message is None) and \
                   (self.__receiving or \
                    (self.__processing > 0 and \
                     (self.__processing > self.__waiting))):
                self.__log_state()
                self.__processing_condition.notify()
                self.__processing_condition.wait()
            self.__log_state()

            # Wait until we've got a response message.
            if handler.message is None and self.__open:
                self.__receiving = True
                self.__processing_condition.release()
                try:
                    m = self.__recv()
                    if m.target == 0:
                        self.__requests.insert(0, m)
                    else:
                        self.__response_handlers[m.target].message = m
                        if m.target != handler.thread:
                            self.__responses += 1
                finally:
                    self.__processing_condition.acquire()
                    self.__receiving = False
            elif self.__open:
                self.__responses -= 1

            if handler.message is not None:
                if handler.message.type not in response_types:
                    # Increment 'processing' count.
                    self.__processing += 1
                elif self.__thread_request_count > 0:
                    # If we were waiting on a response, let the request
                    # handler thread know that we're once again processing our
                    # request.
                    self.__waiting -= 1
            elif not self.__open:
                raise Exception, "Connection is closed"

            return handler.message
        finally:
            handler.message = None
            self.__processing_condition.notifyAll()
            self.__processing_condition.release()
            pushy.util.logger.debug("Leave waitForResponse")


    def __marshal(self, obj):
        # XXX perhaps we can check refcount to optimise (if 1, immutable)
        try:
            if type(obj) in marshallable_types:
                return "s" + marshal.dumps(obj, 0)
        except ValueError:
            pass

        # If it's a tuple, try to marshal each item individually.
        if type(obj) is tuple:
            payload = "t"
            try:
                for item in obj:
                    part = self.__marshal(item)
                    payload += struct.pack(">I", len(part))
                    payload += part
                return payload
            except ValueError: pass

        i = id(obj)
        if i in self.__proxied_objects:
            return "p" + self.__marshal(i)
        elif i in self.__proxy_ids:
            # Object originates at the peer.
            return "o" + marshal.dumps(self.__proxy_ids[i])
        else:
            # Create new entry in proxy objects map:
            #    id -> (obj, refcount, opmask, args)
            #
            # opmask is a bitmask defining whether or not the object
            # defines various methods (__add__, __iter__, etc.)
            opmask = ProxyType.getoperators(obj)
            proxy_type = ProxyType.get(obj)
            args = ProxyType.getargs(proxy_type, obj)

            pushy.util.logger.debug(
                "Marshalling object: %r, %r, %r",
                i, opmask, proxy_type)

            self.__proxied_objects[i] = obj
            return "p" + self.__marshal((i, opmask, int(proxy_type), args))


    def __unmarshal(self, payload):
        if payload[0] == "s":
            # Simple type
            return marshal.loads(buffer(payload, 1))
        elif payload[0] == "t":
            size_size = struct.calcsize(">I")
            payload = buffer(payload, 1)
            parts = []
            while len(payload) > 0:
                size = struct.unpack(">I", buffer(payload, 0, size_size))[0]
                payload = buffer(payload, size_size)
                parts.append(self.__unmarshal(buffer(payload, 0, size)))
                payload = buffer(payload, size)
            return tuple(parts)
        elif payload[0] == "p":
            # Proxy object
            id_ = self.__unmarshal(buffer(payload, 1))
            if type(id_) is tuple:
                pushy.util.logger.debug(
                    "Unmarshalling object: %r, %r, %r",
                    id_[0], id_[1], proxy_types[id_[2]])

                # New object: (id, opmask, object_type, args)
                p = Proxy(id_[0], id_[1], id_[2], id_[3], self,
                          self.__register_proxy)

                # Wake anyone waiting on this ID to be unmarshalled.
                self.__unmarshal_lock.acquire()
                try:
                    if id_[0] in self.__pending_proxies:
                        event = self.__pending_proxies[id_[0]]
                        del self.__pending_proxies[id_[0]]
                        event.set()
                finally:
                    self.__unmarshal_lock.release()

                return p
            else:
                # Known object: id
                if id_ not in self.__proxies:
                    self.__unmarshal_lock.acquire()
                    try:
                        if id_ not in self.__proxies:
                            event = self.__pending_proxies.get(id_, None)
                            if event is None:
                                event = threading.Event()
                                self.__pending_proxies[id_] = event
                    finally:
                        self.__unmarshal_lock.release()

                    # Wait for the event to be set.
                    if id_ not in self.__proxies:
                        event.wait()

                return self.__proxies[id_]
        elif payload[0] == "o":
            # The object originated here.
            id_ = marshal.loads(buffer(payload, 1))
            object = self.__proxied_objects[id_]
            return object
        else:
            raise ValueError, "Invalid payload prefix: %s", payload[0]


    def __register_proxy(self, proxy, remote_id):
        pushy.util.logger.debug(
            "Registering a proxy: %r -> %r", id(proxy), remote_id)
        self.__proxies[remote_id] = proxy
        self.__proxy_ids[id(proxy)] = remote_id


    def __send_message(self, message_type, args):
        thread_id = self.__peer_thread
        m = Message(message_type, self.__marshal(args), thread_id)
        bytes = m.pack()
        pushy.util.logger.debug("[%r] Sending %r (%r): %r",
                                self.__connid, m, thread_id, bytes)
        self.__ostream_lock.acquire()
        try:
            self.__ostream.write(bytes)
            self.__ostream.flush()
        finally:
            self.__ostream_lock.release()
        pushy.util.logger.debug("Sent %r [%d bytes]", message_type, len(bytes))


    def __recv(self):
        pushy.util.logger.debug("Waiting for message")
        self.__istream_lock.acquire()
        try:
            m = Message.unpack(self.__istream)
            pushy.util.logger.debug("Received %r", m)
            return m
        finally:
            pushy.util.logger.debug("Receive ended")
            self.__istream_lock.release()


    def __handle(self, m):
        pushy.util.logger.debug("[%r] Handling message: %r", self.__connid, m)

        # Track the number of requests being processed in this thread. May be
        # greater than one, if there is to-and-fro. We need to track this so
        # we know when to set the 'peer_thread'.
        is_request = m.type not in response_types
        if is_request:
            self.__thread_request_count += 1
            if self.__thread_request_count == 1:
                self.__peer_thread = m.source

        try:
            try:
                args = self.__unmarshal(m.payload)
                result = self.message_handlers[m.type](m.type, args)
                if m.type not in response_types:
                    self.__send_response(result)
                return result
            except SystemExit, e:
                self.__send_response(e.code)
                raise e
            except:
                e = sys.exc_info()[1]

                # An exception raised while handling an exception message
                # should be sent up to the caller.
                if m.type is MessageType.exception:
                    raise e

                # Allow the message receiving thread to proceed.
                self.__processing_condition.acquire()
                try:
                    self.__processing -= 1
                    if self.__processing == 0:
                        self.__processing_condition.notifyAll()
                finally:
                    self.__processing_condition.release()

                # Send the above three objects to the caller
                import traceback
                pushy.util.logger.debug(traceback.format_exc())
                self.__send_message(MessageType.exception, e)
        finally:
            if is_request:
                self.__thread_request_count -= 1
                if self.__thread_request_count == 0:
                    self.__peer_thread = 0


    def __handle_response(self, message_type, result):
        return result


    def __handle_exception(self, message_type, e):
        raise e

