#include "helpers/font_helper.hpp"

#include <algorithm>
#include <string_view>

#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/jni_string.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveN/FontHelper";
constexpr uint32_t kWeightAxis = ('w' << 24) | ('g' << 16) | ('h' << 8) | 't';

/* static */ std::vector<rive::rcp<rive::Font>> FontHelper::s_fallbackFonts;
/* static */ std::unordered_map<uint32_t, std::vector<rive::rcp<rive::Font>>>
    FontHelper::s_pickFontCache;
/* static */ uint32_t FontHelper::s_pickLanguagesGeneration = 0;
/* static */ std::
    map<std::pair<size_t, size_t>, std::vector<FontHelper::StrategyFont>>
        FontHelper::s_decodedByContent;
/* static */ std::
    map<std::pair<std::string, unsigned>, FontHelper::SystemFallback>
        FontHelper::s_systemFonts;
/* static */ std::unordered_map<
    rive::TextLanguageId,
    std::vector<FontHelper::SystemFallback*>>
    FontHelper::s_systemChains;
/* static */ std::atomic<uint32_t> FontHelper::s_localeGeneration{1};
/* static */ uint32_t FontHelper::s_loadedGeneration = 0;
/* static */ std::unordered_map<uint64_t, rive::rcp<rive::Font>>
    FontHelper::s_systemFontByCodepoint;
/* static */ std::
    map<std::pair<const rive::Font*, uint16_t>, rive::rcp<rive::Font>>
        FontHelper::s_weightInstances;
/* static */ std::mutex FontHelper::s_fallbackFontsMutex;

/* static */ bool FontHelper::RegisterFallbackFont(jbyteArray byteArray)
{
  rive::rcp<rive::Font> fallback = HBFont::Decode(
      ByteArrayToUint8Vec(GetJNIEnv(), byteArray));
  if (!fallback)
  {
    RiveLogE(TAG, "RegisterFallbackFont - failed to decode byte fonts");
    return false;
  }

  std::lock_guard<std::mutex> lock(s_fallbackFontsMutex);
  s_fallbackFonts.push_back(fallback);
  // Text shaped without this fallback shapes again.
  rive::Font::fallbackFontsChanged();
  return true;
}
/**
 * Finds [name] and retains it for the process lifetime, so later lookups need
 * no class loading.
 * @return The class, or nullptr when it can't be found.
 */
static jclass RetainClass(JNIEnv* env, const char* name)
{
  auto localClass = FindClass(env, name);
  if (!localClass.get())
  {
    return nullptr;
  }
  auto retained = static_cast<jclass>(env->NewGlobalRef(localClass.get()));
  if (JNIExceptionHandler::ClearAndLogErrors(env, TAG, "Retaining a class"))
  {
    return nullptr;
  }
  return retained;
}

/**
 * Finds a static method, logging and clearing the error when it's missing.
 * @return The method, or nullptr when it can't be found.
 */
static jmethodID FindStaticMethod(
    JNIEnv* env,
    jclass owner,
    const char* name,
    const char* signature)
{
  jmethodID method = env->GetStaticMethodID(owner, name, signature);
  if (JNIExceptionHandler::ClearAndLogErrors(env, TAG, name))
  {
    return nullptr;
  }
  return method;
}

/* static */ std::vector<std::pair<std::string, unsigned>> FontHelper::
    ListSystemFallbacks(const std::string& languageTag)
{
  std::vector<std::pair<std::string, unsigned>> files;
  JNIEnv* env = GetJNIEnv();
  // Access is serialized by s_fallbackFontsMutex. Retain the classes for the
  // process lifetime so later chains need no class lookup.
  static jclass fontHelperClass = nullptr;
  static jclass localeClass = nullptr;
  static jmethodID getChainMethodId = nullptr;
  static jmethodID getChainForLocaleMethodId = nullptr;
  static jmethodID forLanguageTagMethodId = nullptr;
  if (!fontHelperClass)
  {
    fontHelperClass = RetainClass(
        env,
        "app/rive/runtime/kotlin/fonts/FontHelper");
  }
  if (!localeClass)
  {
    localeClass = RetainClass(env, "java/util/Locale");
  }
  if (!fontHelperClass || !localeClass)
  {
    return files;
  }
  if (!getChainMethodId)
  {
    getChainMethodId = FindStaticMethod(
        env,
        fontHelperClass,
        "getSystemFallbackChain",
        "()Ljava/util/List;");
  }
  if (!getChainForLocaleMethodId)
  {
    getChainForLocaleMethodId = FindStaticMethod(
        env,
        fontHelperClass,
        "getSystemFallbackChain",
        "(Ljava/util/Locale;)Ljava/util/List;");
  }
  if (!forLanguageTagMethodId)
  {
    forLanguageTagMethodId = FindStaticMethod(
        env,
        localeClass,
        "forLanguageTag",
        "(Ljava/lang/String;)Ljava/util/Locale;");
  }
  if (!getChainMethodId || !getChainForLocaleMethodId ||
      !forLanguageTagMethodId)
  {
    return files;
  }

  JniResource<jobject> chainObj(nullptr, env);
  if (languageTag.empty())
  {
    chainObj = JniResource<jobject>(
        env->CallStaticObjectMethod(fontHelperClass, getChainMethodId),
        env);
  }
  else
  {
    // The language's own families go first, as the default locale's do for
    // text without one.
    auto jTag = MakeJString(env, languageTag);
    JniResource<jobject> locale(
        env->CallStaticObjectMethod(
            localeClass,
            forLanguageTagMethodId,
            jTag.get()),
        env);
    if (JNIExceptionHandler::ClearAndLogErrors(
            env,
            TAG,
            "FontHelper couldn't make a Locale for a text language") ||
        !locale.get())
    {
      return files;
    }
    chainObj = JniResource<jobject>(
        env->CallStaticObjectMethod(
            fontHelperClass,
            getChainForLocaleMethodId,
            locale.get()),
        env);
  }
  // A missing fallback must not take the app down, so log and carry on.
  if (JNIExceptionHandler::ClearAndLogErrors(
          env,
          TAG,
          "FontHelper couldn't list the system fallback fonts") ||
      !chainObj.get())
  {
    return files;
  }
  JniResource<jclass> fontFileClass = FindClass(
      env,
      "app/rive/runtime/kotlin/fonts/Fonts$FontFile");

  JniResource<jclass> listClass = GetObjectClass(env, chainObj.get());
  jmethodID listSizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
  jmethodID listGetMethod = env->GetMethodID(
      listClass.get(),
      "get",
      "(I)Ljava/lang/Object;");
  jmethodID getPathMethod = env->GetMethodID(
      fontFileClass.get(),
      "getPath",
      "()Ljava/lang/String;");
  jmethodID getTtcIndexMethod = env->GetMethodID(
      fontFileClass.get(),
      "getTtcIndex",
      "()I");

  jint chainSize = JNIExceptionHandler::CallIntMethod(
      env,
      chainObj.get(),
      listSizeMethod);
  files.reserve(chainSize);
  for (jint i = 0; i < chainSize; ++i)
  {
    JniResource<jobject> fontFile = GetObjectFromMethod(
        env,
        chainObj.get(),
        listGetMethod,
        i);
    JniResource<jobject> path = GetObjectFromMethod(
        env,
        fontFile.get(),
        getPathMethod);
    jint ttcIndex = JNIExceptionHandler::CallIntMethod(
        env,
        fontFile.get(),
        getTtcIndexMethod);
    files.emplace_back(
        JStringToString(env, static_cast<jstring>(path.get())),
        static_cast<unsigned>(ttcIndex));
  }
  return files;
}

/* static */ const std::vector<FontHelper::SystemFallback*>* FontHelper::
    SystemFallbackChain(rive::TextLanguageId language)
{
  uint32_t generation = s_localeGeneration.load(std::memory_order_acquire);
  if (generation != s_loadedGeneration)
  {
    // Invalidate both hits and misses before selecting with new orderings.
    // Fonts stay in s_systemFonts, and existing text retains its own font
    // references, so clearing caches cannot unmap bytes still in use by a
    // shaped run.
    s_systemChains.clear();
    s_systemFontByCodepoint.clear();
    s_weightInstances.clear();
    s_loadedGeneration = generation;
  }
  auto cached = s_systemChains.find(language);
  if (cached != s_systemChains.end())
  {
    return &cached->second;
  }
  std::vector<std::pair<std::string, unsigned>> files = ListSystemFallbacks(
      rive::TextLanguages::tag(language));
  if (files.empty())
  {
    return nullptr;
  }
  std::vector<SystemFallback*> chain;
  chain.reserve(files.size());
  for (auto& file : files)
  {
    // Chains for other languages list the same files in another order, so a
    // face they already tried keeps its font or probe.
    auto [entry, isNew] = s_systemFonts.try_emplace(file);
    if (isNew)
    {
      entry->second.path = file.first;
      entry->second.faceIndex = file.second;
    }
    chain.push_back(&entry->second);
  }
  return &s_systemChains.emplace(language, std::move(chain)).first->second;
}

/* static */ rive::rcp<rive::Font> FontHelper::FindSystemFallback(
    rive::Unichar missing,
    uint16_t weight,
    rive::TextLanguageId language)
{
  // No font draws these, and a miss would map every system font.
  if (missing < 0x20 || (missing >= 0x7F && missing < 0xA0) ||
      (missing >= 0x200B && missing <= 0x200F) ||
      (missing >= 0x2028 && missing <= 0x202E) || missing == 0xFEFF)
  {
    return nullptr;
  }
  const std::vector<SystemFallback*>* chain = SystemFallbackChain(language);
  if (!chain)
  {
    RiveLogW(TAG, "FindSystemFallback - No current system fonts found");
    return nullptr;
  }

  auto [entry, isNew] = s_systemFontByCodepoint.try_emplace(
      CodepointKey(missing, language));
  if (isNew)
  {
    const char* languageTag = language == rive::TextLanguages::none
                                  ? "default locale"
                                  : rive::TextLanguages::tag(language).c_str();
    for (SystemFallback* fallbackPtr : *chain)
    {
      SystemFallback& fallback = *fallbackPtr;
      if (!fallback.attempted)
      {
        fallback.probe = HBFont::ProbeFile(
            fallback.path.c_str(),
            fallback.faceIndex);
        fallback.attempted = true;
      }
      const bool covered = fallback.font
                               ? fallback.font->hasGlyph(missing)
                               : fallback.probe &&
                                     fallback.probe->hasGlyph(missing);
      if (covered)
      {
        if (!fallback.font)
        {
          // Promote the matching probe without reopening or
          // remapping.
          fallback.font = fallback.probe->makeFont();
          fallback.probe.reset();
        }
        entry->second = fallback.font;
        RiveLogD(
            TAG,
            "Fallback for U+%04X '%s' (%s): %s face %u",
            missing,
            DebugCodepoint(missing).c_str(),
            languageTag,
            fallback.path.c_str(),
            fallback.faceIndex);
        break;
      }
    }
    if (!entry->second)
    {
      RiveLogD(
          TAG,
          "Fallback for U+%04X '%s' (%s): no system font has it",
          missing,
          DebugCodepoint(missing).c_str(),
          languageTag);
    }
  }
  return entry->second ? AtWeight(entry->second, weight) : nullptr;
}

/* static */ bool FontHelper::HasWeightAxis(const rive::Font& font)
{
  for (uint16_t i = 0; i < font.getAxisCount(); ++i)
  {
    if (font.getAxis(i).tag == kWeightAxis)
    {
      return true;
    }
  }
  return false;
}

/* static */ rive::rcp<rive::Font> FontHelper::AtWeight(
    const rive::rcp<rive::Font>& font,
    uint16_t weight)
{
  if (!HasWeightAxis(*font) || font->getWeight() == weight)
  {
    return font;
  }

  rive::rcp<rive::Font>& instance = s_weightInstances[{font.get(), weight}];
  if (!instance)
  {
    rive::Font::Coord coord = {kWeightAxis, static_cast<float>(weight)};
    instance = font->withOptions(
        rive::Span<const rive::Font::Coord>(&coord, 1),
        rive::Span<const rive::Font::Feature>());
  }
  return instance;
}

/* static */ const std::vector<rive::rcp<rive::Font>>& FontHelper::PickFonts(
    uint16_t weight,
    rive::TextLanguageId language,
    rive::Span<const std::string> languages)
{
  // Stable return value (referenced) when aborting due to errors.
  // Always empty.
  static const std::vector<rive::rcp<rive::Font>> ERROR_VEC;

  // Check if weight and language exist in cache first
  const uint32_t cacheKey = PickKey(weight, language);
  auto cacheIt = s_pickFontCache.find(cacheKey);
  if (cacheIt != s_pickFontCache.end())
  {
    return cacheIt->second;
  }

  // Original JNI implementation for cache misses
  JNIEnv* env = GetJNIEnv();
  JniResource<jclass> pickerClass = FindClass(
      env,
      "app/rive/runtime/kotlin/fonts/FontFallbackStrategy");
  if (!pickerClass.get())
  {
    RiveLogE(TAG, "FontFallbackStrategy class not found");
    return ERROR_VEC;
  }

  // Get the Companion field ID
  jfieldID fontCompanionField = env->GetStaticFieldID(
      pickerClass.get(),
      "Companion",
      "Lapp/rive/runtime/kotlin/fonts/FontFallbackStrategy$Companion;");

  // Get the Companion object
  JniResource<jobject> companionObject = GetStaticObjectField(
      env,
      pickerClass.get(),
      fontCompanionField);

  // Find the Companion class
  JniResource<jclass> pickerCompanionClass = FindClass(
      env,
      "app/rive/runtime/kotlin/fonts/FontFallbackStrategy$Companion");
  if (!pickerCompanionClass.get())
  {
    RiveLogE(TAG, "FontFallbackStrategy Companion class not found");
    return ERROR_VEC;
  }

  jmethodID pickFontMid = env->GetMethodID(
      pickerCompanionClass.get(),
      "pickFont",
      "(I[Ljava/lang/String;)Ljava/util/List;");

  JniResource<jclass> stringClass = FindClass(env, "java/lang/String");
  if (!stringClass.get())
  {
    return ERROR_VEC;
  }
  JniResource<jobjectArray> languageArray(
      env->NewObjectArray(
          static_cast<jsize>(languages.size()),
          stringClass.get(),
          nullptr),
      env);
  if (JNIExceptionHandler::ClearAndLogErrors(
          env,
          TAG,
          "Allocating the strategy's languages") ||
      !languageArray.get())
  {
    return ERROR_VEC;
  }
  for (size_t i = 0; i < languages.size(); ++i)
  {
    JniResource<jstring> tag = MakeJString(env, languages[i]);
    env->SetObjectArrayElement(
        languageArray.get(),
        static_cast<jsize>(i),
        tag.get());
  }

  // Call the static pickFont method
  JniResource<jobject> fontListObj = GetObjectFromMethod(
      env,
      companionObject.get(),
      pickFontMid,
      weight,
      languageArray.get());

  // A strategy that throws or returns null (possible from Java) gets the
  // same result as an empty list. Caching it means the strategy is not
  // called again for this weight and language.
  if (JNIExceptionHandler::ClearAndLogErrors(
          env,
          TAG,
          "FontFallbackStrategy.getFont() threw.") ||
      fontListObj.get() == nullptr)
  {
    RiveLogE(TAG, "No strategy fonts for weight %d.", weight);
    auto [iter, _] = s_pickFontCache.emplace(
        cacheKey,
        std::vector<rive::rcp<rive::Font>>());
    return iter->second;
  }

  // Convert Java List<ByteArray> to std::vector<_>
  // Get the List class and methods
  JniResource<jclass> listClass = GetObjectClass(env, fontListObj.get());
  jmethodID listSizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
  jmethodID listGetMethod = env->GetMethodID(
      listClass.get(),
      "get",
      "(I)Ljava/lang/Object;");

  jint listSize = env->CallIntMethod(fontListObj.get(), listSizeMethod);
  // A list that throws is treated like the strategy throwing.
  if (JNIExceptionHandler::ClearAndLogErrors(
          env,
          TAG,
          "FontFallbackStrategy list size() threw."))
  {
    listSize = 0;
  }

  std::vector<rive::rcp<rive::Font>> decodedFonts;
  decodedFonts.reserve(listSize);

  for (jint i = 0; i < listSize; ++i)
  {
    JniResource<jobject> byteArrayObj = GetObjectFromMethod(
        env,
        fontListObj.get(),
        listGetMethod,
        i);
    if (JNIExceptionHandler::ClearAndLogErrors(
            env,
            TAG,
            "FontFallbackStrategy list get() threw.") ||
        byteArrayObj.get() == nullptr)
    {
      RiveLogE(
          TAG,
          "Skipping fallback font at index %d for weight %d",
          i,
          weight);
      continue;
    }

    rive::rcp<rive::Font> decodedFont = DecodeShared(ByteArrayToUint8Vec(
        env,
        reinterpret_cast<jbyteArray>(byteArrayObj.get())));
    if (!decodedFont)
    {
      RiveLogE(
          TAG,
          "Failed to decode fallback font at index %d for weight %d",
          i,
          weight);
    }
    // A repeat would end the shaper's walk, which stops when a fallback
    // returns the font it is already using.
    else if (
        std::find(decodedFonts.begin(), decodedFonts.end(), decodedFont) ==
        decodedFonts.end())
    {
      decodedFonts.push_back(std::move(decodedFont));
    }
  }

  auto [iter, _] = s_pickFontCache.emplace(cacheKey, std::move(decodedFonts));

  return iter->second;
}

/* static */ rive::rcp<rive::Font> FontHelper::DecodeShared(
    std::vector<uint8_t>&& bytes)
{
  // Strategies commonly return the same font for several weights.
  const std::string_view content(
      reinterpret_cast<const char*>(bytes.data()),
      bytes.size());
  size_t hash = std::hash<std::string_view>{}(content);
  auto& bucket = s_decodedByContent[{bytes.size(), hash}];
  for (const auto& entry : bucket)
  {
    if (content == entry.content)
    {
      return entry.font;
    }
  }
  auto font = HBFont::Decode(std::move(bytes));
  if (font)
  {
    // Decode adopts the vector allocation without copying it. Keeping the
    // font alive also keeps content's bytes alive through its HB blob.
    bucket.push_back({font, content});
  }
  return font;
}

std::string FontHelper::UTF8FromCodepoint(rive::Unichar cp)
{
  std::string out;
  out.reserve(4);

  if (cp <= 0x7F)
  {
    out.push_back((char)cp);
  }
  else if (cp <= 0x7FF)
  {
    out.push_back((char)(0xC0 | (cp >> 6)));
    out.push_back((char)(0x80 | (cp & 0x3F)));
  }
  else if (cp <= 0xFFFF)
  {
    out.push_back((char)(0xE0 | (cp >> 12)));
    out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back((char)(0x80 | (cp & 0x3F)));
  }
  else
  { // <= 0x10FFFF
    out.push_back((char)(0xF0 | (cp >> 18)));
    out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
    out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back((char)(0x80 | (cp & 0x3F)));
  }

  return out;
}

std::string FontHelper::DebugCodepoint(rive::Unichar cp)
{
  // Control characters can be mapped to their "Control Pictures" equivalent
  if (cp <= 0x20)
  {
    cp += 0x2400;
  }
  // Special case for the delete character
  else if (cp == 0x7F)
  {
    cp = 0x2421;
  }

  return UTF8FromCodepoint(cp);
}

/**
 * Finds and returns a potential fallback font when `request.font` lacks the
 * `request.missing` character.
 *
 * This function implements the fallback strategy used when the text shaper
 * encounters a glyph missing in the currently selected font for a text run.
 *
 * Execution Scope: This function is expected to be called from the render
 * thread.
 *
 * Fallback Strategy:
 *
 * 1.  **Weight-Matched Selection:** Uses the weight from `request.font`
 * (captured on the first attempt where `fallbackIndex == 0`) and the request's
 * languages to retrieve an ordered list of potential fallback fonts via
 * `PickFonts()`. This internal helper interacts with the Kotlin
 * `FontFallbackStrategy.getFont(weight, languages)` API and will cache decoded
 * fonts (`HBFont`). If `fallbackIndex` is within the bounds of the retrieved
 * list, the font at that index is returned directly. The caller (i.e. the
 * shaper) is attempts shaping the run with the returned font. If it can't,
 * it'll call this function again with an incremented `fallbackIndex`.
 *
 * 2.  **Registered Fallback Search (Deprecated):** If the weight-matched
 * list is exhausted (`fallbackIndex` is out of bounds), iterates through
 * all globally registered fallback fonts (`s_fallbackFonts`). Returns the
 * first registered fallback font that contains the `missing` glyph (checked
 * via `hasGlyph`).
 *
 * 3.  **System Font Fallback:** If no registered fallback contains the
 * glyph, returns the first font in `FontHelper.getSystemFallbackChain()` that
 * does, at the desired weight when the font is variable. The chain puts the
 * families for the request's most preferred language first, or the default
 * locale's when the request has none, so Japanese and Chinese text get their
 * own forms of shared Han characters.
 *
 * Thread Safety: Access to shared fallback resources and internal state is
 * protected by a mutex (`s_fallbackFontsMutex`). State Preservation: The
 * `desiredWeight` for steps 1 and 3 is stored statically and updated only when
 * `fallbackIndex` is 0 to ensure consistency across multiple fallback
 * attempts for the same missing glyph sequence.
 *
 * @param request The character, attempt number, font that failed to shape
 * it, and the languages of its text, most preferred first.
 * @return A reference-counted pointer (`rcp`) to a suitable fallback Font,
 * or `nullptr` if no suitable fallback could be found.
 */
/* static */ rive::rcp<rive::Font> FontHelper::FindFontFallback(
    const rive::Font::FallbackRequest& request)
{
  // Let's lock this down for thread-safety.
  std::lock_guard<std::mutex> lock(s_fallbackFontsMutex);

  // Keep a global variable here so we look for a valid match against the
  // original font weight.
  static uint16_t desiredWeight = 400;
  if (request.fallbackIndex == 0)
  {
    desiredWeight = request.font->getWeight();
  }

  // The most preferred language orders the system chain and keys both
  // caches in one byte. The rest of a request's list is the system
  // languages, so strategy fonts only go stale when those change. A tag the
  // table has no room for shares the default locale's selections.
  const rive::TextLanguageId language = request.languages.empty()
                                            ? rive::TextLanguages::none
                                            : rive::TextLanguages::intern(
                                                  request.languages[0]);
  const uint32_t languagesGeneration = rive::TextLanguages::generation();
  if (languagesGeneration != s_pickLanguagesGeneration)
  {
    s_pickFontCache.clear();
    s_pickLanguagesGeneration = languagesGeneration;
  }

  const std::vector<rive::rcp<rive::Font>>& pickedFonts = PickFonts(
      desiredWeight,
      language,
      request.languages);
  if (request.fallbackIndex < pickedFonts.size())
  {
    return pickedFonts[request.fallbackIndex];
  }

  // Use the old path - just try to find a match for this glyph.
  for (const rive::rcp<rive::Font>& fFont : s_fallbackFonts)
  {
    if (fFont->hasGlyph(request.missing))
    {
      return fFont;
    }
  }

  return FindSystemFallback(request.missing, desiredWeight, language);
}

/* static */ rive::rcp<rive::Font> FontHelper::FindFontFallback(
    const rive::Unichar missing,
    const uint32_t fallbackIndex,
    const rive::Font* riveFont)
{
  rive::Font::FallbackRequest request = {};
  request.missing = missing;
  request.fallbackIndex = fallbackIndex;
  request.font = riveFont;
  return FindFontFallback(request);
}

} // namespace rive_android
