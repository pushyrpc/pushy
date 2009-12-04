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

import sys, os, subprocess, tempfile
from subprocess import PIPE, STDOUT


def forknotty(delegate=None):
    """
    Fork the process, detach the child from the terminal, and then wait for it
    to complete in the parent process.

    By using this as the "preexec_fn" parameter for subprocess.Popen, one can
    run a sub-process without a terminal. If a parameter is passed to this,
    then it is expected to be another callable object which will be invoked.
    """

    # Fork.
    pid = os.fork()
    if pid > 0:
        for fd in range(subprocess.MAXFD):
            try:
                os.close(fd)
            except OSError: pass
        (pid, return_code) = os.waitpid(pid, 0)
        os._exit(return_code)

    # Detach from parent process.
    os.setsid()
    os.umask(0)

    # Fork again - some UNIXes need this.
    pid = os.fork()
    if pid > 0:
        for fd in range(subprocess.MAXFD):
            try:
                os.close(fd)
            except OSError: pass
        (pid, return_code) = os.waitpid(pid, 0)
        os._exit(return_code)

    # Call the delegate, if any.
    if delegate:
        delegate()


class _Popen(subprocess.Popen):
    """
    Wrapper for subprocess.Popen, which removes the temporary SSH_ASKPASS
    program.
    """

    def __init__(self, askpass, *args, **kwargs):
        subprocess.Popen.__init__(self, *args, **kwargs)
        self.__askpass = askpass
    def __del__(self):
        if os.path.exists(self.__askpass):
            os.remove(self.__askpass)
        subprocess.Popen.__del__(self)
    def wait(self):
        try:
            return subprocess.Popen.wait(self)
        finally:
            if os.path.exists(self.__askpass):
                os.remove(self.__askpass)


def Popen(command, password, **kwargs):
    """
    Execute the specified command in a subprocess, with SSH_ASKPASS set in such
    a way as to allow passing a password non-interactively to programs that
    need it (e.g. ssh, scp).
    """

    # Set up the "preexec" function, which is forknotty and whatever the user
    # passes in.
    preexec_fn = forknotty
    if "preexec_fn" in kwargs:
        fn = kwargs["preexec_fn"]
        preexec_fn = lambda: forknotty(fn)
    kwargs["preexec_fn"] = preexec_fn

    # Create a temporary, executable-by-owner file.
    (fd, path) = tempfile.mkstemp(text=True)
    os.chmod(path, 0700)

    try:
        tf = os.fdopen(fd, "w")
        print >> tf, "#!" + sys.executable
        print >> tf, "import os; print os.environ['SSH_PASSWORD']"
        tf.close()

        # Configure environment variables.
        environ = kwargs.get("env", os.environ)
        if environ is None:
            environ = os.environ
        environ = environ.copy()
        environ["SSH_PASSWORD"] = password
        environ["DISPLAY"] = "none:0.0"
        environ["SSH_ASKPASS"] = path
        kwargs["env"] = environ

        # Finally, call subprocess.Popen.
        return _Popen(path, command, **kwargs)
    except:
        import traceback
        traceback.print_exc()
        if os.path.exists(path):
            os.remove(path)

if __name__ == "__main__":
    program = """\
import os, sys, pprint
pprint.pprint(os.environ.items())
print sys.stdin.read()
"""

    new_env = os.environ.copy()
    new_env["ARARARARAR"] = "1231231231312"

    proc = Popen(["python", "-c", program],
                 "blahblah",
                 stdin=subprocess.PIPE,
                 stdout=subprocess.PIPE,
                 env=new_env)
    print proc.communicate("abcxyz")[0]
    proc.wait()

