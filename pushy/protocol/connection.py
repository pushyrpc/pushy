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

# TODO take mutable types out of here, and handle them specially.
#      i.e. set, dict, and list.
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


class Connection:
    def __init__(self, istream, ostream, initiator=True):
        self.__istream   = istream
        self.__ostream   = ostream
        self.__initiator = initiator
        self.__lock      = threading.RLock()

        # Uncomment the following for debugging.
        #self.__istream = LoggingFile(istream, open("%d.in"%os.getpid(),"wb"))
        #self.__ostream = LoggingFile(ostream, open("%d.out"%os.getpid(),"wb"))

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
        # (Server) Contains mapping of id(obj) -> (obj, refcount, opmask)
        self.__proxied_objects = {}

    def serve_forever(self):
        while True:
            try:
                self.serve()
            except IOError: return

    def serve(self):
        self.__handle(self.__recv())

    def eval(self, expression):
        self.__lock.acquire()
        try:
            expression = self.__marshal(expression)
            self.__outstandingRequests += 1
            self.__send(Message(MessageType.evaluate, expression))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def operator(self, type_, id_, args, kwargs):
        self.__lock.acquire()
        try:
            parameters = self.__marshal((id_, tuple(args), tuple(kwargs.items())))
            self.__outstandingRequests += 1
            self.__send(Message(type_, parameters))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def getattr(self, id_, name):
        self.__lock.acquire()
        try:
            parameters = self.__marshal((id_, name))
            self.__outstandingRequests += 1
            self.__send(Message(MessageType.getattr, parameters))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def setattr(self, id_, name, value):
        self.__lock.acquire()
        try:
            parameters = self.__marshal((id_, name, value))
            self.__outstandingRequests += 1
            self.__send(Message(MessageType.setattr, parameters))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def getstr(self, id_):
        self.__lock.acquire()
        try:
            self.__outstandingRequests += 1
            self.__send(Message(MessageType.getstr, self.__marshal(id_)))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def getrepr(self, id_):
        self.__lock.acquire()
        try:
            self.__outstandingRequests += 1
            self.__send(Message(MessageType.getrepr, self.__marshal(id_)))
            return self.__waitForResponse()
        finally:
            self.__lock.release()

    def __waitForResponse(self):
        res = None
        while self.__outstandingRequests > 0:
            res = self.__handle(self.__recv())
        return res

    def __marshal(self, obj):
        # TODO check for mutable types, return proxy object.
        #      XXX perhaps we can check refcount to optimise (if 1, immutable)
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
                    #print >> open("marshal.txt", "a"), "->", repr(item)
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

    def __send(self, m):
        pushy.util.logger.debug("Sending message: %r", m)
        self.__ostream.write(m.pack())
        self.__ostream.flush()

    def __recv(self):
        pushy.util.logger.debug("Waiting for message")
        try:
            m = Message.unpack(self.__istream)
            pushy.util.logger.debug("Received message: %r", m)
        finally:
            pushy.util.logger.debug("Receive ended")
        return m

    def __send_response(self, result):
        marshaled_result = self.__marshal(result)
        self.__send(Message(MessageType.response, marshaled_result))

    def __handle(self, m):
        try:
            if m.type.name.startswith("op__"):
                return self.__handle_operator(m.type.name[2:], m.payload)
            else:
                handler = getattr(self, "_Connection__handle_%s" % m.type.name)
                return handler(m.payload)
        except SystemExit, e:
            self.__send_response(e.code)
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
            self.__send(
                Message(MessageType.exception,
                                self.__marshal((type, value, tb))))

            # Assigning traceback to a local variable within an exception
            # handler creates a cyclic reference. Manual deletion required.
            #
            # http://docs.python.org/lib/module-sys.html#l2h-5142
            del tb

    def __handle_getattr(self, payload):
        (id_, name) = self.__unmarshal(payload)
        self.__send_response(getattr(self.__proxied_objects[id_], name))

    def __handle_setattr(self, payload):
        (id_, name, value) = self.__unmarshal(payload)
        self.__send_response(setattr(self.__proxied_objects[id_], name, value))

    def __handle_getstr(self, payload):
        id_ = self.__unmarshal(payload)
        self.__send_response(str(self.__proxied_objects[id_]))

    def __handle_getrepr(self, payload):
        id_ = self.__unmarshal(payload)
        self.__send_response(repr(self.__proxied_objects[id_]))

    def __handle_evaluate(self, payload):
        self.__send_response(eval(self.__unmarshal(payload)))

    def __handle_exception(self, payload):
        self.__outstandingRequests -= 1

        # If the peer called us back when we called them, then we need to throw
        # the exception back to them.
        #
        # XXX: there's got to be a better way to do this safely. At the very
        #      least we can store the exception if the peer will potentially
        #      rethrow, in which case the client will send a "rethrow" message,
        #      and avoid repetitious marshalling/unmarshalling of exceptions.

        e = self.__unmarshal(payload)
        if self.__outstandingRequests > 0:
            self.__send(Message(MessageType.exception, self.__marshal(e)))
        else:
            raise e[1]

    def __handle_response(self, payload):
        self.__outstandingRequests -= 1
        return self.__unmarshal(payload)

    def __handle_operator(self, name, payload):
        (id_, args, kwargs) = self.__unmarshal(payload)

        # Copy the *args and **kwargs. In particular, the **kwargs dict
        # must be a real dict, because Python will do a PyDict_CheckExact
        # somewhere along the line.
        args, kwargs = list(args), dict(kwargs)

        if name == "__call__":
            self.__send_response(self.__proxied_objects[id_](*args, **kwargs))
        else:
            # TODO handle slot pointer methods specially?
            obj = self.__proxied_objects[id_]
            method = getattr(obj, name)
            self.__send_response(method(*args, **kwargs))

