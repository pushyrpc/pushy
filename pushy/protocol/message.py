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

import os, struct, thread
import pushy.util

class MessageType:
    "A class for describing the type of a message."

    def __init__(self, code, name):
        self.code = code
        self.name = name
    def __repr__(self):
        return "MessageType(%d, '%s')" % (self.code, self.name)
    def __int__(self):
        return self.code
    def __str__(self):
        return self.name
    def __hash__(self):
        return self.code
    def __eq__(self, other):
        if type(other) is int: return other == self.code
        elif type(other) is str: return other == self.name
        return other.__class__ is MessageType and other.code == self.code


class Message:
    PACKING_FORMAT = ">BqqI"
    PACKING_SIZE   = struct.calcsize(PACKING_FORMAT)

    def __init__(self, type, payload, target=0, source=None):
        self.type     = type
        self.payload  = payload
        self.target   = target
        if source is None:
            source = thread.get_ident()
        self.source = source

    def __eq__(self, other):
        if other.__class__ is not Message: return False
        return self.type == other.type and \
               self.source == other.source and \
               self.target == other.target and \
               self.payload == other.payload

    def __repr__(self):
        return "Message(%r, %d->%d, %r [%d bytes])" % \
                   (self.type, self.source, self.target, self.payload,
                    len(self.payload))

    def pack(self):
        result = struct.pack(self.PACKING_FORMAT, int(self.type), self.source,
                             self.target, len(self.payload))
        return result + self.payload

    @staticmethod
    def unpack(file):
        # Read the message header.
        data = ""
        while len(data) < Message.PACKING_SIZE:
            try:
                partial = file.read(Message.PACKING_SIZE - len(data))
            except Exception, e:
                raise IOError, e
            if partial == "":
                raise IOError, "End of file"
            data += partial
        (type, source, target, length) = \
            struct.unpack(Message.PACKING_FORMAT, data)
        type = message_types[type]

        # Read message payload.
        payload = ""
        if length:
            while len(payload) < length:
                try:
                    partial = file.read(length - len(payload))
                except Exception, e:
                    raise IOError, e
                if partial == "":
                    raise IOError, "End of file"
                payload += partial

        pushy.util.logger.debug(
            "Read %d, %r %r", len(data) + len(payload),
            data + payload, (type, source, target, length))
        return Message(type, payload, target, source)

###############################################################################
# Create enumeration of message types
###############################################################################

message_names = (
  "evaluate",
  "response",
  "exception",
  "getattr",
  "setattr",
  "getstr",
  "getrepr",

  # All object ops go at the end
  "op__call__",
  "op__lt__",
  "op__le__",
  "op__eq__",
  "op__ne__",
  "op__gt__",
  "op__ge__",
  "op__cmp__",
  "op__rcmp__",
  "op__hash__",
  "op__nonzero__",
  "op__unicode__",
  "op__len__",
  "op__getitem__",
  "op__setitem__",
  "op__delitem__",
  "op__iter__",
  "op__contains__",
  #"op__slots__",
  "op__get__",
  "op__set__",
  "op__delete__",
  "op__getslice__",
  "op__setslice__",
  "op__delslice__",
  "op__add__",
  "op__sub__",
  "op__mul__",
  "op__floordiv__",
  "op__mod__",
  "op__divmod__",
  "op__pow__",
  "op__lshift__",
  "op__rshift__",
  "op__and__",
  "op__xor__",
  "op__or__",
  "op__div__",
  "op__truediv__",
  "op__radd__",
  "op__rsub__",
  "op__rdiv__",
  "op__rtruediv__",
  "op__rfloordiv__",
  "op__rmod__",
  "op__rdivmod__",
  "op__rpow__",
  "op__rlshift__",
  "op__rrshift__",
  "op__rand__",
  "op__rxor__",
  "op__ror__",
  "op__iadd__",
  "op__isub__",
  "op__imul__",
  "op__idiv__",
  "op__itruediv__",
  "op__ifloordiv__",
  "op__imod__",
  "op__ipow__",
  "op__ilshift__",
  "op__irshift__",
  "op__iand__",
  "op__ixor__",
  "op__ior__",
  "op__neg__",
  "op__pos__",
  "op__abs__",
  "op__invert__",
  "op__complex__",
  "op__int__",
  "op__long__",
  "op__float__",
  "op__oct__",
  "op__hex__",
  "op__index__",
  "op__coerce__",
  "op__enter__",
  "op__exit__",
)
message_types = []
for i,t in enumerate(message_names):
    m = MessageType(i, t)
    message_types.append(m)
    setattr(MessageType, t, m)
message_types = tuple(message_types)

