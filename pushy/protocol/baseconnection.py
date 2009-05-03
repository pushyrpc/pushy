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
from pushy.protocol.message import Message, MessageType
from pushy.protocol.proxy import Proxy, ProxyType, get_opmask
import pushy.util


# This collection should contain only immutable types. Builtin, mutable types
# such as list, set and dict need to be handled specially.
marshallable_types = (
    unicode, slice, frozenset, float, basestring, long, str, int, complex,
    bool, buffer, type(None)
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
        self.event = threading.Event()
        self.result = None
    def wait(self):
        self.event.wait()
        return self.message
    def set(self, result):
        self.result = result
        self.event.set()
    def clear(self):
        self.event.clear()
        self.result = None


class BaseConnection:
    def __init__(self, istream, ostream, initiator=True):
        self.__istream           = istream
        self.__ostream           = ostream
        self.__initiator         = initiator
        self.__lock              = threading.RLock()
        self.__response_handlers = []
        self.__requests          = []

        # Uncomment the following for debugging.
        #self.__istream = LoggingFile(istream, open("%d.in"%os.getpid(),"wb"))
        #self.__ostream = LoggingFile(ostream, open("%d.out"%os.getpid(),"wb"))

        # Define message handlers.
        self.handlers = {
            MessageType.exception: self.__handle_exception,
            MessageType.response:  self.__handle_response
        }

        # Set the following to True for logging.
        if False:
            if not initiator:
                if os.path.exists("server.log"):
                    os.remove("server.log")
                pushy.util.logger.addHandler(logging.FileHandler("server.log"))
            else:
                if os.path.exists("client.log"):
                    os.remove("client.log")
                pushy.util.logger.addHandler(logging.FileHandler("client.log"))
            pushy.util.logger.setLevel(logging.DEBUG)

        self.__outstandingRequests = 0
        # (Client) Contains mapping of id(obj) -> proxy
        self.__proxies = {}
        # (Client) Contains mapping of id(proxy) -> id(obj)
        self.__proxy_ids = {}
        # (Server) Contains mapping of id(obj) -> obj
        self.__proxied_objects = {}

    def serve_forever(self):
        #handler = MessageHandler()
        while True:
            try:
                self.serve()
            except IOError: return

    def serve(self, handler=None):
        #if handler is None:
        #    handler = MessageHandler()
        self.__handle(self.__recv())

    def send_request(self, message_type, args):
        "Send a message and wait for a response."
        self.__lock.acquire()
        try:
            self.__outstandingRequests += 1
            self.__send_message(message_type, args)
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def send_response(self, result):
        self.__lock.acquire()
        try:
            self.__send_message(MessageType.response, result)
        finally:
            self.__lock.release()

    def __waitForResponse(self):
        res = None
        while self.__outstandingRequests > 0:
            res = self.__handle(self.__recv())
        return res

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

            pushy.util.logger.debug(
                "Marshalling object: %r, %r", dumps_args, obj)
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
        m = Message(message_type, self.__marshal(args))
        self.__ostream.write(m.pack())
        self.__ostream.flush()

    def __recv(self):
        pushy.util.logger.debug("Waiting for message")
        try:
            m = Message.unpack(self.__istream)
            pushy.util.logger.debug("Received %r", m.type)
            return m
        finally:
            pushy.util.logger.debug("Receive ended")

    def __handle(self, m):
        try:
            if m.type in (MessageType.response, MessageType.exception):
                self.__outstandingRequests -= 1
            args = self.__unmarshal(m.payload)
            return self.handlers[m.type](m.type, args)
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

    def __handle_response(self, message_type, result):
        return result

    def __handle_exception(self, type, e):
        # If the peer called us back when we called them, then we need to throw
        # the exception back to them.
        #
        # XXX: there's got to be a better way to do this safely. At the very
        #      least we can store the exception if the peer will potentially
        #      rethrow, in which case the client will send a "rethrow" message,
        #      and avoid repetitious marshalling/unmarshalling of exceptions.

        if self.__outstandingRequests > 0:
            self.__send_message(MessageType.exception, e)
        else:
            raise e[1]

