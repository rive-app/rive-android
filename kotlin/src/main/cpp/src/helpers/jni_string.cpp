#include "helpers/jni_string.hpp"

#include <cstdint>
#include <limits>
#include <string_view>
#include <vector>

namespace rive_android
{
namespace
{
// Unicode designates U+FFFD as the replacement character for input that cannot
// be decoded. Using it for malformed UTF-8 or unpaired UTF-16 surrogates marks
// where data was invalid while keeping the converted string well-formed.
constexpr uint32_t replacementCharacter = 0xFFFD;

/**
 * Checks whether a byte is a UTF-8 continuation byte.
 *
 * @param byte Byte to inspect.
 * @return True when the byte has the UTF-8 continuation-byte prefix.
 */
bool IsUTF8ContinuationByte(uint8_t byte) { return (byte & 0xC0) == 0x80; }

/**
 * Appends a Unicode code point as one or two UTF-16 code units.
 *
 * @param codePoint Valid Unicode scalar value to append.
 * @param output Destination UTF-16 buffer.
 */
void AppendUTF16(uint32_t codePoint, std::vector<jchar>& output)
{
    if (codePoint <= 0xFFFF)
    {
        // Basic Multilingual Plane code points fit in one UTF-16 code unit.
        output.push_back(static_cast<jchar>(codePoint));
        return;
    }

    // Supplementary code points require a high and low UTF-16 surrogate pair.
    codePoint -= 0x10000;
    output.push_back(static_cast<jchar>(0xD800 + (codePoint >> 10)));
    output.push_back(static_cast<jchar>(0xDC00 + (codePoint & 0x3FF)));
}

/**
 * Appends a Unicode code point as standard UTF-8.
 *
 * @param codePoint Valid Unicode scalar value to append.
 * @param output Destination UTF-8 string.
 */
void AppendUTF8(uint32_t codePoint, std::string& output)
{
    if (codePoint <= 0x7F)
    {
        output.push_back(static_cast<char>(codePoint));
    }
    else if (codePoint <= 0x7FF)
    {
        output.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
        output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
    else if (codePoint <= 0xFFFF)
    {
        output.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
        output.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
    else
    {
        output.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
        output.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
}

/**
 * Converts standard UTF-8 into UTF-16 for JNI NewString.
 *
 * Malformed UTF-8 bytes are replaced with U+FFFD. This function intentionally
 * does not log conversion failures because it is also used by the logging
 * bridge and logging here would recurse.
 *
 * @param utf8 Standard UTF-8 bytes to convert.
 * @return UTF-16 code units suitable for JNI NewString.
 */
std::vector<jchar> UTF8ToUTF16(std::string_view utf8)
{
    std::vector<jchar> utf16;
    utf16.reserve(utf8.size());

    size_t index = 0;
    while (index < utf8.size())
    {
        const auto lead = static_cast<uint8_t>(utf8[index]);
        if (lead <= 0x7F)
        {
            utf16.push_back(static_cast<jchar>(lead));
            ++index;
            continue;
        }

        size_t sequenceLength = 0;
        uint32_t codePoint = 0;
        uint32_t minimumCodePoint = 0;
        if (lead >= 0xC2 && lead <= 0xDF)
        {
            sequenceLength = 2;
            codePoint = lead & 0x1F;
            minimumCodePoint = 0x80;
        }
        else if (lead >= 0xE0 && lead <= 0xEF)
        {
            sequenceLength = 3;
            codePoint = lead & 0x0F;
            minimumCodePoint = 0x800;
        }
        else if (lead >= 0xF0 && lead <= 0xF4)
        {
            sequenceLength = 4;
            codePoint = lead & 0x07;
            minimumCodePoint = 0x10000;
        }

        bool isValid =
            sequenceLength != 0 && index + sequenceLength <= utf8.size();
        for (size_t offset = 1; isValid && offset < sequenceLength; ++offset)
        {
            const auto continuation =
                static_cast<uint8_t>(utf8[index + offset]);
            isValid = IsUTF8ContinuationByte(continuation);
            if (isValid)
            {
                codePoint = (codePoint << 6) | (continuation & 0x3F);
            }
        }

        isValid = isValid && codePoint >= minimumCodePoint &&
                  codePoint <= 0x10FFFF &&
                  !(codePoint >= 0xD800 && codePoint <= 0xDFFF);
        if (!isValid)
        {
            utf16.push_back(static_cast<jchar>(replacementCharacter));
            ++index;
            continue;
        }

        AppendUTF16(codePoint, utf16);
        index += sequenceLength;
    }

    return utf16;
}

/**
 * Creates a JVM string through JNI's UTF-16 interface from a bounded standard
 * UTF-8 byte sequence.
 *
 * @param env JNI environment used to create the string.
 * @param utf8 Native standard UTF-8 bytes to convert.
 * @return Managed JNI local reference to a JVM string constructed from UTF-16
 * code units, or null when the input is too large.
 */
JniResource<jstring> MakeJString(JNIEnv* env, std::string_view utf8)
{
    auto utf16 = UTF8ToUTF16(utf8);
    if (utf16.size() > static_cast<size_t>(std::numeric_limits<jsize>::max()))
    {
        return MakeJniResource<jstring>(nullptr, env);
    }

    static constexpr jchar emptyString = 0;
    const jchar* utf16Data = utf16.empty() ? &emptyString : utf16.data();
    auto jString = env->NewString(utf16Data, static_cast<jsize>(utf16.size()));
    return MakeJniResource(jString, env);
}
} // namespace

std::string JStringToString(JNIEnv* env, jstring jString)
{
    if (jString == nullptr)
    {
        return {};
    }

    const jsize length = env->GetStringLength(jString);
    const jchar* utf16 = env->GetStringChars(jString, nullptr);
    if (utf16 == nullptr)
    {
        return {};
    }

    std::string utf8;
    utf8.reserve(static_cast<size_t>(length));
    for (jsize index = 0; index < length; ++index)
    {
        uint32_t codePoint = utf16[index];
        // A high surrogate starts a pair representing a code point above the
        // Basic Multilingual Plane and must be followed by a low surrogate.
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF)
        {
            if (index + 1 < length && utf16[index + 1] >= 0xDC00 &&
                utf16[index + 1] <= 0xDFFF)
            {
                // Combine both surrogates' 10-bit payloads into the original
                // supplementary code point, then consume the low surrogate.
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) +
                            (utf16[index + 1] - 0xDC00);
                ++index;
            }
            else
            {
                // An unmatched high surrogate is not a valid Unicode scalar.
                codePoint = replacementCharacter;
            }
        }
        // A low surrogate is invalid here because a valid pair would have
        // consumed it while processing its preceding high surrogate.
        else if (codePoint >= 0xDC00 && codePoint <= 0xDFFF)
        {
            codePoint = replacementCharacter;
        }

        AppendUTF8(codePoint, utf8);
    }

    env->ReleaseStringChars(jString, utf16);
    return utf8;
}

JniResource<jstring> MakeJString(JNIEnv* env, const char* utf8)
{
    if (utf8 == nullptr)
    {
        return MakeJniResource<jstring>(nullptr, env);
    }
    return MakeJString(env, std::string_view(utf8));
}

JniResource<jstring> MakeJString(JNIEnv* env, const std::string& utf8)
{
    return MakeJString(env, std::string_view(utf8.data(), utf8.size()));
}
} // namespace rive_android
