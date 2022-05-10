#include "models/dimensions_helper.hpp"
#include "helpers/general.hpp"
#include "jni_refs.hpp"
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

	JNIEXPORT jobject JNICALL
	Java_app_rive_runtime_kotlin_core_Helpers_cppConvertToArtboardSpace(
	    JNIEnv* env,
	    jobject thisObj,
	    jlong touchSpaceBoundsRef,
	    jobject touchSpacePointF,
	    jobject jfit,
	    jobject jalignment,
	    jlong artboardSpaceBoundsRef)
	{
		auto fit = ::getFit(env, jfit);
		auto alignment = ::getAlignment(env, jalignment);
		auto artboardSpaceBounds = (rive::AABB*)artboardSpaceBoundsRef;
		auto touchSpaceBounds = (rive::AABB*)touchSpaceBoundsRef;
		jlong touchX =
		    (jlong)env->GetFloatField(touchSpacePointF, getXFieldId());
		jlong touchY =
		    (jlong)env->GetFloatField(touchSpacePointF, getYFieldId());

		rive::Mat2D forward = rive::computeAlignment(
		    fit, alignment, *touchSpaceBounds, *artboardSpaceBounds);
		rive::Mat2D inverse = forward.invertOrIdentity();

		auto touchLocation = rive::Vec2D(touchX, touchY);
		rive::Vec2D convertedLocation = inverse * touchLocation;

		return env->NewObject(getPointerFClass(),
		                      getPointFInitMethod(),
		                      convertedLocation.x(),
		                      convertedLocation.y());
	}

#ifdef __cplusplus
}
#endif
