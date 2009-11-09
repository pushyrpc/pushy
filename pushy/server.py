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

"""
This module provides functions for starting a Pushy server within an existing
Python process.
"""

import asyncore, os, socket, sys, threading

__all__ = ["DEFAULT_PORT", "serve_forever", "run"]

DEFAULT_PORT = 10101

def serve_forever(stdin, stdout):
    """
    Start a Pushy server-side connection for a single client, and processes
    requests forever.

    @type  stdin: file
    @param stdin: The reading file for the client.
    @type  stdout: file
    @param stdout: The writing file for the client.
    """

    import pushy.protocol, pushy.util
    c = pushy.protocol.Connection(stdin, stdout, False)
    try:
        c.serve_forever()
    finally:
        pushy.util.logger.debug("Closing connection")
        c.close()


# Define a function for making a file descriptor's mode binary.
try:
    import msvcrt
    def try_set_binary(fd):
        msvcrt.setmode(fd, os.O_BINARY)
except ImportError:
    try_set_binary = lambda fd: None


def serve_stdio_forever(stdin=sys.stdin, stdout=sys.stdout):
    """
    Serve forever on the stdio files.

    This will replace the stdin/stdout/stderr file handles with pipes,
    and then creates threads to poll these pipes in the background and
    redirect them to sys.stdin/sys.stdout/sys.stderr.
    """

    STDIN_FILENO  = 0
    STDOUT_FILENO = 1
    STDERR_FILENO = 2

    # Redirect stdout/stderr. We will redirect stdout/stderr to
    # /dev/null to start with, but this behaviour can be overridden
    # by assigning a different file to os.stdout/os.stderr.
    import pushy.util
    (so_r, so_w) = os.pipe()
    (se_r, se_w) = os.pipe()
    os.dup2(so_w, STDOUT_FILENO)
    os.dup2(se_w, STDERR_FILENO)
    for f in (so_r, so_w, se_r, se_w):
        try_set_binary(f)
    os.close(so_w)
    os.close(se_w)
    sys.stdout = open(os.devnull, "w")
    sys.stderr = sys.stdout
    so_redir = pushy.util.StdoutRedirector(so_r)
    se_redir = pushy.util.StderrRedirector(se_r)
    so_redir.start()
    se_redir.start()

    # Start the request servicing loop.
    try:
        serve_forever(stdin, stdout)
        stdout.close()
        stdin.close()
        os.close(STDOUT_FILENO)
        os.close(STDERR_FILENO)
        so_redir.join()
        se_redir.join()
    finally:
        stdout.close()
        stdin.close()


class pushy_server(asyncore.dispatcher):
    def __init__(self, port):
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.set_reuse_addr()
        self.bind(("", port))
        self.listen(3)

    def handle_accept(self):
        (sock,addr) = self.accept()
        sock.setblocking(1)
        stdin = sock.makefile("rb")
        stdout = sock.makefile("wb")
        threading.Thread(target=serve_forever, args=(stdin,stdout)).start()

    def handle_close(self):
        self.close()


def run(port = DEFAULT_PORT):
    """
    Start a socket server, which creates Pushy connections as client
    connections come in.

    @param port: The port number to listen on, or "stdio" to use standard I/O.
    """

    if port == "stdio":
        serve_forever(sys.stdin, sys.stdout)
    else:
        server = pushy_server(int(port))
        while True:
            asyncore.loop()


if __name__ == "__main__":
    import pushy.util, logging, sys
    pushy.util.logger.addHandler(logging.StreamHandler())
    run(*sys.argv[1:])

