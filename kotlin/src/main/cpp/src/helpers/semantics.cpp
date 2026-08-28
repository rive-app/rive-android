#include "helpers/semantics.hpp"

#include "helpers/conversions.hpp"
#include "helpers/jni_string.hpp"

namespace rive_android
{
JniResource<jobject> semanticsDiffToJObject(JNIEnv* env,
                                            const rive::SemanticsDiff& diff)
{
    auto diffClass = FindClass(env, "app/rive/semantics/SemanticsDiff");
    auto ctor =
        env->GetMethodID(diffClass.get(),
                         "<init>",
                         "(JJI[I[Lapp/rive/semantics/SemanticsDiffNode;"
                         "[Lapp/rive/semantics/SemanticsDiffNode;"
                         "[Lapp/rive/semantics/SemanticsChildrenUpdate;"
                         "[Lapp/rive/semantics/SemanticsDiffNode;"
                         "[Lapp/rive/semantics/SemanticsBoundsUpdate;)V");

    auto jRemoved = VecUInt32ToJIntArray(env, diff.removed);
    auto jAdded = semanticsDiffNodesToJArray(env, diff.added);
    auto jMoved = semanticsDiffNodesToJArray(env, diff.moved);
    auto jChildrenUpdated =
        semanticsChildrenUpdatesToJArray(env, diff.childrenUpdated);
    auto jUpdatedSemantic =
        semanticsDiffNodesToJArray(env, diff.updatedSemantic);
    auto jUpdatedGeometry =
        semanticsBoundsUpdatesToJArray(env, diff.updatedGeometry);

    return MakeObject(env,
                      diffClass.get(),
                      ctor,
                      static_cast<jlong>(diff.treeVersion),
                      static_cast<jlong>(diff.frameNumber),
                      static_cast<jint>(diff.rootId),
                      jRemoved.get(),
                      jAdded.get(),
                      jMoved.get(),
                      jChildrenUpdated.get(),
                      jUpdatedSemantic.get(),
                      jUpdatedGeometry.get());
}

JniResource<jobjectArray> semanticsDiffNodesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsDiffNode>& nodes)
{
    auto nodeClass = FindClass(env, "app/rive/semantics/SemanticsDiffNode");
    auto ctor = env->GetMethodID(nodeClass.get(),
                                 "<init>",
                                 "(IILjava/lang/String;Ljava/lang/String;"
                                 "Ljava/lang/String;IIIFFFFII)V");

    auto jArray = env->NewObjectArray(static_cast<jsize>(nodes.size()),
                                      nodeClass.get(),
                                      nullptr);
    for (size_t i = 0; i < nodes.size(); ++i)
    {
        const auto& node = nodes[i];
        auto jLabel = MakeJString(env, node.label);
        auto jValue = MakeJString(env, node.value);
        auto jHint = MakeJString(env, node.hint);
        auto jNode = MakeObject(env,
                                nodeClass.get(),
                                ctor,
                                static_cast<jint>(node.id),
                                static_cast<jint>(node.role),
                                jLabel.get(),
                                jValue.get(),
                                jHint.get(),
                                static_cast<jint>(node.stateFlags),
                                static_cast<jint>(node.traitFlags),
                                static_cast<jint>(node.headingLevel),
                                static_cast<jfloat>(node.minX),
                                static_cast<jfloat>(node.minY),
                                static_cast<jfloat>(node.maxX),
                                static_cast<jfloat>(node.maxY),
                                static_cast<jint>(node.parentId),
                                static_cast<jint>(node.siblingIndex));
        env->SetObjectArrayElement(jArray, static_cast<jsize>(i), jNode.get());
    }

    return {jArray, env};
}

JniResource<jobjectArray> semanticsChildrenUpdatesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsChildrenUpdate>& updates)
{
    auto updateClass =
        FindClass(env, "app/rive/semantics/SemanticsChildrenUpdate");
    auto ctor = env->GetMethodID(updateClass.get(), "<init>", "(I[I)V");

    auto jArray = env->NewObjectArray(static_cast<jsize>(updates.size()),
                                      updateClass.get(),
                                      nullptr);
    for (size_t i = 0; i < updates.size(); ++i)
    {
        const auto& update = updates[i];
        auto jChildIds = VecUInt32ToJIntArray(env, update.childIds);
        auto jUpdate = MakeObject(env,
                                  updateClass.get(),
                                  ctor,
                                  static_cast<jint>(update.parentId),
                                  jChildIds.get());
        env->SetObjectArrayElement(jArray,
                                   static_cast<jsize>(i),
                                   jUpdate.get());
    }

    return {jArray, env};
}

JniResource<jobjectArray> semanticsBoundsUpdatesToJArray(
    JNIEnv* env,
    const std::vector<rive::SemanticsBoundsUpdate>& updates)
{
    auto updateClass =
        FindClass(env, "app/rive/semantics/SemanticsBoundsUpdate");
    auto ctor = env->GetMethodID(updateClass.get(), "<init>", "(IFFFF)V");

    auto jArray = env->NewObjectArray(static_cast<jsize>(updates.size()),
                                      updateClass.get(),
                                      nullptr);
    for (size_t i = 0; i < updates.size(); ++i)
    {
        const auto& update = updates[i];
        auto jUpdate = MakeObject(env,
                                  updateClass.get(),
                                  ctor,
                                  static_cast<jint>(update.id),
                                  static_cast<jfloat>(update.minX),
                                  static_cast<jfloat>(update.minY),
                                  static_cast<jfloat>(update.maxX),
                                  static_cast<jfloat>(update.maxY));
        env->SetObjectArrayElement(jArray,
                                   static_cast<jsize>(i),
                                   jUpdate.get());
    }

    return {jArray, env};
}
} // namespace rive_android
