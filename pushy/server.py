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

import asyncore, socket, threading

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

    import pushy.protocol
    c = pushy.protocol.Connection(stdin, stdout, False)
    c.serve_forever()


class pushy_server(asyncore.dispatcher):
    def __init__(self, port):
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.set_reuse_addr()
        self.bind(("", port))
        self.listen(3)

    def handle_accept(self):
        (sock,addr) = self.accept()
        stdin = sock.makefile("rb")
        stdout = sock.makefile("wb")
        threading.Thread(target=serve_forever, args=(stdin,stdout)).start()

    def handle_close(self):
        self.close()


def run(port = DEFAULT_PORT):
    """
    Start a socket server, which creates Pushy connections as client
    connections come in.

    @param port: The port number to listen on.
    """

    server = pushy_server(port)
    while True:
        asyncore.loop()

if __name__ == "__main__":
    run()

