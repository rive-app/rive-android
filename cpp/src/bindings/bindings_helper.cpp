#include "models/dimensions_helper.hpp"
#include "helpers/general.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
	using namespace rive_android;

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_core_Rive_cppCalculateRequiredBounds(
	    JNIEnv* env,
	    jobject thisObj,
	    jobject jfit,
	    jobject jalignment,
	    jlong availableBoundsRef,
	    jlong artboardBoundsRef,
	    jlong requiredBoundsRef)
	{
		auto fit = ::getFit(env, jfit);
		auto alignment = ::getAlignment(env, jalignment);
		auto availableBounds = (rive::AABB*)availableBoundsRef;
		auto artboardBounds = (rive::AABB*)artboardBoundsRef;
		auto requiredBounds = (rive::AABB*)requiredBoundsRef;

		DimensionsHelper helper;

		helper.computeDimensions(
		    fit, alignment, *availableBounds, *artboardBounds, *requiredBounds);
	}

#ifdef __cplusplus
}
#endif
