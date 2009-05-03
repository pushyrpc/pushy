# Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
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

from pushy.protocol.baseconnection import BaseConnection
import logging, marshal, os, struct, threading
from pushy.protocol.message import Message, MessageType, message_types
from pushy.protocol.proxy import Proxy, ProxyType, get_opmask
import pushy.util


class Connection(BaseConnection):
    def __init__(self, istream, ostream, initiator=True):
        BaseConnection.__init__(self, istream, ostream, initiator)

        # Add message handlers
        self.handlers.update({
            MessageType.evaluate:    self.__handle_evaluate,
            MessageType.getattr:     self.__handle_getattr,
            MessageType.setattr:     self.__handle_setattr,
            MessageType.getstr:      self.__handle_getstr,
            MessageType.getrepr:     self.__handle_getrepr,
            MessageType.op__call__:  self.__handle_call,
        })
        for message_type in message_types:
            if message_type.name == "__call__":
                continue
            if message_type.name.startswith("op__"):
                self.handlers[message_type] = self.__handle_operator

    def eval(self, expression):
        return self.send_request(MessageType.evaluate, expression)

    def operator(self, type_, object, args, kwargs):
        return self.send_request(
                   type_, (object, tuple(args), tuple(kwargs.items())))

    def getattr(self, object, name):
        return self.send_request(MessageType.getattr, (object, name))

    def setattr(self, object, name, value):
        return self.send_request(MessageType.setattr, (object, name, value))

    def getstr(self, object):
        return self.send_request(MessageType.getstr, object)

    def getrepr(self, object):
        return self.send_request(MessageType.getrepr, object)

    def __handle_getattr(self, type, args):
        (object, name) = args
        self.send_response(getattr(object, name))

    def __handle_setattr(self, type, args):
        (object, name, value) = args
        self.send_response(setattr(object, name, value))

    def __handle_getstr(self, type, object):
        self.send_response(str(object))

    def __handle_getrepr(self, type, object):
        self.send_response(repr(object))

    def __handle_evaluate(self, type, expression):
        self.send_response(eval(expression))

    def __handle_call(self, type, args):
        (object, args, kwargs) = args

        # Copy the *args and **kwargs. In particular, the **kwargs dict
        # must be a real dict, because Python will do a PyDict_CheckExact
        # somewhere along the line.
        args, kwargs = list(args), dict(kwargs)
        self.send_response(object(*args, **kwargs))

    def __handle_operator(self, type, args):
        (object, args, kwargs) = args

        # Copy the *args and **kwargs. In particular, the **kwargs dict
        # must be a real dict, because Python will do a PyDict_CheckExact
        # somewhere along the line.
        args, kwargs = list(args), dict(kwargs)

        # TODO handle slot pointer methods specially?
        name = type.name[2:]
        method = getattr(object, name)
        self.send_response(method(*args, **kwargs))

