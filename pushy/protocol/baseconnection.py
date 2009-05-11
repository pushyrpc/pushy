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
        self.event   = threading.Event()
        self.message = None
    def wait(self):
        while not self.event.is_set():
            self.event.wait(1)
        self.event.clear()
        return self.message
    def set(self, message):
        self.message = message
        self.event.set()


class BaseConnection(threading.Thread):
    def __init__(self, istream, ostream, initiator=True):
        threading.Thread.__init__(self)
        #self.setDaemon(True)

        self.__open              = True
        self.__istream           = istream
        self.__ostream           = ostream
        self.__initiator         = initiator
        self.__istream_lock      = threading.Lock()
        self.__ostream_lock      = threading.Lock()
        self.__lock              = threading.RLock()

        # Define message handlers (MessageType -> method)
        self.message_handlers = {
            MessageType.response:    self.__handle_response,
            MessageType.exception:   self.__handle_exception,
            MessageType.syncrequest: self.__handle_syncrequest
        }

        # Attributes required to track responses.
        self.__thread_local      = threading.local()
        self.__response_handlers = []

        # Attributes required to track requests.
        self.__requests           = []
        self.__requests_condition = threading.Condition()

        # Uncomment the following for debugging.
        #self.__istream = LoggingFile(istream, open("%d.in"%os.getpid(),"wb"))
        #self.__ostream = LoggingFile(ostream, open("%d.out"%os.getpid(),"wb"))

        # (Client) Contains mapping of id(obj) -> proxy
        self.__proxies = {}
        # (Client) Contains mapping of id(proxy) -> id(obj)
        self.__proxy_ids = {}
        # (Server) Contains mapping of id(obj) -> obj
        self.__proxied_objects = {}

        # Start this thread running, which will poll for incoming messages.
        self.start()

    def __del__(self):
        if self.__open:
            self.close()

    def close(self, join=True):
        pushy.util.logger.debug("entered close")
        self.__requests_condition.acquire()
        pushy.util.logger.debug("got lock")
        try:
            if self.__open:
                self.__open = False
                self.__requests = []
                self.__ostream_lock.acquire()
                try:
                    self.__ostream.close()
                finally:
                    self.__ostream_lock.release()
                self.__istream_lock.acquire()
                try:
                    self.__istream.close()
                finally:
                    self.__istream_lock.release()
                self.__requests_condition.notify_all()
        except:
            import traceback
            traceback.print_exc()
            pushy.util.logger.debug(traceback.format_exc())
        finally:
            self.__requests_condition.release()
            if join:
                pushy.util.logger.debug("waiting for join")
                self.join()
        pushy.util.logger.debug("leaving close")

    def run(self):
        "Poll for incoming messages."
        while self.__open:
            try:
                pushy.util.logger.debug("top of loop")
                m = self.__recv()
                pushy.util.logger.debug("after receive")
                if m.type in response_types:
                    response_handler = self.__response_handlers.pop()
                    response_handler.set(m)
                elif m.type is MessageType.syncrequest:
                    response_handler = self.__response_handlers[-1]
                    response_handler.set(m)
                else:
                    self.__requests_condition.acquire()
                    try:
                        self.__requests.append(m)
                        self.__requests_condition.notify()
                    finally:
                        self.__requests_condition.release()
            except IOError:
                import traceback
                pushy.util.logger.critical(traceback.format_exc())
                self.close(join=False)
                return
            except:
                import traceback
                pushy.util.logger.critical(traceback.format_exc())
                self.close(join=False)
                raise
            finally:
                pushy.util.logger.debug("bottom of loop")

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
        self.__lock.acquire()
        try:
            # Push a new response handler onto the stack.
            handler = ResponseHandler()
            self.__response_handlers.append(handler)

            # Send the request. Send it as a 'syncrequest' if the request
            # is made from the handler of a request from the peer.
            if getattr(self.__thread_local, "request_count", 0) > 0:
                pushy.util.logger.debug("Converting to a syncrequest")
                args = (message_type.code, self.__marshal(args))
                message_type = MessageType.syncrequest
            self.__send_message(message_type, args)

            # Wait for the response handler to be signalled.
            return self.__waitForResponse(handler)
        finally:
            self.__lock.release()

    def send_response(self, result):
        self.__lock.acquire()
        try:
            self.__send_message(MessageType.response, result)
        finally:
            self.__lock.release()

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
        if i in self.__proxied_objects:
            return "p" + marshal.dumps(i)
        elif i in self.__proxy_ids:
            # Object originates at the peer.
            return "o" + marshal.dumps(self.__proxy_ids[i])
        else:
            # Create new entry in proxy objects map:
            #    id -> (obj, refcount, opmask[, args])
            #
            # opmask is a bitmask defining whether or not the object
            # defines various methods (__add__, __iter__, etc.)
            opmask = get_opmask(obj)
            proxy_result = ProxyType.get(obj)
            self.__proxied_objects[i] = obj

            if type(proxy_result) is tuple:
                obj_type, args = proxy_result
                dumps_args = (i, opmask, int(obj_type), self.__marshal(args))
            else:
                obj_type = proxy_result
                dumps_args = (i, opmask, int(obj_type))

            #pushy.util.logger.debug(
            #    "Marshalling object: %r, %r", dumps_args, obj)
            return "p" + marshal.dumps(dumps_args, 0)

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
                self.__proxies[id_[0]] = p
                self.__proxy_ids[id(p)] = id_[0]
            else:
                # Known object: id
                p = self.__proxies[id_]
            return p
        elif payload.startswith("o"):
            # The object originated here.
            id_ = marshal.loads(buffer(payload, 1))
            return self.__proxied_objects[id_]
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

