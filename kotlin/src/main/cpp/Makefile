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

DEPS=$(O_FILES) \
	$(BUILD_DIR)/librive.a \
	$(BUILD_DIR)/libskia.a \
	$(BUILD_DIR)/librive_skia_renderer.a \

LIBS=-llog -landroid -lEGL -lGLESv3

# Build the .so object.
jni_bridge: $(OBJ_FILES)
	$(CXX) -shared -o $(BUILD_DIR)/libjnirivebridge.so \
	--target=$(TARGET_ARCH)  \
	--sysroot=$(SYSROOT) \
	$(CXXFLAGS) \
	-static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Qunused-arguments \
	$(DEPS) \
	$(LIBS) -latomic -lm 

# BUILD all .o files - i.e. the files specified in OBJ_FILES.
# Output all the .o files in ODIR
$(ODIR)/%.o: $(SRC_DIR)/%.cpp
	$(CXX) -c $< -o $(ODIR)/$(notdir $@) \
		-DSK_GL \
		-Iinclude \
		-I$(RIVE_RUNTIME_DIR)/include \
		-I$(RIVE_RUNTIME_DIR)/renderer/library/include \
		-I${RIVE_RUNTIME_DIR}/skia/dependencies/$(SKIA_DIR_NAME)/ \
		-I${RIVE_RUNTIME_DIR}/skia/dependencies/$(SKIA_DIR_NAME)/include/core \
		-I${RIVE_RUNTIME_DIR}/skia/dependencies/$(SKIA_DIR_NAME)/include/effects \
		-I${RIVE_RUNTIME_DIR}/skia/dependencies/$(SKIA_DIR_NAME)/include/gpu \
		-I${RIVE_RUNTIME_DIR}/skia/dependencies/$(SKIA_DIR_NAME)/include/config \
		-I${RIVE_RUNTIME_DIR}/skia/renderer/include \
		$(CXXFLAGS)

clean:
	rm -rf $(ODIR)
	rm -f $(BUILD_DIR)/libjnirivebridge.so
	rm -f $(BUILD_DIR)/librive.a
	rm -f $(BUILD_DIR)/libskia.a
	rm -f $(BUILD_DIR)/librive_skia_renderer.a
	rm -f $(BUILD_DIR)/libc++_static.a
	