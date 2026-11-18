package app.rive

import android.view.KeyEvent

/**
 * A key event translated for Rive.
 *
 * @param keyCode The Android key code, which matches a release to its press.
 * @param key The core `Key` value.
 * @param modifiers The core `KeyModifiers` bits.
 * @param isPressed Whether the key went down rather than up.
 * @param isRepeat Whether this press repeats a held key.
 * @param navigation The direction this key moves focus in, or null when it isn't a navigation key.
 * @param isChord Whether Ctrl or Meta is held, which makes this a shortcut rather than typing.
 */
internal data class RiveKeyPress(
    val keyCode: Int,
    val key: Int,
    val modifiers: Int,
    val isPressed: Boolean,
    val isRepeat: Boolean,
    val navigation: RiveFocusDirection?,
    val isChord: Boolean,
) {
    companion object {
        // Core `KeyModifiers` bits, from packages/runtime/include/rive/input/focusable.hpp.
        private const val SHIFT = 1
        private const val CTRL = 2
        private const val ALT = 4
        private const val META = 8

        /**
         * Translates a key event for Rive.
         *
         * Like Android focus navigation, only plain `Tab`, `Shift+Tab`, and arrow keys without
         * modifiers navigate. Lock keys such as Caps Lock don't count as modifiers.
         *
         * @param event The key event the host received.
         * @return The translated event, or null for an action other than down or up, for a key
         *    core has no code for, such as Back or the volume keys, and for presses of numpad
         *    digits and the decimal point while NumLock is off.
         */
        fun from(event: KeyEvent): RiveKeyPress? {
            val isPressed = when (event.action) {
                KeyEvent.ACTION_DOWN -> true
                KeyEvent.ACTION_UP -> false
                else -> return null
            }
            val keyCode = event.keyCode
            val isNumLockKey = keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_DOT
            if (isPressed && isNumLockKey && !event.isNumLockOn) {
                // Android turns these into arrow and editing keys, but only when the app doesn't
                // consume them. Releases still map, since NumLock can change while a key is held.
                return null
            }
            val key = riveKeyOf(keyCode) ?: return null
            val modifiers = (if (event.isShiftPressed) SHIFT else 0) or
                (if (event.isCtrlPressed) CTRL else 0) or
                (if (event.isAltPressed) ALT else 0) or
                (if (event.isMetaPressed) META else 0)
            val navigation = when {
                event.hasNoModifiers() -> when (keyCode) {
                    KeyEvent.KEYCODE_TAB -> RiveFocusDirection.Next
                    KeyEvent.KEYCODE_DPAD_LEFT -> RiveFocusDirection.Left
                    KeyEvent.KEYCODE_DPAD_RIGHT -> RiveFocusDirection.Right
                    KeyEvent.KEYCODE_DPAD_UP -> RiveFocusDirection.Up
                    KeyEvent.KEYCODE_DPAD_DOWN -> RiveFocusDirection.Down
                    else -> null
                }

                keyCode == KeyEvent.KEYCODE_TAB &&
                    event.hasModifiers(KeyEvent.META_SHIFT_ON) -> RiveFocusDirection.Previous

                else -> null
            }
            return RiveKeyPress(
                keyCode = keyCode,
                key = key,
                modifiers = modifiers,
                isPressed = isPressed,
                isRepeat = event.repeatCount > 0,
                navigation = navigation,
                isChord = event.isCtrlPressed || event.isMetaPressed,
            )
        }

        /**
         * Maps an Android key code to the core `Key` value, from
         * packages/runtime/include/rive/input/focusable.hpp.
         *
         * @param keyCode The Android key code.
         * @return The core key value, or null when core has no code for it.
         */
        private fun riveKeyOf(keyCode: Int): Int? = when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 65 + (keyCode - KeyEvent.KEYCODE_A)

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 48 + (keyCode - KeyEvent.KEYCODE_0)

            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 290 + (keyCode - KeyEvent.KEYCODE_F1)

            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                320 + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)

            KeyEvent.KEYCODE_SPACE -> 32

            KeyEvent.KEYCODE_APOSTROPHE -> 39

            KeyEvent.KEYCODE_COMMA -> 44

            KeyEvent.KEYCODE_MINUS -> 45

            KeyEvent.KEYCODE_PERIOD -> 46

            KeyEvent.KEYCODE_SLASH -> 47

            KeyEvent.KEYCODE_SEMICOLON -> 59

            KeyEvent.KEYCODE_EQUALS -> 61

            KeyEvent.KEYCODE_LEFT_BRACKET -> 91

            KeyEvent.KEYCODE_BACKSLASH -> 92

            KeyEvent.KEYCODE_RIGHT_BRACKET -> 93

            KeyEvent.KEYCODE_GRAVE -> 96

            KeyEvent.KEYCODE_ESCAPE -> 256

            // Android TV remotes and D-pads select with the center button.
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> 257

            KeyEvent.KEYCODE_TAB -> 258

            KeyEvent.KEYCODE_DEL -> 259

            KeyEvent.KEYCODE_INSERT -> 260

            KeyEvent.KEYCODE_FORWARD_DEL -> 261

            KeyEvent.KEYCODE_DPAD_RIGHT -> 262

            KeyEvent.KEYCODE_DPAD_LEFT -> 263

            KeyEvent.KEYCODE_DPAD_DOWN -> 264

            KeyEvent.KEYCODE_DPAD_UP -> 265

            KeyEvent.KEYCODE_PAGE_UP -> 266

            KeyEvent.KEYCODE_PAGE_DOWN -> 267

            KeyEvent.KEYCODE_MOVE_HOME -> 268

            KeyEvent.KEYCODE_MOVE_END -> 269

            KeyEvent.KEYCODE_CAPS_LOCK -> 280

            KeyEvent.KEYCODE_SCROLL_LOCK -> 281

            KeyEvent.KEYCODE_NUM_LOCK -> 282

            KeyEvent.KEYCODE_SYSRQ -> 283

            KeyEvent.KEYCODE_BREAK -> 284

            KeyEvent.KEYCODE_NUMPAD_DOT -> 330

            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 331

            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 332

            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 333

            KeyEvent.KEYCODE_NUMPAD_ADD -> 334

            KeyEvent.KEYCODE_NUMPAD_ENTER -> 335

            KeyEvent.KEYCODE_NUMPAD_EQUALS -> 336

            KeyEvent.KEYCODE_SHIFT_LEFT -> 340

            KeyEvent.KEYCODE_CTRL_LEFT -> 341

            KeyEvent.KEYCODE_ALT_LEFT -> 342

            KeyEvent.KEYCODE_META_LEFT -> 343

            KeyEvent.KEYCODE_SHIFT_RIGHT -> 344

            KeyEvent.KEYCODE_CTRL_RIGHT -> 345

            KeyEvent.KEYCODE_ALT_RIGHT -> 346

            KeyEvent.KEYCODE_META_RIGHT -> 347

            KeyEvent.KEYCODE_MENU -> 348

            else -> null
        }
    }
}
