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
#include <iostream>
#include <windows.h>
#include <stdio.h>
#include <stdarg.h>

#define SERVICE_NAME TEXT("Pushy")

namespace {
  bool                  shutdown = false;
  SERVICE_STATUS        service_status;
  SERVICE_STATUS_HANDLE status_handle;
}

void ReportSvcStatus(DWORD currentState, DWORD win32ExitCode, DWORD waitHint)
{
    static DWORD checkPoint = 1;

    // Fill in the SERVICE_STATUS structure.
    service_status.dwCurrentState = currentState;
    service_status.dwWin32ExitCode = win32ExitCode;
    service_status.dwWaitHint = waitHint;

    if (currentState == SERVICE_START_PENDING)
        service_status.dwControlsAccepted = 0;
    else
        service_status.dwControlsAccepted = SERVICE_ACCEPT_STOP;

    if (currentState == SERVICE_RUNNING || currentState == SERVICE_STOPPED)
        service_status.dwCheckPoint = 0;
    else
        service_status.dwCheckPoint = checkPoint++;

    // Report the status of the service to the SCM.
    SetServiceStatus(status_handle, &service_status);
}

/**
 * Not re-entrant - don't use in threaded code.
 */
const char* format_error(DWORD error)
{
    static TCHAR buffer[65535];
    DWORD nchars =
        FormatMessage(
            FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL, error, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
            buffer, sizeof(buffer), NULL);
    if (nchars == 0)
        return "<FormatMessage failed>";
    return buffer; 
}

VOID WINAPI control_handler(DWORD code)
{
    // Handle the requested control code. 
    switch(code) 
    {  
        case SERVICE_CONTROL_STOP:
        {
            ReportSvcStatus(SERVICE_STOP_PENDING, NO_ERROR, 0);
            shutdown = true;
            return;
        }
        default: break;
   }
   ReportSvcStatus(service_status.dwCurrentState, NO_ERROR, 0);
}

VOID service_init(DWORD argc, LPTSTR *argv)
{
    // Report running status when initialization is complete.
    ReportSvcStatus(SERVICE_RUNNING, NO_ERROR, 0);
    
    while (!shutdown)
    {
        try
        {
            pushyd_once(NULL);
        }
        catch (...)
        {
            break;
        }
    }
    ReportSvcStatus(SERVICE_STOPPED, NO_ERROR, 0);
}

VOID WINAPI service_main(DWORD argc, LPTSTR *argv)
{
    status_handle = RegisterServiceCtrlHandler(SERVICE_NAME, control_handler);
    if (!status_handle)
        return;

    // Initialise status with common attributes.
    service_status.dwServiceType = SERVICE_WIN32_OWN_PROCESS; 
    service_status.dwServiceSpecificExitCode = 0;

    // Report initial status to the SCM.
    ReportSvcStatus(SERVICE_START_PENDING, NO_ERROR, 3000);

    // Perform service-specific initialization and work.
    service_init(argc, argv);
}

void start_service()
{
    SERVICE_TABLE_ENTRY DispatchTable[] =
    {
        {SERVICE_NAME, (LPSERVICE_MAIN_FUNCTION)service_main},
        {NULL, NULL}
    };
    StartServiceCtrlDispatcher(DispatchTable);
}

/**
 * Install as a service.
 */
int service_install()
{
    SC_HANDLE handleManager;
    SC_HANDLE handleService;
    TCHAR service_path[MAX_PATH];

    // Get the path to the executable.
    if (!GetModuleFileName(NULL, service_path, MAX_PATH))
    {
        std::cerr << "Cannot get path to executable: "
                  << format_error(GetLastError()) << std::endl;
        return 1;
    }

    // Get a handle to the SCM database. 
    handleManager = OpenSCManager(NULL, NULL, SC_MANAGER_ALL_ACCESS);
    if (handleManager == NULL)
    {
        std::cerr << "Cannot open service control manager: "
                  << format_error(GetLastError()) << std::endl;
        return 2;
    }

    // Create the service.
    handleService = CreateService(handleManager, SERVICE_NAME, SERVICE_NAME,
                                  SERVICE_ALL_ACCESS, SERVICE_WIN32_OWN_PROCESS,
                                  SERVICE_AUTO_START, SERVICE_ERROR_NORMAL,
                                  service_path, NULL, NULL, NULL, NULL, NULL);
    if (handleService == NULL) 
    {
        std::cerr << "Cannot create service: "
                  << format_error(GetLastError()) << std::endl;
        CloseServiceHandle(handleManager);
        return 3;
    }

    std::cerr << "Service installed successfully" << std::endl;
    CloseServiceHandle(handleService); 
    CloseServiceHandle(handleManager);
    return 0;
}

int main(int argc, char* argv[])
{
    if (argc > 1)
    {
        if (strcmp(argv[1], "-shell") == 0)
        {
            while (!shutdown)
            {
                try
                {
                    pushyd_once(&std::cout);
                }
                catch (std::exception const& e)
                {
                    std::cerr << "std::exception caught: " << e.what()
                              << std::endl;
                    return 1;
                }
                catch (...)
                {
                    std::cerr << "Unknown exception caught" << std::endl;
                    return 1;
                }
            }
        }
        else if (strcmp(argv[1], "-install") == 0)
        {
            return service_install();
        }
    }
    else
    {
        start_service();
    }
    return 0;
}

