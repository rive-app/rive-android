package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.FontAsset
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.core.RiveWorker
import app.rive.rememberFont
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/** Rides the Yamanote loop on a station sign whose Japanese and Korean text uses bound fonts. */
class ComposeFontBindingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(NIGHT.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(NIGHT.toArgb()),
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val riveFileResult = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.font_db_station_sign),
                riveWorker,
            )
            val fontsResult = rememberRawFont(R.raw.station_font_ja, riveWorker)
                .zip(rememberRawFont(R.raw.station_font_ko, riveWorker))

            var stationIndex by rememberSaveable {
                mutableIntStateOf(Station.LOOP.indexOfFirst { it.romaji == "Shibuya" })
            }
            var fontChoice by rememberSaveable { mutableStateOf(FontChoice.App) }

            val contentResult = riveFileResult.zip(fontsResult).andThen { (file, fonts) ->
                val (japaneseFont, koreanFont) = fonts
                val source = ViewModelSource.Named("StationSign").defaultInstance()
                rememberViewModelInstanceResult(file, source)
                    .zip(rememberViewModelInstanceResult(file, source)) { appFonts, systemFonts ->
                        StationSignContent(
                            file = file,
                            japaneseFont = japaneseFont,
                            koreanFont = koreanFont,
                            appFontsVmi = appFonts,
                            systemFontsVmi = systemFonts,
                        )
                    }
            }

            Scaffold(containerColor = NIGHT) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (contentResult) {
                            is Result.Loading -> LoadingIndicator()

                            is Result.Error -> ErrorMessage(contentResult.throwable)

                            is Result.Success -> {
                                val content = contentResult.value
                                val appFontsVmi = content.appFontsVmi
                                val systemFontsVmi = content.systemFontsVmi
                                // Only the App fonts view model instance gets fonts. The other
                                // keeps the authored Inter, so its Japanese and Korean text falls
                                // back to system fonts. Clearing a font with null instead would
                                // leave that text blank.
                                val vmi = when (fontChoice) {
                                    FontChoice.App -> appFontsVmi
                                    FontChoice.System -> systemFontsVmi
                                }
                                val boundVmi by rememberUpdatedState(vmi)

                                LaunchedEffect(content) {
                                    appFontsVmi.setFont("japaneseFont", content.japaneseFont)
                                    appFontsVmi.setFont("koreanFont", content.koreanFont)
                                    content.showStation(stationIndex)
                                    // Triggers from both view model instances are collected, so a
                                    // tap during a switch still moves the sign.
                                    merge(
                                        appFontsVmi.getTriggerFlow("previous").map { -1 },
                                        appFontsVmi.getTriggerFlow("next").map { 1 },
                                        systemFontsVmi.getTriggerFlow("previous").map { -1 },
                                        systemFontsVmi.getTriggerFlow("next").map { 1 },
                                    ).collect { step ->
                                        stationIndex = (stationIndex + step).mod(Station.LOOP.size)
                                        // The ride hides the sign's text while the train is away,
                                        // so the new text goes in before the ride starts.
                                        content.showStation(stationIndex)
                                        val ride = if (step > 0) "arriveNext" else "arrivePrevious"
                                        boundVmi.fireTrigger(ride)
                                    }
                                }

                                Rive(
                                    content.file,
                                    Modifier.fillMaxSize(),
                                    viewModelInstance = vmi,
                                    fit = Fit.Contain(),
                                )
                            }
                        }
                    }

                    FontChoiceControls(
                        choice = fontChoice,
                        onChoiceChange = { fontChoice = it },
                    )
                }
            }
        }
    }

    @Composable
    private fun FontChoiceControls(
        choice: FontChoice,
        onChoiceChange: (FontChoice) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            ) {
                FontChoice.entries.forEach { option ->
                    SegmentedButton(
                        selected = option == choice,
                        onClick = { onChoiceChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = option.ordinal,
                            count = FontChoice.entries.size,
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = LINE_GREEN,
                            activeContentColor = NIGHT,
                            activeBorderColor = LINE_GREEN,
                            inactiveContainerColor = NIGHT,
                            inactiveContentColor = Color.White,
                            inactiveBorderColor = OUTLINE_GRAY,
                        ),
                    ) {
                        Text(option.label)
                    }
                }
            }
            Text(
                text = choice.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = CAPTION_GRAY,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    companion object {
        private val NIGHT = Color(0xFF0F1A2B)
        private val LINE_GREEN = Color(0xFF80C241)
        private val OUTLINE_GRAY = Color(0xFF5B6B80)
        private val CAPTION_GRAY = Color(0xFF9FB3C8)
    }
}

private data class StationSignContent(
    val file: RiveFile,
    val japaneseFont: FontAsset,
    val koreanFont: FontAsset,
    val appFontsVmi: ViewModelInstance,
    val systemFontsVmi: ViewModelInstance,
) {
    fun showStation(index: Int) {
        val station = Station.LOOP[index]
        val previous = Station.LOOP[(index - 1).mod(Station.LOOP.size)]
        val next = Station.LOOP[(index + 1).mod(Station.LOOP.size)]
        for (vmi in listOf(appFontsVmi, systemFontsVmi)) {
            vmi.setString("number", (index + 1).toString().padStart(2, '0'))
            vmi.setString("kanji", station.kanji)
            vmi.setString("kana", station.kana)
            vmi.setString("romaji", station.romaji)
            vmi.setString("korean", station.korean)
            vmi.setString("previousKanji", previous.kanji)
            vmi.setString("previousRomaji", previous.romaji)
            vmi.setString("nextKanji", next.kanji)
            vmi.setString("nextRomaji", next.romaji)
        }
    }
}

private data class Station(
    val kanji: String,
    val kana: String,
    val romaji: String,
    val korean: String,
) {
    companion object {
        val LOOP = listOf(
            Station(kanji = "東京", kana = "とうきょう", romaji = "Tokyo", korean = "도쿄"),
            Station(kanji = "神田", kana = "かんだ", romaji = "Kanda", korean = "간다"),
            Station(kanji = "秋葉原", kana = "あきはばら", romaji = "Akihabara", korean = "아키하바라"),
            Station(kanji = "御徒町", kana = "おかちまち", romaji = "Okachimachi", korean = "오카치마치"),
            Station(kanji = "上野", kana = "うえの", romaji = "Ueno", korean = "우에노"),
            Station(kanji = "鶯谷", kana = "うぐいすだに", romaji = "Uguisudani", korean = "우구이스다니"),
            Station(kanji = "日暮里", kana = "にっぽり", romaji = "Nippori", korean = "닛포리"),
            Station(kanji = "西日暮里", kana = "にしにっぽり", romaji = "Nishi-Nippori", korean = "니시닛포리"),
            Station(kanji = "田端", kana = "たばた", romaji = "Tabata", korean = "다바타"),
            Station(kanji = "駒込", kana = "こまごめ", romaji = "Komagome", korean = "고마고메"),
            Station(kanji = "巣鴨", kana = "すがも", romaji = "Sugamo", korean = "스가모"),
            Station(kanji = "大塚", kana = "おおつか", romaji = "Otsuka", korean = "오쓰카"),
            Station(kanji = "池袋", kana = "いけぶくろ", romaji = "Ikebukuro", korean = "이케부쿠로"),
            Station(kanji = "目白", kana = "めじろ", romaji = "Mejiro", korean = "메지로"),
            Station(kanji = "高田馬場", kana = "たかだのばば", romaji = "Takadanobaba", korean = "다카다노바바"),
            Station(kanji = "新大久保", kana = "しんおおくぼ", romaji = "Shin-Okubo", korean = "신오쿠보"),
            Station(kanji = "新宿", kana = "しんじゅく", romaji = "Shinjuku", korean = "신주쿠"),
            Station(kanji = "代々木", kana = "よよぎ", romaji = "Yoyogi", korean = "요요기"),
            Station(kanji = "原宿", kana = "はらじゅく", romaji = "Harajuku", korean = "하라주쿠"),
            Station(kanji = "渋谷", kana = "しぶや", romaji = "Shibuya", korean = "시부야"),
            Station(kanji = "恵比寿", kana = "えびす", romaji = "Ebisu", korean = "에비스"),
            Station(kanji = "目黒", kana = "めぐろ", romaji = "Meguro", korean = "메구로"),
            Station(kanji = "五反田", kana = "ごたんだ", romaji = "Gotanda", korean = "고탄다"),
            Station(kanji = "大崎", kana = "おおさき", romaji = "Osaki", korean = "오사키"),
            Station(kanji = "品川", kana = "しながわ", romaji = "Shinagawa", korean = "시나가와"),
            Station(
                kanji = "高輪ゲートウェイ",
                kana = "たかなわゲートウェイ",
                romaji = "Takanawa Gateway",
                korean = "다카나와 게이트웨이",
            ),
            Station(kanji = "田町", kana = "たまち", romaji = "Tamachi", korean = "다마치"),
            Station(kanji = "浜松町", kana = "はままつちょう", romaji = "Hamamatsucho", korean = "하마마쓰초"),
            Station(kanji = "新橋", kana = "しんばし", romaji = "Shimbashi", korean = "신바시"),
            Station(kanji = "有楽町", kana = "ゆうらくちょう", romaji = "Yurakucho", korean = "유라쿠초"),
        )
    }
}

/** Where the sign's Japanese and Korean glyphs come from. */
private enum class FontChoice(val label: String, val description: String) {
    App("App fonts", "Japanese: M PLUS Rounded 1c · Korean: Jua\nBound by the app at runtime"),
    System(
        "System fonts",
        "Japanese and Korean: Android system fonts\nNo fonts are bound, so the text falls back"
    ),
}

@Composable
private fun rememberRawFont(@RawRes resourceId: Int, riveWorker: RiveWorker): Result<FontAsset> {
    val resources = LocalResources.current
    return produceState<Result<ByteArray>>(Result.Loading, resourceId) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(resourceId).use { Result.Success(it.readBytes()) }
        }
    }.value.andThen { bytes -> rememberFont(riveWorker, bytes) }
}
