# What?

Pushy is an RPC (Remote Procedure Call) package for Python and Java. The key feature of Pushy is that it only requires Python and an SSH daemon on the "server". You don't need to install anything on the remote machine, which makes Pushy useful for controlling remote systems without installing custom software on them.

The core of Pushy is written in and for Python, and a Java API is provided that sits on top of this. The Java API allows developers to write Java code that interacts local or remote Python (plain old CPython, not Jython) interpreters, providing Java abstractions around Python modules, that will be more familiar to Java developers.

# How?

First, install Pushy by one of several methods:

* Using [pip](http://www.pip-installer.org): `pip install pushy`.
* Grab the source archive from [the PyPI page](http://pypi.python.org/pypi/pushy/), and run the usual: `python setup.py install`
* Grab the jar file for Java and add it to your classpath. It's self-contained (Java and Python together.)

Now you're ready to create a Pushy connection. In the example below, we create a connection to a remote machine and list the directories in the home directory of the user 'me'. In accessing an attribute of a connection's "modules", you are effectively "importing" the remote module of that name. A proxy to that module is returned, and from then on you can (more or less) treat the module as if it were locally defined.

```python
import pushy
    
with pushy.connect("ssh:remotehost", username="me", password="password") as conn:
    for (root,dirs,files) in conn.modules.os.walk("."):
        for d in dirs:
            print conn.modules.os.path.join(root, d)
        for f in files:
            print conn.modules.os.path.join(root, f)
```

# Documentation

Further documentation can be found at [http://packages.python.org/pushy/](http://packages.python.org/pushy/).