package com.dashwroom.f1telemetry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dashwroom.f1telemetry.ui.adaptive.Hinge
import com.dashwroom.f1telemetry.ui.adaptive.PaneLayoutInfo
import com.dashwroom.f1telemetry.ui.adaptive.PaneMode
import com.dashwroom.f1telemetry.ui.adaptive.TwoPane
import com.dashwroom.f1telemetry.ui.preview.PreviewData
import com.dashwroom.f1telemetry.ui.screens.analysis.AnalysisContent
import com.dashwroom.f1telemetry.ui.screens.car.CarContent
import com.dashwroom.f1telemetry.ui.screens.car.CarUiState
import com.dashwroom.f1telemetry.ui.screens.qualifying.Knockout
import com.dashwroom.f1telemetry.ui.screens.qualifying.QualifyingContent
import com.dashwroom.f1telemetry.ui.screens.qualifying.QualifyingUiState
import com.dashwroom.f1telemetry.ui.screens.race.PitStrategy
import com.dashwroom.f1telemetry.ui.screens.race.RaceContent
import com.dashwroom.f1telemetry.ui.screens.race.RaceUiState
import com.dashwroom.f1telemetry.ui.theme.DashwroomTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Foldable hinge handling and basic accessibility guarantees across the data screens. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.MediumTablet)
class AdaptiveAndA11yTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var density: Density

    private fun set(content: @Composable () -> Unit) = compose.setContent {
        density = LocalDensity.current
        DashwroomTheme { Box(Modifier.fillMaxSize()) { content() } }
    }

    @Test
    fun `vertical hinge splits the panes exactly at the fold`() {
        // A 40 px wide hinge at x = 900..940 px.
        set {
            TwoPane(
                first = { Box(Modifier.fillMaxSize().testTag("first")) },
                second = { Box(Modifier.fillMaxSize().testTag("second")) },
                hinge = Hinge(Rect(900f, 0f, 940f, 5_000f), vertical = true),
            )
        }
        val first = compose.onNodeWithTag("first").getBoundsInRoot()
        val second = compose.onNodeWithTag("second").getBoundsInRoot()
        with(density) {
            assertThat(first.right.toPx()).isWithin(1f).of(900f)
            assertThat(second.left.toPx()).isWithin(1f).of(940f)
        }
    }

    @Test
    fun `horizontal hinge stacks the panes around the fold`() {
        set {
            TwoPane(
                first = { Box(Modifier.fillMaxSize().testTag("first")) },
                second = { Box(Modifier.fillMaxSize().testTag("second")) },
                hinge = Hinge(Rect(0f, 700f, 5_000f, 730f), vertical = false),
            )
        }
        val first = compose.onNodeWithTag("first").getBoundsInRoot()
        val second = compose.onNodeWithTag("second").getBoundsInRoot()
        with(density) {
            assertThat(first.bottom.toPx()).isWithin(1f).of(700f)
            assertThat(second.top.toPx()).isWithin(1f).of(730f)
        }
    }

    @Test
    fun `without a hinge the split follows the weight`() {
        set {
            TwoPane(
                first = { Box(Modifier.fillMaxSize().testTag("first")) },
                second = { Box(Modifier.fillMaxSize().testTag("second")) },
                firstWeight = 0.5f,
                gap = 0.dp,
            )
        }
        val root = compose.onRoot().getBoundsInRoot()
        val first = compose.onNodeWithTag("first").getBoundsInRoot()
        assertThat(first.right.value).isWithin(1f).of(root.right.value / 2f)
    }

    @Test
    @Config(qualifiers = "w841dp-h673dp-land")
    fun `race in tabletop posture`() {
        set {
            // A half-folded device: 10 px hinge across the middle of the window.
            val mid = LocalWindowInfo.current.containerSize.height / 2f
            val layout = PaneLayoutInfo(PaneMode.SINGLE, Hinge(Rect(0f, mid - 5f, 100_000f, mid + 5f), vertical = false))
            Surface(color = MaterialTheme.colorScheme.background) {
                RaceContent(raceState(), PreviewData.hot(), PreviewData.frame(), {}, {}, {}, layout = layout)
            }
        }
        compose.onRoot().captureRoboImage("screenshots/race_tabletop.png")
    }

    private fun raceState(): RaceUiState {
        val race = PreviewData.race()
        return RaceUiState(true, PreviewData.info(), race, PreviewData.history(), PreviewData.events(), pitAdvice = PitStrategy.compute(race, PreviewData.info()))
    }

    // ---- accessibility ----

    @Test
    fun `race controls are labelled and large enough`() {
        set { RaceContent(raceState(), PreviewData.hot(), PreviewData.frame(), {}, {}, {}) }
        assertClickablesAccessible()
    }

    @Test
    fun `qualifying controls are labelled and large enough`() {
        val race = PreviewData.race(race = false)
        val info = PreviewData.info(race = false).copy(sessionType = 5)
        val ranked = QualifyingUiState.rank(race.drivers)
        set { QualifyingContent(QualifyingUiState(true, info, race, ranked.toImmutableList(), PreviewData.history(), Knockout.of(info, ranked.size)), PreviewData.hot(), PreviewData.frame(), {}, {}, {}) }
        assertClickablesAccessible()
    }

    @Test
    fun `analysis controls are labelled and large enough`() {
        set { AnalysisContent(PreviewData.analysis(), {}, {}, {}, {}, {}) }
        assertClickablesAccessible()
    }

    @Test
    fun `car screen has described visuals`() {
        set { CarContent(CarUiState(true, PreviewData.info(), PreviewData.car()), onOpenConnect = {}) }
        val described = compose.onAllNodesWithDescription()
        assertThat(described).contains("Tyre temperature heat map")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithDescription(): List<String> =
        onAllNodes(androidx.compose.ui.test.hasContentDescription("", substring = true))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    /** Every clickable node has a label (text or description) and a ≥ 48 dp touch target. */
    private fun assertClickablesAccessible() {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertThat(nodes).isNotEmpty()
        for (node in nodes) {
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)
            assertWithMessage("clickable without a label: ${node.config}").that(label.joinToString("").isNotBlank()).isTrue()
            val touch = node.touchBoundsInRoot
            val minPx = with(density) { 47.5.dp.toPx() }
            assertWithMessage("touch target too small (${touch.height} px high): $label").that(touch.height).isAtLeast(minPx)
        }
    }
}
