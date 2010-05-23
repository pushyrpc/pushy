/*
 * Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif

#include "pushyd.h"
#include "subprocess.hpp"

#include <limits.h>
#include <stdio.h>
#include <windows.h>
#include <stdexcept>
#include <iostream>
#include <string>
#include <vector>

// Manages a named pipe handle.
class NamedPipe
{
public:
    NamedPipe(const char *path) : m_Handle(INVALID_HANDLE_VALUE)
    {
        m_Handle = CreateNamedPipe(
                       path,
                       PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
                       PIPE_TYPE_BYTE | PIPE_WAIT,
                       PIPE_UNLIMITED_INSTANCES,
                       USHRT_MAX,
                       USHRT_MAX,
                       1000,
                       0);
        if (m_Handle == INVALID_HANDLE_VALUE)
        {
            std::cout << GetLastError() << std::endl;
            throw std::runtime_error("Failed to create named pipe");
        }
    }

    ~NamedPipe()
    {
        if (m_Handle != INVALID_HANDLE_VALUE)
            CloseHandle(m_Handle);
    }

    operator HANDLE& ()
    {
        return m_Handle;
    }

private:
    NamedPipe(NamedPipe const&);
    NamedPipe& operator=(NamedPipe const&);

    HANDLE m_Handle;
};

struct TimeoutException : std::exception
{
    TimeoutException() : std::exception() {}
};

// Manages a named pipe connection (Connect/DisconnectNamedPipe)
class NamedPipeConnection
{
public:
    NamedPipeConnection(NamedPipe &pipe, DWORD timeout = 1000)
      : m_Pipe(pipe), m_Overlapped()
    {
        m_Overlapped.hEvent = CreateEvent(NULL, TRUE, FALSE, NULL);
        if (m_Overlapped.hEvent == NULL)
            throw std::runtime_error("Failed to create event");

        if (!ConnectNamedPipe(m_Pipe, &m_Overlapped))
        {
            DWORD error = GetLastError();
            if (error == ERROR_IO_PENDING)
            {
                DWORD result =
                    WaitForSingleObject(m_Overlapped.hEvent, timeout);
                if (result == WAIT_TIMEOUT)
                {
                    CloseHandle(m_Overlapped.hEvent);
                    throw TimeoutException();
                }
                else if (result != WAIT_OBJECT_0)
                    error = GetLastError();
                else
                    error = ERROR_PIPE_CONNECTED;
            }
            if (error != ERROR_PIPE_CONNECTED)
            {
                CloseHandle(m_Overlapped.hEvent);
                throw std::runtime_error("Failed to connect named pipe");
            }
        }
    }

    ~NamedPipeConnection()
    {
        CloseHandle(m_Overlapped.hEvent);
        FlushFileBuffers(m_Pipe);
        DisconnectNamedPipe(m_Pipe);
    }

    void read(void *buffer, DWORD nbytes)
    {
        DWORD total_nread = 0;
        DWORD nread = 0;
        for (; total_nread < nbytes; total_nread += nread)
        {
            if (!ReadFile(m_Pipe, (char*)buffer+total_nread,
                          nbytes-total_nread, &nread, 0))
            {
                throw std::runtime_error("ReadFile failed");
            }
        }
    }

private:
    NamedPipeConnection(NamedPipeConnection const&);
    NamedPipeConnection& operator=(NamedPipeConnection const&);

    NamedPipe &m_Pipe;
    OVERLAPPED m_Overlapped;
};

// Manages named pipe client impersonation
class ScopedImpersonation
{
public:
    ScopedImpersonation(NamedPipe &pipe) : m_Pipe(pipe)
    {
        if (!ImpersonateNamedPipeClient(pipe))
            throw std::runtime_error(
                      "Failed to impersonate named pipe client");
    }

    ~ScopedImpersonation()
    {
        RevertToSelf();
    }

    std::string getClientUsername()
    {
        char buffer[1024];
        if (GetNamedPipeHandleState(
                m_Pipe, 0, 0, 0, 0, buffer, sizeof(buffer)))
        {
            return std::string(buffer);
        }
        throw std::runtime_error("GetNamedPipeHandleState failed");
    }

private:
    ScopedImpersonation(ScopedImpersonation const&);
    ScopedImpersonation& operator=(ScopedImpersonation const&);

    NamedPipe &m_Pipe;
};

void readArguments(NamedPipeConnection &conn,
                   std::vector<std::string> &args)
{
    unsigned char nargs = 0;
    conn.read(&nargs, sizeof(nargs));

    while (nargs--)
    {
        unsigned char uint_bytes[4];
        conn.read(uint_bytes, sizeof(uint_bytes));

        unsigned int arglen =
            ((unsigned int)uint_bytes[0]) << 24 |
            ((unsigned int)uint_bytes[1]) << 16 |
            ((unsigned int)uint_bytes[2]) << 8 |
            ((unsigned int)uint_bytes[3]);

        char *buffer = new char[arglen];
        try
        {
            conn.read(buffer, arglen);
            args.push_back(std::string(buffer, arglen));
            delete [] buffer;
        }
        catch (...)
        {
            delete [] buffer;
            throw;
        }
    }
}

int pushyd_once(std::ostream *const log)
{
    if (log) *log << "Creating pipe" << std::endl;
    NamedPipe pipe("\\\\.\\pipe\\pushy");
    if (log) *log << "Created pipe" << std::endl;

    try
    {
        // Time out for pipe is 5 seconds.
        if (log) *log << "Waiting for connection" << std::endl;
        NamedPipeConnection conn(pipe, 5000);
        if (log) *log << "Received connection" << std::endl;

        // Read arguments
        std::vector<std::string> arguments;
        if (log) *log << "Waiting for arguments" << std::endl;
        readArguments(conn, arguments);
        if (log)
        {
            *log << "Received " << arguments.size()
                 << " arguments" << std::endl;
            for (std::vector<std::string>::const_iterator
                     iter = arguments.begin(); iter != arguments.end(); ++iter)
            {
                *log << "  " << *iter << std::endl;
            }
        }

        // Impersonate pipe client.
        ScopedImpersonation impersonation(pipe);

        try
        {
            if (log) *log << "Executing subprocess" << std::endl;
            int result = ExecuteSubprocess(arguments, pipe);
            if (log) *log << "Subprocess returned " << result << std::endl;
            return result;
        }
        catch (std::exception const& e)
        {
            if (log)
            {
                *log << "std::exception occurred: " << e.what()
                     << std::endl;
            }
            throw;
        }
        catch (...)
        {
            if (log) *log << "Unknown exception occurred" << std::endl;
            throw;
        }
    }
    catch (TimeoutException const&)
    {
        return -1;
    }
    catch (...)
    {
        return -2;
    }
}

struct PushydState
{
    PushydState()
      : shutdown(false), stop_event(CreateEvent(NULL, TRUE, FALSE, NULL)),
        thread()
    {
        if (stop_event == NULL)
            throw std::runtime_error("CreateEvent failed");
    }

    ~PushydState()
    {
        if (thread)
            CloseHandle(thread);
        CloseHandle(stop_event);
    }

    bool   shutdown;
    HANDLE stop_event;
    HANDLE thread;

private:
    PushydState(PushydState const&);
    PushydState& operator=(PushydState const&);
};

DWORD WINAPI pushyd_thread(LPVOID param)
{
    PushydState *state = (PushydState*)param;
    DWORD error = 0;

    while (!state->shutdown)
    {
        try
        {
            pushyd_once(NULL);
        }
        catch (...)
        {
            error = -1;
            break;
        }
    }

    SetEvent(state->stop_event);
    return error;
}

// Executes pushyd in a thread
void* pushyd_start()
{
    PushydState *state = new PushydState;
    try
    {
        state->thread =
            CreateThread(NULL, 0, &pushyd_thread, state, 0, NULL);
        if (!state->thread)
            throw std::runtime_error("Failed to create thread");
        return state;
    }
    catch (std::exception const&)
    {
        //std::cerr << "std::exception: " << e.what()
        //          << " (" << GetLastError() << ")" << std::endl;
        delete state;
        return NULL;
    }
    catch (...)
    {
        //std::cerr << "Unknown exception: "
        //          << "(" << GetLastError() << ")" << std::endl;
        delete state;
        return NULL;
    }
}

int pushyd_stop(void *handle)
{
    PushydState *state = (PushydState*)handle;
    state->shutdown = true;
    int status = 0;
    if (WaitForSingleObject(state->stop_event, INFINITE) != WAIT_OBJECT_0)
        status = -1;
    delete state;
    return status;
}

