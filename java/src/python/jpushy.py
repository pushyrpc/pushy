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

from DocXMLRPCServer import DocXMLRPCServer as XMLRPCServer
from DocXMLRPCServer import DocXMLRPCRequestHandler
import glob, os, sys, UserDict, xmlrpclib


def isdict(obj):
    if isinstance(obj, dict):
        return True
    return isinstance(obj, UserDict.UserDict)


class JPushyObject:
    "A wrapper class for objects that can't be marshalled by XML-RPC server."

    def __init__(self, object_pool, object):
        self.object_pool = object_pool
        self.object = object
        self.object_pool[id(self.object)] = self.object

    def __cmp__(self, other):
        if isinstance(other, JPushyObject):        
            other = other.object
        return cmp(self.object, other)

    def __str__(self):
        return str(self.object)

    def __repr__(self):
        return "<JPushyObject %s>" % repr(self.object)

    def decode(self, data):
        id_ = int(str(data))
        self.object = self.object_pool[id_] 

    def encode(self, out):
        out.write("<value><pobject")
        if isinstance(self.object, list):
            out.write(" type=\"list\"")
        elif isdict(self.object):
            out.write(" type=\"dict\"")
        out.write(">%d</pobject></value>" % id(self.object))

# Lists are mutable. Don't convert to Object[].
del xmlrpclib.Marshaller.dispatch[list]
# Add JPushyObject to WRAPPERS.
xmlrpclib.WRAPPERS = xmlrpclib.WRAPPERS + (JPushyObject,)        


class JPushyFunctions:
    "Defines functions exposed by the XML-RPC server."

    def __init__(self):
        self.__connection  = None
        self.__commands    = {}
        self.__object_pool = {}

    def __wrap(self, value):
        if type(value) not in xmlrpclib.Marshaller.dispatch:
            value = JPushyObject(self.__object_pool, value)
        return value

    def __unwrap(self, args, kwargs):
        if args == None:
            args = []
        else:
            for i in range(len(args)):
                if isinstance(args[i], xmlrpclib.Binary):
                    args[i] = str(args[i])
        if kwargs == None:
            kwargs = {}
        else:
            for (k,v) in kwargs.items():
                if isinstance(v, xmlrpclib.Binary):
                    kwargs[k] = str(v)
        return args, kwargs

    def connect(self, address, kwargs):
        "Register a host for connecting to."

        assert self.__connection is None
        kwargs = self.__unwrap(None, kwargs)[1]
        import pushy
        self.__connection = pushy.connect(address, **kwargs) 

    def evaluate(self, expression):
        try:
            assert self.__connection is not None 
            return self.__wrap(
                       self.__connection.modules.__builtin__.eval(expression))
        except:
            import traceback
            raise Exception, traceback.format_exc() 

    def call(self, function, args, kwargs):    
        try:
            assert self.__connection is not None
            parts = function.split(".")
            assert len(parts) > 1
            object = getattr(self.__connection.modules, parts[0])
            for i in range(1, len(parts)):
                object = getattr(object, parts[i])
            args, kwargs = self.__unwrap(args, kwargs)
            return self.__wrap(object(*args, **kwargs))
        except:
            import traceback
            raise Exception, traceback.format_exc() 

    def getitem_(self, objid, key):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            return self.__wrap(obj[key])
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def setitem_(self, objid, key, value):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            obj[key] = value
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def getattr_(self, objid, key):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            return self.__wrap(getattr(obj, key))
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def hasattr_(self, objid, key):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            return hasattr(obj, key)
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def getlen_(self, objid):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            return len(obj)
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def callobj(self, objid, args, kwargs):
        try:
            assert self.__connection is not None
            objid = long(objid)
            obj = self.__object_pool[objid]
            args, kwargs = self.__unwrap(args, kwargs)
            return self.__wrap(obj(*args, **kwargs))
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def putfile(self, src, dest):
        try:
            assert self.__connection is not None
            self.__connection.putfile(src, dest)
        except:
            import traceback
            raise Exception, traceback.format_exc()

    def getfile(self, src, dest):
        try:
            assert self.__connection is not None
            self.__connection.getfile(src, dest)
        except:
            import traceback
            raise Exception, traceback.format_exc()


class JPushyXMLRPCRequestHandler(DocXMLRPCRequestHandler):
    "Subclass of DocXMLRPCRequestHandler, which ignores log messages."
    def log_message(self, format, *args):
        pass


def start():
    """
    Start the XML-RPC server, and print out the port number that it is
    listening on.
    """

    server = XMLRPCServer(("127.0.0.1", 0), JPushyXMLRPCRequestHandler)

    # print out the port number
    print server.socket.getsockname()[1]
    sys.stdout.flush()

    server.allow_none = True
    server.register_introspection_functions()
    server.register_instance(JPushyFunctions())
    server.serve_forever()


if __name__ == "__main__":
    start()

