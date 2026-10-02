package app.liora.core.designsystem.illustration

import androidx.compose.ui.graphics.Path

internal val Illustration.layers: List<Layer>
    get() =
        when (this) {
            Illustration.Routines -> RoutinesScene
            Illustration.Workout -> WorkoutScene
            Illustration.Calendar -> CalendarScene
            Illustration.Chart -> ChartScene
            Illustration.Search -> SearchScene
            Illustration.Exercises -> ExercisesScene
            Illustration.Body -> BodyScene
            Illustration.Photos -> PhotosScene
            Illustration.Gym -> GymScene
            Illustration.UnknownFile -> UnknownFileScene
            Illustration.Done -> DoneScene
        }

/** A strip of confetti at ([x], [y]), turned by [degrees]. */
private fun confetto(
    x: Float,
    y: Float,
    degrees: Float,
): Path =
    rect(
        x - CONFETTO_HALF_WIDTH,
        y - CONFETTO_HALF_LENGTH,
        x + CONFETTO_HALF_WIDTH,
        y + CONFETTO_HALF_LENGTH,
        CONFETTO_HALF_WIDTH,
    ).rotated(degrees, x, y)

private const val CONFETTO_HALF_WIDTH = 2.5f
private const val CONFETTO_HALF_LENGTH = 6f

/** A weight plate's three grips: arcs [radius] from its middle, evenly around, one at the top. */
private fun grips(
    x: Float,
    y: Float,
    radius: Float,
): List<Path> = GRIP_STARTS.map { start -> arc(x, y, radius, start, GRIP_SWEEP) }

private val GRIP_STARTS = listOf(-125f, -5f, 115f)
private const val GRIP_SWEEP = 70f

private val RoutinesScene =
    scene {
        // A second routine behind, tilted.
        fill(Tone.Line, rect(70f, 38f, 170f, 100f, 14f).rotated(-9f, 120f, 69f))
        fill(Tone.Paper, rect(70f, 52f, 178f, 122f, 14f))
        fill(Tone.Accent, rect(84f, 65f, 134f, 72f, 3.5f))
        listOf(85f to 150f, 97f to 140f, 109f to 146f).forEach { (y, end) ->
            fill(Tone.Line, circle(88f, y, 4f))
            fill(Tone.Line, rect(97f, y - 3f, end, y + 3f, 3f))
        }
        fill(Tone.Accent, circle(174f, 116f, 17f))
        val play = svg("M 169.5 107.5 L 169.5 124.5 L 183.5 116 Z")
        fill(Tone.OnAccent, play)
        stroke(Tone.OnAccent, 3f, play)
        fill(Tone.Accent, sparkle(56f, 44f, 8f))
        fill(Tone.Soft, sparkle(196f, 52f, 6f))
        fill(Tone.Soft, circle(206f, 84f, 3f))
        stroke(Tone.Soft, 2f, circle(50f, 108f, 4.5f))
    }

private val WorkoutScene =
    scene {
        fill(Tone.Shadow, oval(120f, 131f, 74f, 5f))
        // The bar and its sleeves, then the plates on them: big ones outside, small ones inside.
        fill(Tone.Paper, rect(48f, 86f, 192f, 92f, 3f))
        fill(Tone.Paper, rect(44f, 83.5f, 68f, 94.5f, 4f))
        fill(Tone.Paper, rect(172f, 83.5f, 196f, 94.5f, 4f))
        fill(Tone.Accent, rect(62f, 50f, 76f, 128f, 6f))
        fill(Tone.Accent, rect(164f, 50f, 178f, 128f, 6f))
        fill(Tone.Soft, rect(78f, 62f, 88f, 116f, 5f))
        fill(Tone.Soft, rect(152f, 62f, 162f, 116f, 5f))
        fill(Tone.Accent, rect(90f, 82f, 96f, 96f, 2f))
        fill(Tone.Accent, rect(144f, 82f, 150f, 96f, 2f))
        // Add the first exercise.
        fill(Tone.Paper, circle(120f, 50f, 15f))
        fill(Tone.Accent, plus(120f, 50f, 7f, 3.5f))
        fill(Tone.Accent, sparkle(190f, 36f, 8f))
        fill(Tone.Soft, sparkle(58f, 34f, 6f))
        fill(Tone.Soft, circle(204f, 66f, 3f))
        stroke(Tone.Soft, 2f, circle(44f, 60f, 4.5f))
    }

private val CalendarScene =
    scene {
        fill(Tone.Paper, rect(70f, 38f, 170f, 128f, 14f))
        fill(Tone.Accent, tab(70f, 38f, 170f, 60f, 14f))
        fill(Tone.Soft, rect(92f, 28f, 99f, 47f, 3.5f))
        fill(Tone.Soft, rect(141f, 28f, 148f, 47f, 3.5f))
        for (y in listOf(76f, 93f, 110f)) {
            for (x in listOf(88f, 104f, 120f, 136f, 152f)) {
                if (x != 136f || y != 93f) fill(Tone.Line, rect(x - 4.5f, y - 4.5f, x + 4.5f, y + 4.5f, 3f))
            }
        }
        // The day trained.
        fill(Tone.Accent, circle(136f, 93f, 9.5f))
        stroke(Tone.OnAccent, 2.8f, svg("M 131.5 93.5 L 134.8 96.6 L 140.8 89.8"))
        fill(Tone.Accent, sparkle(186f, 40f, 8f))
        fill(Tone.Soft, sparkle(54f, 62f, 6f))
        fill(Tone.Soft, circle(190f, 112f, 3f))
        stroke(Tone.Soft, 2f, circle(52f, 118f, 4.5f))
    }

private val ChartScene =
    scene {
        fill(Tone.Paper, rect(60f, 40f, 180f, 126f, 14f))
        listOf(82f to 98f, 101f to 90f, 120f to 94f, 139f to 80f, 158f to 72f).forEach { (x, top) ->
            fill(Tone.Line, rect(x - 5.5f, top, x + 5.5f, 112f, 4f))
        }
        stroke(Tone.Accent, 4.5f, svg("M 82 84 L 101 74 L 120 79 L 139 62 L 158 50"))
        listOf(82f to 84f, 101f to 74f, 120f to 79f, 139f to 62f).forEach { (x, y) ->
            fill(Tone.Paper, circle(x, y, 4f))
            stroke(Tone.Accent, 2.5f, circle(x, y, 4f))
        }
        // A flag where the line arrives.
        stroke(Tone.Accent, 3f, svg("M 158 50 L 158 22"))
        val pennant = svg("M 158 21 L 180 27.5 L 158 34 Z")
        fill(Tone.Accent, pennant)
        stroke(Tone.Accent, 2f, pennant)
        fill(Tone.Accent, circle(158f, 50f, 5.5f))
        fill(Tone.Accent, sparkle(46f, 54f, 6f))
        fill(Tone.Soft, sparkle(198f, 64f, 6f))
        fill(Tone.Soft, circle(48f, 112f, 3f))
        stroke(Tone.Soft, 2f, circle(196f, 116f, 4.5f))
    }

private val SearchScene =
    scene {
        fill(Tone.Shadow, oval(122f, 132f, 50f, 4.5f))
        // The handle first, so the rim covers its end.
        stroke(Tone.Accent, 13f, svg("M 133 97 L 160 124"))
        fill(Tone.Accent, circle(110f, 74f, 36f))
        fill(Tone.Paper, circle(110f, 74f, 27f))
        stroke(
            Tone.Soft,
            6.5f,
            svg(
                "M 100.5 66 C 100.5 59.5 105 55.5 110.5 55.5 C 116 55.5 120 59.5 120 64.5 " +
                    "C 120 70.5 113 72 110.5 77.5 L 110.5 81",
            ),
        )
        fill(Tone.Soft, circle(110.5f, 91f, 4f))
        fill(Tone.Accent, sparkle(172f, 44f, 8f))
        fill(Tone.Soft, sparkle(58f, 40f, 6f))
        fill(Tone.Soft, circle(186f, 80f, 3f))
        stroke(Tone.Soft, 2f, circle(56f, 110f, 4.5f))
    }

private val ExercisesScene =
    scene {
        fill(Tone.Shadow, oval(124f, 131f, 70f, 5f))
        // A kettlebell: a wide handle over a round body, flat underneath.
        stroke(Tone.Accent, 9f, svg("M 80 80 C 70 66 72 40 100 40 C 128 40 130 66 120 80"))
        fill(
            Tone.Accent,
            svg(
                "M 100 64 C 120 64 132 80 132 98 C 132 110 127 119 121 126 Q 120 128 117 128 L 83 128 " +
                    "Q 80 128 79 126 C 73 119 68 110 68 98 C 68 80 80 64 100 64 Z",
            ),
        )
        fill(Tone.Soft, circle(85f, 88f, 5f))
        // A dumbbell lying beside it.
        fill(Tone.Paper, rect(156f, 113f, 180f, 119f, 3f))
        fill(Tone.Soft, rect(140f, 103f, 150f, 129f, 5f))
        fill(Tone.Soft, rect(186f, 103f, 196f, 129f, 5f))
        fill(Tone.Soft, rect(149f, 107f, 157f, 125f, 3f))
        fill(Tone.Soft, rect(179f, 107f, 187f, 125f, 3f))
        fill(Tone.Accent, sparkle(150f, 52f, 8f))
        fill(Tone.Soft, sparkle(54f, 46f, 6f))
        fill(Tone.Soft, circle(176f, 78f, 3f))
        stroke(Tone.Soft, 2f, circle(196f, 58f, 4.5f))
    }

// The sole, then the toes, the big toe on the inside, turned out a little.
private val LeftFootprint: List<Path> =
    listOf(
        oval(104f, 102f, 8.5f, 15f),
        circle(108f, 81f, 3.6f),
        circle(102.5f, 80f, 2.8f),
        circle(98f, 82f, 2.4f),
        circle(94.8f, 85.5f, 2f),
    ).map { it.rotated(-6f, 104f, 100f) }

private val BodyScene =
    scene {
        // A bathroom scale from above: its display, and footprints where to stand.
        fill(Tone.Paper, rect(72f, 36f, 168f, 130f, 24f))
        fill(Tone.Accent, rect(102f, 47f, 138f, 63f, 5f))
        fill(Tone.OnAccent, rect(108f, 53f, 117f, 57f, 2f))
        fill(Tone.OnAccent, rect(121f, 53f, 132f, 57f, 2f))
        LeftFootprint.forEach { part ->
            fill(Tone.Soft, part)
            fill(Tone.Soft, part.mirrored())
        }
        fill(Tone.Accent, sparkle(188f, 46f, 8f))
        fill(Tone.Soft, sparkle(52f, 58f, 6f))
        fill(Tone.Soft, circle(192f, 110f, 3f))
        stroke(Tone.Soft, 2f, circle(54f, 116f, 4.5f))
    }

private val PhotosScene =
    scene {
        // Two instant photos of the same pose, the older one behind.
        polaroid(
            frame = rect(66f, 42f, 130f, 126f, 8f),
            photo = rect(72f, 48f, 124f, 104f, 4f),
            head = circle(98f, 68f, 8f),
            shoulders = svg("M 81 104 C 81 89 89 82 98 82 C 107 82 115 89 115 104 Z"),
            caption = rect(80f, 112f, 104f, 116f, 2f),
            person = Tone.Soft,
            turn = Turn(-10f, 98f, 84f),
        )
        polaroid(
            frame = rect(112f, 46f, 176f, 130f, 8f),
            photo = rect(118f, 52f, 170f, 108f, 4f),
            head = circle(144f, 72f, 8.5f),
            shoulders = svg("M 126 108 C 126 92 135 86 144 86 C 153 86 162 92 162 108 Z"),
            caption = rect(126f, 116f, 152f, 120f, 2f),
            person = Tone.Accent,
            turn = Turn(8f, 144f, 88f),
        )
        fill(Tone.Accent, sparkle(56f, 40f, 7f))
        fill(Tone.Soft, sparkle(194f, 40f, 6f))
        fill(Tone.Soft, circle(196f, 122f, 3f))
        stroke(Tone.Soft, 2f, circle(46f, 108f, 4.5f))
    }

private class Turn(
    val degrees: Float,
    val x: Float,
    val y: Float,
)

private fun SceneBuilder.polaroid(
    frame: Path,
    photo: Path,
    head: Path,
    shoulders: Path,
    caption: Path,
    person: Tone,
    turn: Turn,
) {
    fun Path.turned() = rotated(turn.degrees, turn.x, turn.y)
    fill(Tone.Paper, frame.turned())
    fill(Tone.Line, photo.turned())
    fill(person, head.turned())
    fill(person, shoulders.turned())
    fill(Tone.Line, caption.turned())
}

private val GymScene =
    scene {
        fill(Tone.Shadow, oval(124f, 130f, 66f, 5f))
        // A big plate standing, a small one leaning against it, each with three grips cut through it.
        fill(Tone.Accent, circle(102f, 86f, 42f))
        grips(102f, 86f, 25f).forEach { stroke(Tone.Wash, 8.5f, it) }
        fill(Tone.Paper, circle(102f, 86f, 10f))
        fill(Tone.Accent, circle(102f, 86f, 4.5f))
        fill(Tone.Paper, circle(154f, 104f, 26f))
        grips(154f, 104f, 15.5f).forEach { stroke(Tone.Line, 5.5f, it) }
        fill(Tone.Soft, circle(154f, 104f, 6.5f))
        fill(Tone.Paper, circle(154f, 104f, 2.8f))
        fill(Tone.Accent, sparkle(170f, 50f, 8f))
        fill(Tone.Soft, sparkle(56f, 44f, 6f))
        fill(Tone.Soft, circle(190f, 72f, 3f))
        stroke(Tone.Soft, 2f, circle(52f, 112f, 4.5f))
    }

private val UnknownFileScene =
    scene {
        fill(
            Tone.Paper,
            svg(
                "M 92 32 L 140 32 L 166 58 L 166 122 C 166 127 163 130 158 130 L 92 130 " +
                    "C 87 130 84 127 84 122 L 84 40 C 84 35 87 32 92 32 Z",
            ),
        )
        // The folded corner.
        fill(Tone.Line, svg("M 140 32 L 140 50 C 140 55 143 58 148 58 L 166 58 Z"))
        listOf(70f to 150f, 84f to 140f, 98f to 146f, 112f to 124f).forEach { (y, end) ->
            fill(Tone.Line, rect(98f, y, end, y + 6f, 3f))
        }
        fill(Tone.Accent, circle(166f, 114f, 18f))
        stroke(
            Tone.OnAccent,
            3.6f,
            svg(
                "M 159.5 108.5 C 159.5 104.5 162.5 102 166 102 C 169.5 102 172.5 104.5 172.5 108 " +
                    "C 172.5 112 167 113 166 117 L 166 118.5",
            ),
        )
        fill(Tone.OnAccent, circle(166f, 125f, 2.3f))
        fill(Tone.Accent, sparkle(188f, 46f, 7f))
        fill(Tone.Soft, sparkle(64f, 50f, 6f))
        fill(Tone.Soft, circle(68f, 112f, 3f))
        stroke(Tone.Soft, 2f, circle(196f, 82f, 4f))
    }

private val DoneScene =
    scene {
        fill(Tone.Accent, circle(120f, 82f, 34f))
        stroke(Tone.OnAccent, 8f, svg("M 104 83 L 115 94 L 137 71"))
        listOf(
            Triple(70f, 44f, 30f),
            Triple(198f, 100f, 65f),
            Triple(82f, 130f, 40f),
            Triple(150f, 22f, 15f),
        ).forEach { (x, y, degrees) -> fill(Tone.Accent, confetto(x, y, degrees)) }
        listOf(
            Triple(172f, 34f, -25f),
            Triple(54f, 108f, -50f),
            Triple(98f, 24f, -15f),
            Triple(184f, 128f, 20f),
        ).forEach { (x, y, degrees) -> fill(Tone.Soft, confetto(x, y, degrees)) }
        fill(Tone.Accent, sparkle(186f, 70f, 7f))
        fill(Tone.Soft, sparkle(60f, 76f, 6f))
        fill(Tone.Soft, circle(208f, 46f, 3f))
        fill(Tone.Accent, circle(40f, 98f, 3f))
        fill(Tone.Soft, circle(156f, 138f, 3f))
    }
