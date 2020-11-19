#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/render_paint.hpp"
#include "models/render_path.hpp"

// From 'rive-cpp'
#include "file.hpp"
#include "layout.hpp"
//

// lets make sure we stich our rive android renderers into the rive namespace
namespace rive
{
	RenderPaint *makeRenderPaint() { return new rive_android::JNIRenderPaint(); }
	RenderPath *makeRenderPath() { return new rive_android::JNIRenderPath(); }
} // namespace rive

namespace rive_android
{
	JNIEnv *globalJNIEnv;
	jobject globalJNIObj;
	jobject androidCanvas;
	int sdkVersion;

	void setSDKVersion()
	{
		char sdk_ver_str[255];
		__system_property_get("ro.build.version.sdk", sdk_ver_str);
		sdkVersion = atoi(sdk_ver_str);
	}

	rive::Fit getFit(JNIEnv *env, jobject jfit)
	{
		jstring fitValue = (jstring)env->CallObjectMethod(jfit, rive_android::fitNameMethodId);
		const char *fitValueNative = env->GetStringUTFChars(fitValue, 0);

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

	rive::Alignment getAlignment(JNIEnv *env, jobject jalignment)
	{
		jstring alignmentValue = (jstring)env->CallObjectMethod(jalignment, rive_android::alignmentNameMethodId);
		const char *alignmentValueNative = env->GetStringUTFChars(alignmentValue, 0);

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

	long import(uint8_t *bytes, jint length)
	{

		auto reader = rive::BinaryReader(bytes, length);
		rive::File *file = nullptr;
		auto result = rive::File::import(reader, &file);

		if (result == rive::ImportResult::success)
		{
			return (long)file;
		}
		else if (result == rive::ImportResult::unsupportedVersion)
		{
			__android_log_print(ANDROID_LOG_INFO, __FILE__, "unsupported version");
		}
		else if (result == rive::ImportResult::malformed)
		{
			__android_log_print(ANDROID_LOG_INFO, __FILE__, "malformed");
		}
		return (long)-1;
	}

} // namespace rive_android