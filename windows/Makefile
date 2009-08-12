CXX=cl
CXXFLAGS=/nologo /EHac /MT /WX /W3 /Z7
LINKFLAGS=/nologo /debug /fixed:no

# Define objects.
OBJS=main.obj pushyd.obj subprocess.obj handle.obj

# Define libraries to link against.
LIBS=kernel32.lib advapi32.lib

all:	pushysvc.exe

pushysvc.exe:	$(OBJS)
		link $(LINKFLAGS) /out:$@ $(LIBS) $**

.cpp.obj:
		$(CXX) $(CXXFLAGS) /Fd$@ /c $<

.PHONY:	clean
clean:
	del /f *.obj *.exe *.pdb *.ilk

