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

#include "handle.hpp"
#include "subprocess.hpp"
#include <stdexcept>
#include <windows.h>
#include <iostream>
#include <sstream>

bool contains(std::string const& s, char c)
{
    return s.find(c) != std::string::npos;
}

// Algorithm taken from Python's subprocess module.
std::string list2cmdline(std::vector<std::string> const& args)
{
    std::string result;
    bool needquote = false;

    for (std::vector<std::string>::const_iterator iter = args.begin();
         iter != args.end(); ++iter)
    {
        std::string const& arg = *iter;
        std::string bs_buf;

        if (!result.empty())
            result += ' ';

        needquote = (arg == "") ||
                    (contains(arg, ' ') || contains(arg, '\t'));
        if (needquote)
            result += '"';

        for (unsigned int i = 0; i < arg.size(); ++i)
        {
            char c = arg[i];

            if (c == '\\')
            {
                bs_buf += c;
            }
            else if (c == '"')
            {
                result.append(std::string(bs_buf.size() * 2, '\\'));
                bs_buf.clear();
                result.append("\\\"");
            }
            else
            {
                if (!bs_buf.empty())
                {
                    result.append(bs_buf);
                    bs_buf.clear();
                }
                result += c;
            }
        }

        if (!bs_buf.empty())
            result.append(bs_buf);

        if (needquote)
        {
            result.append(bs_buf);
            result += '"';
        }
    }

    return result;
}

///////////////////////////////////////////////////////////////////////////////
// Create a named pipe with the name "\\.\pipe\pushy.<pid>.<tid>", whose read
// handle is created with FILE_FLAG_OVERLAPPED.

BOOL CreateOverlappedPipe(PHANDLE hReadPipe,
                          PHANDLE hWritePipe,
                          LPSECURITY_ATTRIBUTES lpPipeAttributes,
                          DWORD nSize)
{
    // Generate a unique pipe name. Since we'll only ever have one of these
    // per thread, just use the process ID and thread ID.
    std::stringstream ss;
    ss << "\\\\.\\pipe\\pushy."
       << GetCurrentProcessId() << "." << GetCurrentThreadId();
    std::string pipeName = ss.str();

    if (nSize == 0)
        nSize = 4096;

    // Create the reading pipe, with the overlapped I/O flag set.
    HANDLE readPipe =
        CreateNamedPipe(
            pipeName.c_str(),
            PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED,
            PIPE_TYPE_BYTE | PIPE_WAIT,
            1, nSize, nSize, 0, lpPipeAttributes);
    if (readPipe == INVALID_HANDLE_VALUE)
        return FALSE;

    // Create a pipe client for writing.
    HANDLE writePipe = CreateFile(pipeName.c_str(), GENERIC_WRITE, 0,
                                  lpPipeAttributes, OPEN_EXISTING,
                                  FILE_FLAG_WRITE_THROUGH, NULL);
    if (writePipe == INVALID_HANDLE_VALUE)
    {
        CloseHandle(readPipe);
        return FALSE;
    }

    *hReadPipe = readPipe;
    *hWritePipe = writePipe;
    return TRUE;
}

///////////////////////////////////////////////////////////////////////////////
// ReadFromPipe reads from a pipe using overlapped I/O pipe.

void ReadFromPipe(HANDLE pipe, HANDLE file)
{
    char buf[4096];

    // Create an auto-close Handle for the file, so the child process' stdin
    // is closed when the pipe is closed.
    Handle stdin_(file);

    // Create a manual reset event.
    Handle event(CreateEvent(NULL, TRUE, FALSE, NULL));

    // While the file is open, write the buffer to the pipe using
    // overlapped I/O.
    for (;;)
    {
        OVERLAPPED overlapped;
        memset(&overlapped, 0, sizeof(overlapped));

        overlapped.hEvent = event;

        DWORD nread = 0;
        if (!ReadFile(pipe, buf, sizeof(buf), &nread, &overlapped))
        {
            DWORD error = GetLastError();
            if (error != ERROR_IO_PENDING)
                throw std::runtime_error("ReadFile failed");
            while (!GetOverlappedResult(pipe, &overlapped, &nread, 0))
            {
                if (GetLastError() != ERROR_IO_INCOMPLETE)
                    throw std::runtime_error("GetOverlappedResult failed");
                Sleep(1);
            }
        }

        // Write what was read from the pipe to the file.
        DWORD total_written = 0;
        while (total_written < nread)
        {
            DWORD written = 0;
            if (!WriteFile(file, buf+total_written, nread-total_written,
                           &written, 0))
                throw std::runtime_error("WriteFile failed");
            if (!FlushFileBuffers(file))
                throw std::runtime_error("FlushFileBuffers failed");

            total_written += written;
        }
    }
}

DWORD WINAPI ReadFromPipeThread(LPVOID args)
{
    HANDLE *handles = (HANDLE*)args;
    try
    {
        ReadFromPipe(handles[0], handles[1]);
        return 0;
    }
    catch (...)
    {
        return 1;
    }
}

///////////////////////////////////////////////////////////////////////////////
// ReadIntoPipe writes to a pipe using overlapped I/O pipe.

void ReadIntoPipe(HANDLE file, HANDLE pipe)
{
    char buf[4096];

    // Create a manual reset event.
    Handle event(CreateEvent(NULL, TRUE, FALSE, NULL));

    // While the file is open, write the buffer to the pipe using
    // overlapped I/O.
    for (;;)
    {
        OVERLAPPED overlapped;
        memset(&overlapped, 0, sizeof(overlapped));
        overlapped.hEvent = event;

        DWORD nread = 0;
        if (!ReadFile(file, buf, sizeof(buf), &nread, &overlapped))
        {
            DWORD error = GetLastError();
            if (error != ERROR_IO_PENDING)
                throw std::runtime_error("ReadFile failed");
            while (!GetOverlappedResult(file, &overlapped, &nread, 0))
            {
                if (GetLastError() != ERROR_IO_INCOMPLETE)
                    throw std::runtime_error("GetOverlappedResult failed");
                Sleep(1);
            }
        }

        // Write the data to the remote pipe.
        DWORD total_written = 0;
        while (total_written < nread)
        {
            memset(&overlapped, 0, sizeof(overlapped));
            overlapped.hEvent = event;

            DWORD written = 0;
            if (!WriteFile(pipe, buf+total_written, nread-total_written,
                           &written, &overlapped))
            {
                DWORD error = GetLastError();
                if (error != ERROR_IO_PENDING)
                    throw std::runtime_error("WriteFile failed");
                while (!GetOverlappedResult(pipe, &overlapped, &written, 0))
                {
                    if (GetLastError() != ERROR_IO_INCOMPLETE)
                        throw std::runtime_error("GetOverlappedResult failed");
                    Sleep(1);
                }
            }

            if (!FlushFileBuffers(pipe))
                throw std::runtime_error("FlushFileBuffers failed");

            total_written += written;
        }
    }
}

///////////////////////////////////////////////////////////////////////////////

HANDLE MakeInheritable(HANDLE handle)
{
    HANDLE duplicate;
    if (!DuplicateHandle(GetCurrentProcess(), handle,
                         GetCurrentProcess(), &duplicate, 0,
                         TRUE, DUPLICATE_SAME_ACCESS))
        throw std::runtime_error("Failed to duplicate handle");
    return duplicate;
}

DWORD ExecuteSubprocess(std::vector<std::string> const& args, HANDLE pipe)
{
    SECURITY_ATTRIBUTES sa;
    STARTUPINFO         sinfo;
    PROCESS_INFORMATION pinfo;

    if (args.empty())
        throw std::runtime_error("Empty argument list supplied");

    memset(&sa, 0, sizeof(sa));
    memset(&sinfo, 0, sizeof(sinfo));
    memset(&pinfo, 0, sizeof(pinfo));

    // Create a pipe for writing to the child process.
    HANDLE p2cread_  = INVALID_HANDLE_VALUE;
    HANDLE p2cwrite_ = INVALID_HANDLE_VALUE;
    if (!CreatePipe(&p2cread_, &p2cwrite_, NULL, 0))
        throw std::runtime_error("Failed to create p2c pipe");
    Handle p2cread(p2cread_);
    Handle p2cwrite(p2cwrite_);

    // Create a pipe for reading from the child process. We want to do
    // overlapped I/O reads from the child pipe, so we can't use CreatePipe.
    // We'll create a FILE_FLAG_OVERLAPPED named pipe with a unique name.
    HANDLE c2pread_  = INVALID_HANDLE_VALUE;
    HANDLE c2pwrite_ = INVALID_HANDLE_VALUE;
    if (!CreateOverlappedPipe(&c2pread_, &c2pwrite_, NULL, 0))
        throw std::runtime_error("Failed to create c2p pipe");
    Handle c2pread(c2pread_);
    Handle c2pwrite(c2pwrite_);

    // Create a "null" file for setting the stderr handle to.
    Handle nulfile(CreateFile("nul", GENERIC_WRITE, FILE_SHARE_WRITE, 0,
                              OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0));

    // Create inheritable versions of the files for stdin/stdout/stderr.
    Handle hStdInput(MakeInheritable(p2cread));
    Handle hStdOutput(MakeInheritable(c2pwrite));
    Handle hStdError(MakeInheritable(nulfile));

    // Set up the parameters for process creation.
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;
    sinfo.cb = sizeof(STARTUPINFO);
    GetStartupInfo(&sinfo);
    sinfo.dwFlags    = STARTF_USESTDHANDLES;
    sinfo.hStdInput  = hStdInput;
    sinfo.hStdOutput = hStdOutput;
    sinfo.hStdError  = hStdError;

    // Generate command line, start up process.
    std::string cmd = list2cmdline(args);
    char *cmd_cstr = _strdup(cmd.c_str());

    try
    {
        if (!CreateProcess(NULL,             // Image file name
                           cmd_cstr,         // Command line
                           NULL,             // process security
                           NULL,             // thread security
                           TRUE,             // Inherit all handles
                           CREATE_NO_WINDOW, // Creation attributes
                           NULL,             // env
                           NULL,             // cwd
                           &sinfo,           // startup info
                           &pinfo))
            throw std::runtime_error("CreateProcess failed");
    }
    catch (...)
    {
        free(cmd_cstr);
        throw;
    }

    // Close the handles not used by the parent process.
    p2cread.close();
    c2pwrite.close();
    nulfile.close();
    hStdInput.close();
    hStdOutput.close();
    hStdError.close();

    // Create Handle objects for the process and thread handles.
    Handle hProcess(pinfo.hProcess);
    Handle hThread(pinfo.hThread);

    try
    {
        // Create a thread for reading from the pipe into the process' stdin.
        HANDLE handles[] = {pipe, p2cwrite};
        Handle thread(CreateThread(
                   NULL, 0, ReadFromPipeThread, handles, 0, 0));

        // Read from the child process' stdout into the pipe.
        ReadIntoPipe(c2pread, pipe);

        // Wait for process to finish
        DWORD result;
        if (WaitForSingleObject(pinfo.hProcess, INFINITE) == WAIT_FAILED)
            throw std::runtime_error("WaitForSingleObject failed");
        if (!GetExitCodeProcess(pinfo.hProcess, &result))
            throw std::runtime_error("GetExitCodeProcess failed");
        free(cmd_cstr);
        return result;
    }
    catch (...)
    {
        free(cmd_cstr);
        throw;
    }
}

