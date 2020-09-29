# All variables that aren't defined up here, are exported by the script calling
# this Makefile, that is the `build.rive.for.sh` shell script. 
# 
# Calling this Makefile by itself, won't work.
#
SRC_DIR=./src
ODIR=$(BUILD_DIR)/obj

# Get all cpp source files from the source files directory
SRC_FILES := $(wildcard $(SRC_DIR)/*.cpp) $(wildcard $(SRC_DIR)/*/*.cpp)
# $(info $(SRC_FILES))

# Generate all the object files comprising by replacing 
OBJ_FILES := $(patsubst $(SRC_DIR)/%.cpp,$(ODIR)/%.o,$(SRC_FILES))
# $(info $(OBJ_FILES))

# Remove all the directories from source files and obtain corresponding .o filenames
O_FILES = $(patsubst %.cpp,$(ODIR)/%.o,$(notdir $(SRC_FILES)))
# $(info $(O_FILES))

DEPS=$(O_FILES) $(BUILD_DIR)/librive.a
LDIR=-L$(SYSROOT)/usr/lib
LIBS=-llog -landroid

jni_bridge: $(OBJ_FILES)
	$(CXX) -shared $(DEPS) -o $(BUILD_DIR)/libjnirivebridge.so --sysroot=$(SYSROOT) -I$(RIVE_CPP_ROOT)/include -I$(INCLUDE) -I$(INCLUDE_CXX) $(LDIR) $(LIBS) $(CXXFLAGS)

# BUILD all .o files - i.e. the files specified in OBJ_FILES.
# Output all the .o files in ODIR
$(ODIR)/%.o: $(SRC_DIR)/%.cpp
	$(CXX) -c $< -o $(ODIR)/$(notdir $@) -I$(LIBRIVE)/include $(CXXFLAGS)

clean:
	rm -f $(ODIR)/*.o
	rm -f $(BUILD_DIR)/libjnirivebridge.so
	rm -f $(BUILD_DIR)/librive.a