# Copyright (c) 2008, 2011 Andrew Wilkins <axwalk@gmail.com>
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

import logging
import marshal
import os
import struct
import sys
import thread
import threading
import time
import weakref

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


# Marshalling constants.
MARSHAL_TUPLE  = 0
MARSHAL_ORIGIN = 1
MARSHAL_PROXY  = 2


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


class MessageStream:
    def __init__(self, file_):
        self.__lock = threading.Lock()
        self.__file = file_
    def close(self):
        self.__lock.acquire()
        try:
            self.__file.close()
        finally:
            self.__lock.release()
    def send_message(self, m):
        bytes_ = m.pack()
        self.__lock.acquire()
        try:
            self.__file.write(bytes_)
            self.__file.flush()
        finally:
            self.__lock.release()
    def receive_message(self):
        self.__lock.acquire()
        try:
            return Message.unpack(self.__file)
        finally:
            self.__lock.release()


class ResponseHandler:
    def __init__(self, condition):
        self.condition = condition
        self.message   = None
        self.thread    = thread.get_ident()


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
        self.__open = True
        self.__istream = MessageStream(istream)
        self.__ostream = MessageStream(ostream)
        self.__initiator = initiator
        self.__marshal_lock = threading.Lock()
        self.__delete_lock = threading.RLock()
        self.__connid = get_connection_id()
        self.__last_delete = time.time()
        self.gc_enabled = True
        self.gc_interval = 5.0 # Every 5 seconds

        # Define message handlers (MessageType -> method)
        self.message_handlers = {
            MessageType.response: self.__handle_response,
            MessageType.exception: self.__handle_exception
        }

        # Attributes required to track responses.
        self.__thread_local = threading.local()
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
        self.__waiting = 0  # How many responses are pending.
        self.__responses = 0
        self.__requests = []
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
        # (Client) Contains mapping of id(proxy) -> (id(obj), version)
        self.__proxy_ids = {}
        # (Client) Contains a mapping of id(obj) -> version
        self.__pending_deletes = {}
        # (Server) Contains mapping of id(obj) -> (obj, proxy-info)
        self.__proxied_objects = {}


    def __del__(self):
        if self.__open:
            self.close()

    STATE_FORMAT = """
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
Proxy Count:          %r
Proxied Object Count: %r
""".strip()

    def __log_state(self):
        state_args = (self.__connid, self.__open, self.__receiving,
                      self.__processing, self.__waiting, self.__responses,
                      len(self.__requests), self.__thread_request_count,
                      self.__peer_thread, len(self.__proxies),
                      len(self.__proxied_objects))
        pushy.util.logger.debug("\n"+self.STATE_FORMAT, *state_args)


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

            self.__ostream.close()
            pushy.util.logger.debug("Closed ostream")
            self.__istream.close()
            pushy.util.logger.debug("Closed istream")
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

        # Convert the message type to a "as_tuple", if the user called
        # self.as_tuple.
        try:
            if self.__thread_local.as_tuple_count:
                if self.__thread_local.as_tuple_count == 1:
                    del self.__thread_local.as_tuple_count
                else:
                    self.__thread_local.as_tuple_count -= 1
                args = (int(message_type), args)
                message_type = MessageType.as_tuple
        except AttributeError:
            pass

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
        if type(obj) in marshallable_types:
            return obj

        # If it's a tuple, try to marshal each item individually.
        if type(obj) is tuple:
            return (MARSHAL_TUPLE, tuple(map(self.__marshal, obj)))

        i = id(obj)
        if i in self.__proxied_objects:
            # The object has previously been proxied.
            self.__marshal_lock.acquire()
            try:
                if i in self.__proxied_objects:
                    obj, result, version = self.__proxied_objects[i]
                    self.__proxied_objects[i] = (obj, result, version+1)
                    return (MARSHAL_PROXY, result, version+1)
            finally:
                self.__marshal_lock.release()

        if i in self.__proxy_ids:
            # Object originates at the peer.
            return (MARSHAL_ORIGIN, self.__proxy_ids[i][0])
        else:
            # Create new entry in proxy objects map:
            #    id -> (obj, opmask, proxy_type[, args])
            #
            # opmask is a bitmask defining whether or not the object
            # defines various methods (__add__, __iter__, etc.)
            opmask = ProxyType.getoperators(obj)
            proxy_type = ProxyType.get(obj)
            args = ProxyType.getargs(proxy_type, obj)
            pushy.util.logger.debug(
                "Marshalling object: %r, %r", i, proxy_type)

            version = 0
            if args is not None:
                marshalled_args = self.__marshal(args)
                result = (i, opmask, int(proxy_type), marshalled_args)
            else:
                result = (i, opmask, int(proxy_type))
            self.__proxied_objects[i] = (obj, result, version)
            return (MARSHAL_PROXY, result, version)


    def __unmarshal(self, obj):
        if type(obj) is tuple:
            if obj[0] is MARSHAL_TUPLE:
                return tuple(map(self.__unmarshal, obj[1]))
            elif obj[0] is MARSHAL_ORIGIN:
                return self.__proxied_objects[obj[1]][0]
            elif obj[0] is MARSHAL_PROXY:
                description, version = obj[1], obj[2]
                oid = description[0]
                ref = self.__proxies.get(oid, None)
                if ref is not None:
                    obj = ref()
                    if obj is not None:
                        # Update the local version
                        self.__proxy_ids[id(obj)] = (oid, version)
                        return obj

                opmask = description[1]
                proxy_type = proxy_types[description[2]]
                args = None
                if len(description) > 3:
                    args = self.__unmarshal(description[3])
                pushy.util.logger.debug(
                    "Unmarshalling object: %r, %r, %r",
                    oid, proxy_type, opmask)

                # New object: (id, opmask, object_type, args)
                register_proxy = \
                    lambda proxy: self.__register_proxy(proxy, oid, version)
                return Proxy(opmask, proxy_type, args, self, register_proxy)
            else:
                raise ValueError, "Invalid type: %r" % obj[0]
        else:
            # Simple type.
            return obj


    def __register_proxy(self, proxy, remote_id, version):
        id_proxy = id(proxy)
        pushy.util.logger.debug(
            "Registering a proxy: %r -> id=%r, version=%r",
            id_proxy, remote_id, version)
        if self.gc_enabled:
            ref = weakref.ref(proxy, lambda ref: self.delete(id_proxy))
        else:
            ref = lambda: proxy
        self.__proxies[remote_id] = ref
        self.__proxy_ids[id_proxy] = (remote_id, version)


    def __send_message(self, message_type, args):
        # See if there are any objects to delete. If there are, send a delete
        # message first.
        self.__send_pending_deletes()

        # Send the original message.
        thread_id = self.__peer_thread
        marshalled = self.__marshal(args)
        payload = marshal.dumps(marshalled, 0)
        m = Message(message_type, payload, thread_id)
        pushy.util.logger.debug("Sending %r -> %r", m, thread_id)
        self.__ostream.send_message(m)


    def __send_pending_deletes(self):
        """
        Checks if there are any pending deletions, and, if the garbage
        collecton timer has expired, sends a deletion message and resets the
        timer.

        Note that this method does not check whether GC is enabled, since there
        may be deletions enqueued since before GC was disabled. The initial
        check of "not self.__pending_deletes" should be sufficient to keep the
        overhead down.
        """

        if not self.__pending_deletes:
            return

        time_now = time.time()
        if time_now - self.__last_delete > self.gc_interval:
            pending = self.__pending_deletes
            self.__pending_deletes = {}
            self.__delete_lock.acquire()
            try:
                if pending:
                    self.__last_delete = time.time()
                    try:
                        pending_items = tuple(pending.items())
                        pushy.util.logger.debug("Deleting %r", pending_items)
                        payload = marshal.dumps(pending_items, 0)
                        m = Message(MessageType.delete, payload, 0, 0)
                        pushy.util.logger.debug("Sending %r", m)
                        self.__ostream.send_message(m)
                    finally:
                        pending.clear()
            finally:
                self.__delete_lock.release()


    def __recv(self):
        pushy.util.logger.debug("Waiting for message")
        m = self.__istream.receive_message()
        while m.type == MessageType.delete:
            pushy.util.logger.debug("Received %r", m)
            deleted_ids = marshal.loads(m.payload)
            self.__handle_delete(deleted_ids)
            m = self.__istream.receive_message()
        pushy.util.logger.debug("Received %r", m)
        return m


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
                args = self.__unmarshal(marshal.loads(m.payload))
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


    def __handle_delete(self, deleted):
        pushy.util.logger.debug("Handling delete: %r", deleted)
        try:
            self.__marshal_lock.acquire()
            try:
                for (id_, remote_version) in deleted:
                    if id_ in self.__proxied_objects:
                        obj, result, version = self.__proxied_objects[id_]
                        if remote_version == version:
                            del self.__proxied_objects[id_]
            finally:
                self.__marshal_lock.release()
        except:
            import traceback
            pushy.util.logger.debug(traceback.format_exc())
            raise


    def __handle_response(self, message_type, result):
        return result


    def __handle_exception(self, message_type, e):
        raise e


    def delete(self, id_proxy):
        """
        This is the weakref callback for proxied objects. This will enqueue the
        original object's ID and most recently received version for deletion at
        the originator.

        The __send_message method is reponsible for picking up these pending
        deletions, and sending them prior to any new messages.
        """

        try:
            # If the connection is not closed, send a message to the peer to
            # delete its copy.
            id_orig, version = self.__proxy_ids[id_proxy]
            del self.__proxies[id_orig]
            del self.__proxy_ids[id_proxy]
            self.__delete_lock.acquire()
            try:
                self.__pending_deletes[id_orig] = version
            finally:
                self.__delete_lock.release()
        except:
            # [lp:784619] Pushy was causing an access violation at interpreter
            # exit on Windows 7 + Python 2.7.1. This was caused by an exception
            # raised during weak references being collected. Furthermore, since
            # it occurs during interpreter finalisation, various facilities
            # (e.g. logging) are unavailable. The only logical thing to do is
            # to swallow the exception and immediately return.
            return


    def as_tuple(self, fn):
        """
        Calls the given function, ensuring that the next request sent to the
        peer will be returned as a tuple.
        """

        try:
            self.__thread_local.as_tuple_count += 1
        except AttributeError:
            self.__thread_local.as_tuple_count = 1
        res = fn()
        assert type(res) is tuple
        return res

