#pragma once

#include <jni.h>
#include <vector>

#include "helpers/jni_resource.hpp"
#include "rive/semantic/semantic_snapshot.hpp"

namespace rive_android
{
/**
 * Converts a native semantics diff into the Kotlin SemanticsDiff model object.
 *
 * @param env JNI environment used to allocate and populate Kotlin objects.
 * @param diff Native semantics diff payload to marshal.
 * @return Managed JNI local reference wrapping the created SemanticsDiff.
 */
JniResource<jobject> semanticsDiffToJObject(JNIEnv* env,
                                            const rive::SemanticsDiff& diff);

/**
 * Converts native semantics node diffs into Kotlin SemanticsDiffNode[].
 *
 * @param env JNI environment used to allocate and populate Kotlin objects.
 * @param nodes Node payloads to marshal.
 * @return Managed JNI local reference wrapping the created object array.
 */
JniResource<jobjectArray> semanticsDiffNodesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsDiffNode>& nodes);

/**
 * Converts native child-list updates into Kotlin SemanticsChildrenUpdate[].
 *
 * @param env JNI environment used to allocate and populate Kotlin objects.
 * @param updates Child update payloads to marshal.
 * @return Managed JNI local reference wrapping the created object array.
 */
JniResource<jobjectArray> semanticsChildrenUpdatesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsChildrenUpdate>& updates);

/**
 * Converts native bounds updates into Kotlin SemanticsBoundsUpdate[].
 *
 * @param env JNI environment used to allocate and populate Kotlin objects.
 * @param updates Bounds update payloads to marshal.
 * @return Managed JNI local reference wrapping the created object array.
 */
JniResource<jobjectArray> semanticsBoundsUpdatesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsBoundsUpdate>& updates);
} // namespace rive_android
