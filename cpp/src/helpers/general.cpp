#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "skia_factory.hpp"
#include "rive/file.hpp"
#include "rive/layout.hpp"

#ifdef DEBUG
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <EGL/egl.h>
#endif

static rive::SkiaFactory gFactory;

// luigi: murdered this due to our single renderer model right now...all canvas
// rendering won't work in this branch lets make sure we stich our rive android
// renderers into the rive namespace namespace rive
// {
// 	RenderPaint *makeRenderPaint() { return new rive_android::JNIRenderPaint();
// } 	RenderPath *makeRenderPath() { return new rive_android::JNIRenderPath();
// } } // namespace rive

namespace rive_android
{
	JavaVM* globalJavaVM;
	jobject androidCanvas;
	int sdkVersion;

	JNIEnv* getJNIEnv()
	{
		// double check it's all ok
		JNIEnv* g_env;
		int getEnvStat = globalJavaVM->GetEnv((void**)&g_env, JNI_VERSION_1_6);
		if (getEnvStat == JNI_EDETACHED)
		{
			// std::cout << "GetEnv: not attached" << std::endl;
			if (globalJavaVM->AttachCurrentThread((JNIEnv**)&g_env, NULL) != 0)
			{
				// std::cout << "Failed to attach" << std::endl;
			}
		}
		else if (getEnvStat == JNI_OK)
		{
			//
		}
		else if (getEnvStat == JNI_EVERSION)
		{
			// std::cout << "GetEnv: version not supported" << std::endl;
		}
		return g_env;
	}
	void detachThread()
	{
		if (!(globalJavaVM->DetachCurrentThread() == JNI_OK))
		{
			LOGE("Could not detach thread from JVM");
		}
	}

	void logReferenceTables()
	{
		jclass vm_class = getJNIEnv()->FindClass("dalvik/system/VMDebug");
		jmethodID dump_mid = getJNIEnv()->GetStaticMethodID(
		    vm_class, "dumpReferenceTables", "()V");
		getJNIEnv()->CallStaticVoidMethod(vm_class, dump_mid);
	}

	void setSDKVersion()
	{
		char sdk_ver_str[255];
		__system_property_get("ro.build.version.sdk", sdk_ver_str);
		sdkVersion = atoi(sdk_ver_str);
	}

	rive::Fit getFit(JNIEnv* env, jobject jfit)
	{
		jstring fitValue = (jstring)env->CallObjectMethod(
		    jfit, rive_android::getFitNameMethodId());
		const char* fitValueNative = env->GetStringUTFChars(fitValue, 0);
		env->DeleteLocalRef(fitValue);

		rive::Fit fit = rive::Fit::none;
		if (strcmp(fitValueNative, "FILL") == 0)
		{
			fit = rive::Fit::fill;
		}
		else if (strcmp(fitValueNative, "CONTAIN") == 0)
		{
			fit = rive::Fit::contain;
		}
		else if (strcmp(fitValueNative, "COVER") == 0)
		{
			fit = rive::Fit::cover;
		}
		else if (strcmp(fitValueNative, "FIT_WIDTH") == 0)
		{
			fit = rive::Fit::fitWidth;
		}
		else if (strcmp(fitValueNative, "FIT_HEIGHT") == 0)
		{
			fit = rive::Fit::fitHeight;
		}
		else if (strcmp(fitValueNative, "NONE") == 0)
		{
			fit = rive::Fit::none;
		}
		else if (strcmp(fitValueNative, "SCALE_DOWN") == 0)
		{
			fit = rive::Fit::scaleDown;
		}
		return fit;
	}

	rive::Alignment getAlignment(JNIEnv* env, jobject jalignment)
	{
		jstring alignmentValue = (jstring)env->CallObjectMethod(
		    jalignment, rive_android::getAlignmentNameMethodId());
		const char* alignmentValueNative =
		    env->GetStringUTFChars(alignmentValue, 0);
		env->DeleteLocalRef(alignmentValue);

		rive::Alignment alignment = rive::Alignment::center;
		if (strcmp(alignmentValueNative, "TOP_LEFT") == 0)
		{
			alignment = rive::Alignment::topLeft;
		}
		else if (strcmp(alignmentValueNative, "TOP_CENTER") == 0)
		{
			alignment = rive::Alignment::topCenter;
		}
		else if (strcmp(alignmentValueNative, "TOP_RIGHT") == 0)
		{
			alignment = rive::Alignment::topRight;
		}
		else if (strcmp(alignmentValueNative, "CENTER_LEFT") == 0)
		{
			alignment = rive::Alignment::centerLeft;
		}
		else if (strcmp(alignmentValueNative, "CENTER") == 0)
		{
			alignment = rive::Alignment::center;
		}
		else if (strcmp(alignmentValueNative, "CENTER_RIGHT") == 0)
		{
			alignment = rive::Alignment::centerRight;
		}
		else if (strcmp(alignmentValueNative, "BOTTOM_LEFT") == 0)
		{
			alignment = rive::Alignment::bottomLeft;
		}
		else if (strcmp(alignmentValueNative, "BOTTOM_CENTER") == 0)
		{
			alignment = rive::Alignment::bottomCenter;
		}
		else if (strcmp(alignmentValueNative, "BOTTOM_RIGHT") == 0)
		{
			alignment = rive::Alignment::bottomRight;
		}
		return alignment;
	}

	long import(uint8_t* bytes, jint length)
	{
		rive::ImportResult result;
		auto file = rive::File::import(rive::Span<const uint8_t>(bytes, length),
		                               &gFactory,
		                               &result)
		                .release();
		if (result == rive::ImportResult::success)
		{
			return (long)file;
		}
		else if (result == rive::ImportResult::unsupportedVersion)
		{
			return throwUnsupportedRuntimeVersionException(
			    "Unsupported Rive File Version.");
		}
		else if (result == rive::ImportResult::malformed)
		{
			return throwMalformedFileException("Malformed Rive File.");
		}
		else
		{
			return throwRiveException("Unknown error loading file.");
		}
	}

	std::string jstring2string(JNIEnv* env, jstring jStr)
	{
		const char* cstr = env->GetStringUTFChars(jStr, NULL);
		std::string str = std::string(cstr);
		return str;
	}
#ifdef DEBUG
	void logThread()
	{
		int pipes[2];
		pipe(pipes);
		dup2(pipes[1], STDERR_FILENO);
		FILE* inputFile = fdopen(pipes[0], "r");
		char readBuffer[256];
		while (1)
		{
			fgets(readBuffer, sizeof(readBuffer), inputFile);
			__android_log_write(2, "stderr", readBuffer);
		}
	}

	void _check_egl_error(const char* file, int line)
	{
		EGLenum err(eglGetError());

		while (true)
		{
			std::string error;

			switch (err)
			{
				case EGL_SUCCESS:
					return;
				case EGL_NOT_INITIALIZED:
					error = "EGL_NOT_INITIALIZED";
					break;
				case EGL_BAD_ACCESS:
					error = "EGL_BAD_ACCESS";
					break;
				case EGL_BAD_ALLOC:
					error = "EGL_BAD_ALLOC";
					break;
				case EGL_BAD_ATTRIBUTE:
					error = "EGL_BAD_ATTRIBUTE";
					break;
				case EGL_BAD_CONTEXT:
					error = "EGL_BAD_CONTEXT";
					break;
				case EGL_BAD_CONFIG:
					error = "EGL_BAD_CONFIG";
					break;
				case EGL_BAD_CURRENT_SURFACE:
					error = "EGL_BAD_CURRENT_SURFACE";
					break;
				case EGL_BAD_DISPLAY:
					error = "EGL_BAD_DISPLAY";
					break;
				case EGL_BAD_SURFACE:
					error = "EGL_BAD_SURFACE";
					break;
				case EGL_BAD_MATCH:
					error = "EGL_BAD_MATCH";
					break;
				case EGL_BAD_PARAMETER:
					error = "EGL_BAD_PARAMETER";
					break;
				case EGL_BAD_NATIVE_PIXMAP:
					error = "EGL_BAD_NATIVE_PIXMAP";
					break;
				case EGL_BAD_NATIVE_WINDOW:
					error = "EGL_BAD_NATIVE_WINDOW";
					break;
				case EGL_CONTEXT_LOST:
					error = "EGL_CONTEXT_LOST";
					break;
				default:
					LOGE("(%d) %s - %s:%d", err, "Unknown", file, line);
					return;
			}
			LOGE("(%d) %s - %s:%d", err, error.c_str(), file, line);
			err = eglGetError();
		}
	}
#endif
} // namespace rive_android