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

"""
This module provides the code to used to start a remote Pushy server running,
and initiate a connection.
"""

import __builtin__
import imp
import inspect
import marshal
import os
import struct
import sys
import threading

# Import zipimport, for use in PushyPackageLoader.
try:
    import zipimport
except ImportError:
    zipimport = None

# Import hashlib if it exists, with md5 as a backup.
try:
    import hashlib
except ImportError:
    import md5 as hashlib

__all__ = ["PushyPackageLoader", "InMemoryLoader", "InMemoryLoader",
           "AutoImporter", "PushyClient", "connect"]

class PushyPackageLoader:
    """
    This class loads packages and modules into the format expected by
    PushyServer.InMemoryImporter.
    """
    def load(self, *args):
        self.__packages = {}
        self.__modules = {}
        for arg in args:
            self.__load(arg)
        return self.__packages, self.__modules

    def __load(self, package_or_module):
        if hasattr(package_or_module, "__path__"):
            self.__loadPackage(package_or_module)
        else:
            self.__loadModule(package_or_module)

    def __loadPackage(self, package):
        if len(package.__path__) == 0:
            return

        path = package.__path__[0]
        if os.sep != "/":
            path = path.replace(os.sep, "/")
    
        # Check to see if the package was loaded from a zip file.
        is_zip = False
        if zipimport is not None:
            if hasattr(package, "__loader__"):
                is_zip = isinstance(package.__loader__, zipimport.zipimporter)

        if is_zip:
            zippath = package.__loader__.archive
            subdir = path[len(zippath)+1:] # Skip '/'

            import zipfile, pushy.util
            zf = zipfile.ZipFile(zippath)
            walk_fn = lambda: pushy.util.zipwalk(zf, subdir)
            read_fn = lambda path: zf.read(path)
        else:
            prefix_len = len(path) - len(package.__name__)
            #prefix_len = path.rfind("/") + 1
            walk_fn = lambda: os.walk(path)
            read_fn = lambda path: open(path).read()
    
        for root, dirs, files in walk_fn():
            if os.sep != "/":
                root = root.replace(os.sep, "/")
            if not is_zip:
                modulename = root[prefix_len:].replace("/", ".")
            else:
                modulename = root.replace("/", ".")
     
            if "__init__.py" in files:
                modules = {}
                for f in [f for f in files if f.endswith(".py")]:
                    source = read_fn(root + "/" + f)
                    source = source.replace("\r", "")
                    modules[f[:-3]] = marshal.dumps(source, 0)
     
                parent = self.__packages
                parts = modulename.split(".")
                for part in parts[:-1]:
                    if part not in parent:
                        parent[part] = [root[:prefix_len] + part, {}, {}]

                        # Parent must have an __init__.py, else it wouldn't be
                        # a package.
                        source = read_fn(root[:prefix_len] + part + \
                                         "/__init__.py")
                        source = source.replace("\r", "")
                        #parent[part][2]["__init__"] = marshal.dumps(source, 0)
                    parent = parent[part][1]
                parent[parts[-1]] = [root, {}, modules]

    def __loadModule(self, module):
        source = inspect.getsource(module)
        self.__modules[module.__name__] = marshal.dumps(source, 0)

class InMemoryImporter:
    """
    Custom "in-memory" importer, which imports preconfigured packages. This is
    used to load modules without requiring the Python source to exist on disk.
    Thus, one can load modules on a remote machine without copying the source
    to the machine.

    See U{PEP 302<http://www.python.org/dev/peps/pep-0302>} for information on
    custom importers.
    """

    def __init__(self, packages, modules):
        # name => [packagedir, subpackages, modules]
        self.__packages = packages
        self.__modules  = modules

    def find_module(self, fullname, path=None):
        parts = fullname.split(".")
        parent = [None, self.__packages, self.__modules]
        for part in parts[:-1]:
            if parent[1].has_key(part):
                parent = parent[1][part]
            else:
                return

        if parent[1].has_key(parts[-1]):
            package = parent[1][parts[-1]]
            filename = package[0] + "/__init__.pyc"
            code = marshal.loads(package[2]["__init__"])
            return InMemoryLoader(fullname, filename, code, path=[])
        elif parent[2].has_key(parts[-1]):
            if parent[0]:
                filename = "%s/%s.pyc" % (parent[0], parts[-1])
            else:
                filename = parts[-1] + ".pyc"
            code = marshal.loads(parent[2][parts[-1]])
            return InMemoryLoader(fullname, filename, code)

class InMemoryLoader:
    """
    Custom "in-memory" package/module loader (used by InMemoryImporter).
    """

    def __init__(self, fullname, filename, code, path=None):
        self.__fullname = fullname
        self.__filename = filename
        self.__code     = code
        self.__path     = path

    def load_module(self, fullname):
        module = imp.new_module(fullname)
        module.__file__ = self.__filename
        if self.__path is not None:
            module.__path__ = self.__path
        module.__loader__ = self
        sys.modules[fullname] = module
        exec self.__code in module.__dict__
        return module

def try_set_binary(fd):
    try:
        import msvcrt
        msvcrt.setmode(fd, os.O_BINARY)
    except ImportError: pass

def pushy_server(stdin, stdout):
    import sys

    # Reconstitute the package hierarchy delivered from the client
    (packages, modules) = marshal.load(stdin)

    # Add the package to the in-memory package importer
    importer = InMemoryImporter(packages, modules)
    sys.meta_path.insert(0, importer)

    import pushy.server
    pushy.server.serve_stdio_forever(stdin, stdout)

###############################################################################
# Auto-import object.

# Define a remote function which we'll use to import modules, to avoid lots of
# exception passing due to misuse of "getattr" on the AutoImporter.
quiet_import = """
def quiet_import(name):
    try:
        module = __import__(name)
        # Traverse down to the target module.
        if "." in name:
            for part in name.split(".")[1:]:
                module = getattr(module, part)
        return module
    except ImportError:
        return None
""".strip()

class AutoImporter(object):
    "RPyc-inspired automatic module importer."

    def __init__(self, client):
        self.__client = client
        locals = {}

        remote_compile = self.__client.eval("compile")
        code = remote_compile(quiet_import, "", "exec")
        self.__client.eval(code, locals=locals)
        self.__import = locals["quiet_import"]

    def __getattr__(self, name):
        try:
            value = self.remote_import(name)
            # Modify the module proxy to automatically import modules in
            # the same manner as this auto-importer on a failed getattr.
            value._ModuleProxy__importer = lambda name: getattr(self, name)
            return value
        except:
            pass
        return object.__getattribute__(self, name)

    def remote_import(self, name):
        return self.__import(name)

###############################################################################

# Read the source for the server into a string. If we're the server, we'll
# have defined __builtin__.pushy_source (by the "realServerLoaderSource").
if not hasattr(__builtin__, "pushy_source"):
    if "__loader__" in locals():
        serverSource = __loader__.get_source(__name__)
        serverSource = marshal.dumps(serverSource, 0)
    else:
        serverSource = open(inspect.getsourcefile(AutoImporter)).read()
        serverSource = marshal.dumps(serverSource, 0)
else:
    serverSource = __builtin__.pushy_source
md5ServerSource = hashlib.md5(serverSource).digest()

# This is the program we run on the command line. It'll read in a
# predetermined number of bytes, and execute them as a program. So once we
# start the process up, we immediately write the "real" server source to it.
realServerLoaderSource = """
import __builtin__, os, marshal, sys
try:
    import hashlib
except ImportError:
    import md5 as hashlib

# Back up old stdin/stdout.
stdout = os.fdopen(os.dup(sys.stdout.fileno()), "wb", 0)
stdin = os.fdopen(os.dup(sys.stdin.fileno()), "rb", 0)
try:
    import msvcrt
    msvcrt.setmode(stdout.fileno(), os.O_BINARY)
    msvcrt.setmode(stdin.fileno(), os.O_BINARY)
except ImportError: pass
sys.stdout.close()
sys.stdin.close()

serverSourceLength = %d
serverSource = stdin.read(serverSourceLength)
while len(serverSource) < serverSourceLength:
    serverSource += stdin.read(serverSourceLength - len(serverSource))

try:
    assert hashlib.md5(serverSource).digest() == %r
    __builtin__.pushy_source = serverSource
    serverCode = marshal.loads(serverSource)
    exec serverCode
    pushy_server(stdin, stdout)
except:
    import traceback
    # Uncomment for debugging
    # traceback.print_exc(file=open("stderr.txt", "w"))
    raise
""".strip() % (len(serverSource), md5ServerSource)

# Avoid putting any quote characters in the program at all costs.
serverLoaderSource = \
  "exec reduce(lambda a,b: a+b, map(chr, (%s)))" \
      % str((",".join(map(str, map(ord, realServerLoaderSource)))))

###############################################################################

def get_transport(target):
    import pushy

    colon = target.find(":")
    if colon == -1:
        raise Exception, "Missing colon from transport address"

    transport_name = target[:colon]

    if transport_name not in pushy.transports:
        raise Exception, "Transport '%s' does not exist in pushy.transport" \
                             % transport_name

    transport = pushy.transports[transport_name]
    if transport is None:
        transport = __import__("pushy.transport.%s" % transport_name,
                               globals(), locals(), ["pushy.transport"])
        pushy.transports[transport_name] = transport
    return (transport, target[colon+1:])

###############################################################################

logid = 0

class PushyClient(object):
    "Client-side Pushy connection initiator."

    pushy_packages = None
    packages_lock  = threading.Lock()

    def __init__(self, target, python="python", **kwargs):
        (transport, address) = get_transport(target)

        # Start the server
        command = [python, "-u", "-c", serverLoaderSource]
        kwargs["address"] = address
        self.server = transport.Popen(command, **kwargs)

        try:
            if not self.server.daemon:
                # Write the "real" server source to the remote process
                self.server.stdin.write(serverSource)
                self.server.stdin.flush()
                # Send the packages over to the server
                packages = self.__load_packages()
                marshal.dump(packages, self.server.stdin, 1)
                self.server.stdin.flush()

            # Finally... start the connection. Magic! 
            import pushy.protocol
            remote = pushy.protocol.Connection(self.server.stdout,
                                               self.server.stdin)

            # Start a thread for processing asynchronous requests from the peer
            self.serve_thread = threading.Thread(target=remote.serve_forever)
            self.serve_thread.setDaemon(True)
            self.serve_thread.start()

            # putfile/getfile
            self.putfile = getattr(self.server, "putfile", self.__putfile)
            self.getfile = getattr(self.server, "getfile", self.__getfile)

            self.remote  = remote
            """The L{connection<pushy.protocol.Connection>} for the remote
               interpreter"""

            # Create an auto-importer.
            self.modules = AutoImporter(self)
            "An instance of L{AutoImporter} for the remote interpreter."
            rsys = self.modules.sys
            rsys.stdout = sys.stdout
            rsys.stderr = sys.stderr

            # Specify the filesystem interface
            if hasattr(self.server, "fs"):
                self.fs = self.server.fs
            else:
                self.fs = self.modules.os
        except:
            import traceback
            traceback.print_exc()
            errorlines = self.server.stderr.readlines()
            print >> sys.stderr, ""
            for line in errorlines:
                print >> sys.stderr, "[remote]", line.rstrip()
            print >> sys.stderr, ""

            self.server = None
            self.remote = None
            self.serve_thread = None
            raise


    # With-statement/context-manager support
    def __enter__(self):
        return self
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False


    def __get_gc_enabled(self):
        return self.remote.gc_enabled
    def __set_gc_enabled(self, enabled):
        self.remote.gc_enabled = enabled
    gc_enabled = property(__get_gc_enabled, __set_gc_enabled)


    def __get_gc_interval(self):
        return self.remote.gc_interval
    def __set_gc_interval(self, interval):
        self.remote.gc_interval = interval
    gc_interval = property(__get_gc_interval, __set_gc_interval)


    def __putfile(self, local, remote):
        "Transport-independent fallback for putfile."
        f_read = open(local, "rb")
        f_write = self.modules.__builtin__.open(remote, "wb")
        try:
            d = f_read.read(8192)
            while len(d) > 0:
                f_write.write(d)
                d = f_read.read(8192)
        finally:
            f_write.close()
            f_read.close()


    def __getfile(self, remote, local):
        "Transport-independent fallback for getfile."
        f_read = self.modules.__builtin__.open(remote, "rb")
        try:
            f_write = open(local, "wb")
            try:
                d = f_read.read(8192)
                while len(d) > 0:
                    f_write.write(d)
                    d = f_read.read(8192)
            finally:
                f_write.close()
        finally:
            f_read.close()


    def __del__(self):
        try:
            self.close()
        except:
            pass


    def remote_import(self, name):
        "Import a remote Python module."
        return self.modules.remote_import(name)


    def eval(self, code, globals=None, locals=None):
        """
        Evaluate an expression or code object in the remote Python
        interpreter.
        """
        return self.remote.eval(code, globals, locals)


    def compile(self, source, mode="exec"):
        """
        Compiles Python source into a code object, or a local function into a
        remotely defined function that executes wholly in the remote Python
        interpreter.
        """

        if inspect.isfunction(source):
            func = source
            try:
                remote_compile = self.eval("compile")

                # Get and unindent the source.
                source = inspect.getsourcelines(func)[0]
                unindent_len = len(source[0]) - len(source[0].lstrip())
                source = "".join([l[unindent_len:] for l in source])

                code = remote_compile(source, inspect.getfile(func), "exec")
                locals = {}
                self.eval(code, locals=locals)
                # We can't use func_name, because that doesn't apply to
                # lambdas. Lambdas seem to have their assigned name built-in,
                # but I'm not sure how to extract it.
                return locals.values()[0]
            except IOError:
                from pushy.util.clone_function import clone_function
                return self.compile(clone_function)(func)
        else:
            return self.eval("compile")(source, "<pushy>", mode)


    def execute(self, source, globals=None, locals=None):
        """
        Shortcut for self.eval(self.compile(source), globals, locals).
        """
        self.eval(self.compile(source), globals, locals)


    def close(self):
        "Close the connection."
        if self.remote is not None:
            self.remote.close()
        if self.serve_thread is not None:
            self.serve_thread.join()
        if self.server is not None:
            self.server.close()


    def __load_packages(self):
        if self.pushy_packages is None:
            self.packages_lock.acquire()
            try:
                import pushy
                loader = PushyPackageLoader()
                self.pushy_packages = loader.load(pushy)
            finally:
                self.packages_lock.release()
        return self.pushy_packages


    def enable_logging(self, client=True, server=False):
        global logid
        logid += 1

        if client:
            import pushy.util, logging
            filename = "pushy-client.%d.log" % logid
            if os.path.exists(filename):
                os.remove(filename)
            handler = logging.FileHandler(filename)
            handler.setFormatter(logging.Formatter(
                "[%(process)d:(%(threadName)s:%(thread)d)] %(message)s"))
            pushy.util.logger.addHandler(handler)
            pushy.util.logger.setLevel(logging.DEBUG)
            pushy.util.logger.disabled = False

        if server:
            filename = "pushy-server.%d.log" % logid
            ros = self.modules.os
            if ros.path.exists(filename):
                ros.remove(filename)
            handler = self.modules.logging.FileHandler(filename)
            handler.setFormatter(self.modules.logging.Formatter(
                "[%(process)d:(%(threadName)s:%(thread)d)] %(message)s"))
            self.modules.pushy.util.logger.addHandler(handler)
            self.modules.pushy.util.logger.setLevel(self.modules.logging.DEBUG)
            self.modules.pushy.util.logger.disabled = False


def connect(target, **kwargs):
    """
    Creates a Pushy connection.

    e.g. C{pushy.connect("ssh:somewhere.com", username="me", password="...")}

    @type  target: string
    @param target: A string specifying the target to connect to, in the format
                   I{transport}:I{address}.
    @param kwargs: Any arguments supported by the transport specified by
                   I{target}.
    @rtype: L{PushyClient}
    """
    return PushyClient(target, **kwargs)

