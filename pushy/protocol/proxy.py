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

from pushy.protocol.message import message_types
import pushy.util
import exceptions, types


class ProxyType:
    def __init__(self, code, name):
        self.code = code
        self.name = name
    def __repr__(self):
        return "ProxyType(%d, '%s')" % (self.code, self.name)
    def __str__(self):
        return self.name
    def __int__(self):
        return self.code
    def __eq__(self, other):
        if type(other) is int: return other == self.code
        elif type(other) is str: return other == self.name
        return other.__class__ is ProxyType and other.code == self.code

    @staticmethod
    def get(obj):
        # First check if it's a class
        class_ = type(obj)
        if hasattr(class_, "pushy_proxy_type"):
            return class_.pushy_proxy_type

        if isinstance(obj, Exception):
            return ProxyType.exception
        if isinstance(obj, dict):
            return ProxyType.dictionary
        if isinstance(obj, list):
            return ProxyType.list
        if isinstance(obj, set):
            return ProxyType.list
        if isinstance(obj, types.ModuleType):
            return ProxyType.module
        return ProxyType.object

    @staticmethod
    def getargs(proxy_type, obj):
        if type(proxy_type) is int:
            proxy_type = proxy_types[proxy_type]
        if proxy_type is ProxyType.exception:
            module_ = obj.__class__.__module__
            if module_ == "exceptions":
                return obj.__class__.__name__
        elif proxy_type is ProxyType.dictionary:
            args = None
            if len(obj) > 0:
                args = tuple(obj.items())
            return args
        elif proxy_type in (ProxyType.list, ProxyType.set):
            return tuple(obj)
        return None

    @staticmethod
    def getoperators(obj):
        class_ = type(obj)
        if hasattr(class_, "pushy_operator_mask"):
            return class_.pushy_operator_mask
        else:
            mask = 0L
            for t in message_types:
                if t.name.startswith("op__"):
                    mask <<= 1
                    if hasattr(obj, t.name[2:]):
                        mask = mask + 1
            return mask


def Proxy(id_, opmask, proxy_type, args, conn, on_proxy_init):
    """
    Create a proxy object, which delegates attribute access and method
    invocation to an object in a remote Python interpreter.
    """

    # Determine the class to use for the proxy type.
    if proxy_type == ProxyType.exception:
        BaseException = Exception
        if args is not None:
            BaseException = getattr(exceptions, args)
        class ExceptionProxy(BaseException):
            def __init__(self):
                on_proxy_init(self, id_)
                BaseException.__init__(self)
            def __getattribute__(self, name):
                return conn.getattr(self, name)
        ProxyClass = ExceptionProxy
    elif proxy_type == ProxyType.dictionary:
        class DictionaryProxy(dict):
            def __init__(self):
                on_proxy_init(self, id_)
                if args is not None:
                    dict.__init__(self, args)
                else:
                    dict.__init__(self)
            def __getattribute__(self, name):
                return conn.getattr(self, name)
        ProxyClass = DictionaryProxy
    elif proxy_type == ProxyType.list:
        class ListProxy(list):
            def __init__(self):
                on_proxy_init(self, id_)
                list.__init__(self, args)
            def __getattribute__(self, name):
                return conn.getattr(self, name)
        ProxyClass = ListProxy
    elif proxy_type == ProxyType.set:
        class SetProxy(set):
            def __init__(self):
                on_proxy_init(self, id_)
                set.__init__(self, args)
            def __getattribute__(self, name):
                return conn.getattr(self, name)
        ProxyClass = SetProxy
    elif proxy_type == ProxyType.module:
        class ModuleProxy(types.ModuleType):
            def __init__(self):
                on_proxy_init(self, id_)
                types.ModuleType.__init__(self, "")
                self.__importer = None
            def __getattribute__(self, name):
                try:
                    return conn.getattr(self, name)
                except AttributeError:
                    if self.__importer:
                        return self.__importer(self.__name__ + "." + name)
                    else:
                        raise
        ProxyClass = ModuleProxy
    else:
        class ObjectProxy(object):
            def __init__(self):
                on_proxy_init(self, id_)
                object.__init__(self)
            def __getattribute__(self, name):
                return conn.getattr(self, name)
        ProxyClass = ObjectProxy

    # Store the operator mask and proxy type in the class. This will be used by
    # ProxyType.getoperators() and ProxyType.get()
    #
    # XXX Important! Without this, the performance of tunnelled connections is
    #                unusably slow due to the chatter induced by many calls to
    #                hasattr/getattr.
    ProxyClass.pushy_operator_mask = opmask
    ProxyClass.pushy_proxy_type = proxy_type

    # Creates a lambda with the operator type and connection bound.
    def bound_operator(type_):
        return lambda self, *args, **kwargs: \
            (conn.operator(type_, self, args, kwargs))

    # Create proxy operators.
    operators = []
    op_index = -1
    while opmask:
        if opmask & 1:
            type_ = message_types[op_index]
            method = bound_operator(type_)
            setattr(ProxyClass, type_.name[2:], method)
        opmask >>= 1
        op_index -= 1

    # Add other standard methods.
    setattr(ProxyClass, "__del__", lambda self: conn.delete(self))
    setattr(ProxyClass, "__str__", lambda self: conn.getstr(self))
    setattr(ProxyClass, "__repr__", lambda self: conn.getrepr(self))
    setattr(ProxyClass, "__setattr__", lambda *args: conn.setattr(*args))

    # Make sure we support the iteration protocol properly
    #if hasattr(ProxyClass, "__iter__"):
    setattr(ProxyClass, "next", lambda self: conn.getattr(self, "next")())

    return ProxyClass()


###############################################################################
# A class for storing a proxy object along with its operator mask and proxy-
# type.
###############################################################################

class ProxyObject(object):
    def __init__(self, proxy, operator_mask, proxy_type):
        self.proxy = proxy
        self.operator_mask = operator_mask
        self.proxy_type = proxy_type

###############################################################################
# Create enumeration of proxy types
###############################################################################

proxy_names = (
  "object",
  "exception",
  "dictionary",
  "list",
  "set",
  "module"
)
proxy_types = []
for i,t in enumerate(proxy_names):
    p = ProxyType(i, t)
    proxy_types.append(p)
    setattr(ProxyType, t, p)

