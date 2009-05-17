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

import logging, marshal, os, struct, threading
from pushy.protocol.message import Message, MessageType, message_types
from pushy.protocol.proxy import Proxy, ProxyType, get_opmask
import pushy.util


# This collection should contain only immutable types. Builtin, mutable types
# such as list, set and dict need to be handled specially.
marshallable_types = (
    unicode, slice, frozenset, float, basestring, long, str, int, complex,
    bool, buffer, type(None)
)

# Message types that may received in response to a request.
response_types = (
    MessageType.response, MessageType.exception
)


class LoggingFile:
    def __init__(self, stream, log):
        self.stream = stream
        self.log = log
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
    def __init__(self):
        self.event       = threading.Event()
        self.message     = None
        self.syncrequest = False
    def wait(self):
        while not self.event.isSet():
            self.event.wait()
        self.event.clear()
        return self.message
    def set(self, message):
        self.message = message
        self.event.set()


class BaseConnection:
    def __init__(self, istream, ostream, initiator=True):
        self.__open         = True
        self.__istream      = istream
        self.__ostream      = ostream
        self.__initiator    = initiator
        self.__istream_lock = threading.Lock()
        self.__ostream_lock = threading.Lock()
        self.__request_lock = threading.Lock()
        self.__marshal_lock = threading.Lock()

        # Define message handlers (MessageType -> method)
        self.message_handlers = {
            MessageType.response:    self.__handle_response,
            MessageType.exception:   self.__handle_exception,
            MessageType.syncrequest: self.__handle_syncrequest
        }

        # Attributes required to track responses.
        self.__thread_local          = threading.local()
        self.__response_handlers     = []
        self.__response_handler_lock = threading.Lock()

        # Attributes required to track requests.
        self.__requests           = []
        self.__requests_condition = threading.Condition(threading.Lock())

        # Attributes required to track number of threads processing requests.
        # The message receiving thread may only attempt to receive a message
        # when there are 0 threads processing events and not waiting for a
        # response from a syncrequest.
        self.__processing = 0
        self.__processing_condition = threading.Condition(threading.Lock())

        # Uncomment the following for debugging.
        #self.__istream = LoggingFile(istream, open("%d.in"%os.getpid(),"wb"))
        #self.__ostream = LoggingFile(ostream, open("%d.out"%os.getpid(),"wb"))

        # (Client) Contains mapping of id(obj) -> proxy
        self.__proxies = {}
        # (Client) Contains mapping of id(proxy) -> id(obj)
        self.__proxy_ids = {}
        # (Server) Contains mapping of id(obj) -> obj
        self.__proxied_objects = {}

        # Start a thread which will poll for incoming messages.
        self.__receive_thread = \
            threading.Thread(target=self.receive_messages)
        self.__receive_thread.start()


    def __del__(self):
        if self.__open:
            self.close()


    def close(self, join=True):
        pushy.util.logger.debug("Closing connection")
        self.__requests_condition.acquire()
        try:
            if self.__open:
                self.__open = False
                self.__requests = []
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
                self.__requests_condition.notifyAll()
        except:
            import traceback
            traceback.print_exc()
            pushy.util.logger.debug(traceback.format_exc())
        finally:
            self.__requests_condition.release()
            if join:
                self.__receive_thread.join()


    def receive_messages(self):
        "Poll for incoming messages."
        while self.__open:
            self.__processing_condition.acquire()
            try:
                pushy.util.logger.debug(
                    "Waiting until we are allowed to read a message")

                while self.__open and self.__processing > 0:
                    self.__processing_condition.wait()
                if not self.__open:
                    return

                m = self.__recv()
                if m.type in response_types:
                    self.__response_handler_lock.acquire()
                    try:
                        response_handler = self.__response_handlers[0]
                        del self.__response_handlers[0]
                    finally:
                        self.__response_handler_lock.release()
                    response_handler.set(m)
                    if response_handler.syncrequest:
                        self.__processing += 1
                elif m.type is MessageType.syncrequest:
                    self.__response_handler_lock.acquire()
                    try:
                        response_handler = self.__response_handlers[0]
                    finally:
                        self.__response_handler_lock.release()
                    response_handler.set(m)
                    self.__processing += 1
                else:
                    self.__processing += 1
                    self.__requests_condition.acquire()
                    try:
                        self.__requests.append(m)
                        self.__requests_condition.notify()
                    finally:
                        self.__requests_condition.release()
                self.__processing_condition.release()
            except IOError:
                import traceback
                pushy.util.logger.critical(traceback.format_exc())
                self.__processing_condition.release()
                self.close(join=False)
                return
            except:
                import traceback
                pushy.util.logger.critical(traceback.format_exc())
                self.__processing_condition.release()
                self.close(join=False)
                raise


    def serve_forever(self):
        "Serve asynchronous requests from the peer forever."
        while self.__open:
            try:
                self.serve()
            except IOError:
                pushy.util.logger.debug("IOError")
                return
            except:
                import traceback
                pushy.util.logger.critical(traceback.format_exc())
                raise


    def serve(self):
        "Serve an asynchronous request from the peer."
        request = None

        # Wait for an asynchronous request.
        self.__requests_condition.acquire()
        try:
            while self.__open and len(self.__requests) == 0:
                self.__requests_condition.wait(1)
            if self.__open:
                request = self.__requests[0]
                del self.__requests[0]
        except:
            pushy.util.logger.debug("error occurred")
            raise
        finally:
            self.__requests_condition.release()

        if request is not None:
            self.__handle(request)


    def send_request(self, message_type, args):
        "Send a request message and wait for a response."
        self.__request_lock.acquire()
        try:
            # Send the request. Send it as a 'syncrequest' if the request
            # is made from the handler of a request from the peer.
            if getattr(self.__thread_local, "request_count", 0) > 0:
                pushy.util.logger.debug(
                    "Converting %r to a syncrequest", message_type)
                args = (message_type.code, self.__marshal(args))
                message_type = MessageType.syncrequest

            # Create a new response handler.
            handler = ResponseHandler()

            # If the a syncrequest is made, then reduce the 'processing'
            # count, so the message receiving thread may attempt to
            # receive messages.
            if message_type == MessageType.syncrequest:
                handler.syncrequest = True
                self.__processing_condition.acquire()
                try:
                    self.__processing -= 1
                    if self.__processing == 0:
                        self.__processing_condition.notify()
                finally:
                    self.__processing_condition.release()

            # The handler and the request message need to be added and sent in
            # the same order.
            self.__response_handler_lock.acquire()
            try:
                self.__response_handlers.append(handler)
                self.__send_message(message_type, args)
            finally:
                self.__response_handler_lock.release()
        finally:
            self.__request_lock.release()

        # Wait for the response handler to be signalled.
        return self.__waitForResponse(handler)


    def send_response(self, result):
        pushy.util.logger.debug("send_response")
        # Allow the message receiving thread to proceed. We must do this
        # *before* sending the message, in case the other side is
        # attempting to send a message at the same time.
        pushy.util.logger.debug("waiting for lock")
        self.__processing_condition.acquire()
        try:
            self.__processing -= 1
            if self.__processing == 0:
                self.__processing_condition.notify()
        finally:
            self.__processing_condition.release()

        # Now send the message.
        pushy.util.logger.debug("sending message")
        self.__send_message(MessageType.response, result)


    def __waitForResponse(self, handler):
        # Wait for the response handler to be signaled. The handler will be
        # signaled multiple times if requests come back from the peer. The
        # end is marked by a response/exception.
        m = handler.wait()
        while m.type not in response_types:
            self.__handle(m)
            m = handler.wait()
        return self.__handle(m)


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
        self.__marshal_lock.acquire()
        try:
            if i in self.__proxied_objects:
                return "p" + marshal.dumps(i)
            elif i in self.__proxy_ids:
                # Object originates at the peer.
                return "o" + marshal.dumps(self.__proxy_ids[i])
            else:
                # Temporarily release the lock.
                self.__marshal_lock.release()

                # Create new entry in proxy objects map:
                #    id -> (obj, refcount, opmask[, args])
                #
                # opmask is a bitmask defining whether or not the object
                # defines various methods (__add__, __iter__, etc.)
                try:
                    opmask = get_opmask(obj)
                    proxy_result = ProxyType.get(obj)
    
                    if type(proxy_result) is tuple:
                        obj_type, args = proxy_result
                        dumps_args = \
                            (i, opmask, int(obj_type), self.__marshal(args))
                    else:
                        obj_type = proxy_result
                        dumps_args = (i, opmask, int(obj_type))
                finally:
                    # Reacquire lock.
                    self.__marshal_lock.acquire()

                # Store the result.
                self.__proxied_objects[i] = obj
                return "p" + marshal.dumps(dumps_args, 0)
        finally:
            self.__marshal_lock.release()


    def __unmarshal(self, payload):
        if payload.startswith("s"):
            # Simple type
            return marshal.loads(buffer(payload, 1))
        elif payload.startswith("t"):
            size_size = struct.calcsize(">I")
            payload = buffer(payload, 1)
            parts = []
            while len(payload) > 0:
                size = struct.unpack(">I", payload[:size_size])[0]
                payload = buffer(payload, size_size)
                parts.append(self.__unmarshal(payload[:size]))
                payload = buffer(payload, size)
            return tuple(parts)
        elif payload.startswith("p"):
            # Proxy object
            id_ = marshal.loads(buffer(payload, 1))
            if type(id_) is tuple:
                # New object: (id, opmask, object_type)
                args = None
                if len(id_) >= 4:
                    args = self.__unmarshal(id_[3])
                p = Proxy(id_[0], id_[1], id_[2], args, self)
                self.__marshal_lock.acquire()
                try:
                    self.__proxies[id_[0]] = p
                    self.__proxy_ids[id(p)] = id_[0]
                finally:
                    self.__marshal_lock.release()
            else:
                # Known object: id
                self.__marshal_lock.acquire()
                try:
                    p = self.__proxies[id_]
                finally:
                    self.__marshal_lock.release()
            return p
        elif payload.startswith("o"):
            # The object originated here.
            id_ = marshal.loads(buffer(payload, 1))
            self.__marshal_lock.acquire()
            try:
                return self.__proxied_objects[id_]
            finally:
                self.__marshal_lock.release()
        else:
            raise ValueError, "Invalid payload prefix"


    def __send_message(self, message_type, args):
        pushy.util.logger.debug("Sending %r", message_type)
        m = Message(message_type, self.__marshal(args))
        bytes = m.pack()
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
            pushy.util.logger.debug("Received %r", m.type)
            return m
        finally:
            pushy.util.logger.debug("Receive ended")
            self.__istream_lock.release()


    def __handle(self, m):
        pushy.util.logger.debug("Handing message: %r", m)

        # Track the number of requests being processed in this thread. May be
        # greater than one, if there is to-and-fro. We need to track this so
        # we know when to send a 'syncrequest' message.
        is_request = m.type not in response_types
        if is_request:
            if hasattr(self.__thread_local, "request_count"):
                self.__thread_local.request_count += 1
            else:
                self.__thread_local.request_count = 1

        try:
            args = self.__unmarshal(m.payload)
            return self.message_handlers[m.type](m.type, args)
        except SystemExit, e:
            self.send_response(e.code)
            raise e
        except:
            if m.type is MessageType.exception:
                raise

            import sys, traceback
            (type, value, tb) = sys.exc_info()
            pushy.util.logger.debug(
                "Raising an exception", exc_info=(type, value, tb))

            # Send the above three objects to the caller
            tb = "".join(traceback.format_tb(tb))
            self.__send_message(MessageType.exception, (type, value, tb))

            # Allow the message receiving thread to proceed.
            self.__processing_condition.acquire()
            try:
                self.__processing -= 1
                if self.__processing == 0:
                    self.__processing_condition.notify()
            finally:
                self.__processing_condition.release()

            # Assigning traceback to a local variable within an exception
            # handler creates a cyclic reference. Manual deletion required.
            #
            # http://docs.python.org/lib/module-sys.html#l2h-5142
            del type, value, tb
        finally:
            if is_request:
                self.__thread_local.request_count -= 1


    def __handle_response(self, message_type, result):
        return result


    def __handle_exception(self, message_type, e):
        # If the peer called us back when we called them, then we need
        # to throw the exception back to them.
        if len(self.__response_handlers) > 0:
            self.__send_message(MessageType.exception, self.__marshal(e))
        else:
            raise e[1]


    def __handle_syncrequest(self, message_type, args):
        "Synchronous requests (i.e. requests in response to another request.)"
        real_message_type_code, payload = args
        real_message_type = message_types[real_message_type_code]
        return self.message_handlers[real_message_type](
                   real_message_type, self.__unmarshal(payload))

