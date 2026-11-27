package com.meysam.divanemtiaz

/** How a card's edge is drawn. */
enum class ThemeFrame {
    /** Gold gradient frame, inner hairline and corner jewels (the classic royal card). */
    ORNATE,
    /** Thin metal line with an optional inner double line. */
    HAIRLINE,
    /** No visible metal: a soft border and a tinted drop shadow. */
    SOFT,
    /** Translucent glass with a coloured, glowing edge. */
    GLOW
}

/** How buttons and chips are shaped and lit. */
enum class ThemeButton {
    /** Beveled 3D face on a darker edge (the classic royal button). */
    BEVEL,
    /** Flat face with a soft coloured shadow. */
    FLAT,
    /** Matte face, thin border and a deep shadow. */
    MATTE,
    /** Rich gradient face with a fine top gloss. */
    GRADIENT,
    /** Translucent glass with a neon glow. */
    GLASS
}

/** Texture drawn over the page background. */
enum class ThemePattern { KHATAM, LATTICE, DIAMOND, GIRIH, ARABESQUE, DOTS, NEON }

/** A radial light on the page background; positions and radius are fractions of the page width/height. */
class ThemeGlow(val x: Float, val y: Float, val radius: Float, val color: Int)

/**
 * One visual style of the app. Every widget reads its colours, shapes, lights and textures from the active
 * spec, so screens and game logic stay the same while the whole look changes.
 */
class ThemeSpec(
    val id: String,
    val title: String,
    val latin: String,
    val summary: String,
    val light: Boolean,
    // Page
    val bg: IntArray,
    val glows: List<ThemeGlow>,
    val vignette: Int,
    val pattern: ThemePattern,
    val patternColor: Int,
    // Cards
    val surface: Int, val surfaceLow: Int,
    val raised: Int, val raisedLow: Int,
    val flat: Int, val flatLow: Int,
    val border: Int, val borderLow: Int,
    val inner: Int,
    val jewels: Boolean,
    val frame: ThemeFrame,
    val frameWidth: Float,
    val shadow: Int,
    val shadowDepth: Float,
    val gloss: Float,
    val radius: Float,
    // Accent (headings, icons, selection) and the metal of frames, medallions and seals
    val accent: Int, val accentText: Int, val accentDeep: Int, val accentShadow: Int,
    val metalLight: Int, val metal: Int, val metalDeep: Int, val metalShadow: Int,
    val success: Int, val successText: Int, val successDeep: Int,
    val danger: Int, val dangerText: Int, val dangerDeep: Int,
    val text: Int, val muted: Int, val dim: Int, val titleColor: Int,
    val onAccent: Int,
    val scrim: Int,
    val lapis: Int, val lapisLight: Int,
    // Buttons
    val button: ThemeButton,
    val primary: IntArray, val primaryHorizontal: Boolean, val primaryText: Int, val primaryEdge: Int,
    val secondary: IntArray, val secondaryText: Int, val secondaryBorder: Int,
    val chip: IntArray, val chipText: Int, val chipBorder: Int,
    val successFill: IntArray, val successOn: Int,
    val dangerFill: IntArray, val dangerOn: Int,
    val buttonRadius: Float,
    val glow: Boolean,
    // Timers
    val ring: IntArray,
    val ringTrack: Int,
    /** Colours of the theme's swatch in the theme picker. */
    val swatch: IntArray
)

private fun c(v: Long): Int = v.toInt()

/** The six selectable styles; the user picks one on the first launch and can change it in the settings. */
object LegacyThemes {
    val COBALT = ThemeSpec(
        id = "cobalt", title = "وایت کبالت", latin = "White Cobalt", summary = "روشن، تمیز و دقیق؛ سفید با آبی کبالت",
        light = true,
        bg = intArrayOf(c(0xFFF6F8FC), c(0xFFEFF3FA), c(0xFFE8EEF8)),
        glows = listOf(ThemeGlow(0.92f, -0.04f, 0.8f, c(0x262D63E6)), ThemeGlow(0.05f, 0.75f, 0.7f, c(0x147FA6FF))),
        vignette = 0, pattern = ThemePattern.LATTICE, patternColor = c(0xFF2D63E6),
        surface = c(0xFFFFFFFF), surfaceLow = c(0xFFFBFCFF),
        raised = c(0xFFFFFFFF), raisedLow = c(0xFFF1F5FE),
        flat = c(0xFFF8FAFE), flatLow = c(0xFFF3F6FC),
        border = c(0xFFDDE5F3), borderLow = c(0xFFD2DCEE), inner = 0, jewels = false,
        frame = ThemeFrame.SOFT, frameWidth = 1f, shadow = c(0x2E2D4A9A), shadowDepth = 3.2f, gloss = 0f, radius = 1.1f,
        accent = c(0xFF2D63E6), accentText = c(0xFF1E4FC8), accentDeep = c(0xFF1A3FA0), accentShadow = c(0xFF153384),
        metalLight = c(0xFFEFF3FC), metal = c(0xFFB9C8E8), metalDeep = c(0xFF7189C2), metalShadow = c(0xFF41558C),
        success = c(0xFF12A891), successText = c(0xFF0B7F70), successDeep = c(0xFF07695D),
        danger = c(0xFFE0475A), dangerText = c(0xFFC7303F), dangerDeep = c(0xFF9E1F2E),
        text = c(0xFF141B2D), muted = c(0xFF5A6478), dim = c(0xFF8D96A8), titleColor = c(0xFF101828),
        onAccent = c(0xFFFFFFFF), scrim = c(0xFFF4F7FC), lapis = c(0xFFDCE6FB), lapisLight = c(0xFFC8D7F7),
        button = ThemeButton.FLAT,
        primary = intArrayOf(c(0xFF2D63E6), c(0xFF4F80FA), c(0xFF6B95FF)), primaryHorizontal = true, primaryText = c(0xFFFFFFFF), primaryEdge = c(0x552D63E6),
        secondary = intArrayOf(c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFFF6F8FD)), secondaryText = c(0xFF1E4FC8), secondaryBorder = c(0xFFD3DDEE),
        chip = intArrayOf(c(0xFFF3F6FB), c(0xFFF1F4FA), c(0xFFEEF2F9)), chipText = c(0xFF2B3550), chipBorder = c(0xFFDCE3EF),
        successFill = intArrayOf(c(0xFF2D63E6), c(0xFF3C6FEF), c(0xFF4F80FA)), successOn = c(0xFFFFFFFF),
        dangerFill = intArrayOf(c(0xFFEF5466), c(0xFFE24A5C), c(0xFFD63B4E)), dangerOn = c(0xFFFFFFFF),
        buttonRadius = 0.95f, glow = false,
        ring = intArrayOf(c(0xFF2D63E6), c(0xFF4F80FA), c(0xFF8DB2FF)), ringTrack = c(0x1F2D63E6),
        swatch = intArrayOf(c(0xFFF4F7FC), c(0xFF2D63E6), c(0xFFDDE5F3))
    )

    val COPPER = ThemeSpec(
        id = "copper", title = "گرافیت کوپر", latin = "Graphite Copper", summary = "تیرهٔ گرافیتی و مسی؛ رسمی، مات و سنگین",
        light = false,
        bg = intArrayOf(c(0xFF1D2023), c(0xFF17191C), c(0xFF111315)),
        glows = listOf(ThemeGlow(0.85f, -0.05f, 0.85f, c(0x26D9A27C)), ThemeGlow(0.1f, 1.0f, 0.8f, c(0x1A8FB3AA))),
        vignette = c(0x66000000), pattern = ThemePattern.DIAMOND, patternColor = c(0xFFD9A27C),
        surface = c(0xFF2B3034), surfaceLow = c(0xFF24282C),
        raised = c(0xFF32373C), raisedLow = c(0xFF262A2E),
        flat = c(0xFF262A2E), flatLow = c(0xFF202326),
        border = c(0x80E6B48E), borderLow = c(0x40A8785A), inner = 0, jewels = false,
        frame = ThemeFrame.HAIRLINE, frameWidth = 1f, shadow = c(0x8C000000), shadowDepth = 4.5f, gloss = 0.035f, radius = 1f,
        accent = c(0xFFD9A27C), accentText = c(0xFFF0C4A2), accentDeep = c(0xFFA8714E), accentShadow = c(0xFF6B4530),
        metalLight = c(0xFFF6D8C0), metal = c(0xFFD49B74), metalDeep = c(0xFF9C6646), metalShadow = c(0xFF5A3826),
        success = c(0xFF8FC1B5), successText = c(0xFFB9DED5), successDeep = c(0xFF4F7D72),
        danger = c(0xFFE07A6E), dangerText = c(0xFFF2A79D), dangerDeep = c(0xFF8A3A32),
        text = c(0xFFF1ECE6), muted = c(0xFFB2ABA3), dim = c(0xFF7F7972), titleColor = c(0xFFF6EFE8),
        onAccent = c(0xFF1A1C1E), scrim = c(0xFF111315), lapis = c(0xFF2F3438), lapisLight = c(0xFF3A4045),
        button = ThemeButton.MATTE,
        primary = intArrayOf(c(0xFFE6B48E), c(0xFFC9B19C), c(0xFFA9C1B8)), primaryHorizontal = true, primaryText = c(0xFF1A1C1E), primaryEdge = c(0xFF4A3426),
        secondary = intArrayOf(c(0xFF30353A), c(0xFF2A2E32), c(0xFF25292D)), secondaryText = c(0xFFF0C4A2), secondaryBorder = c(0x73D9A27C),
        chip = intArrayOf(c(0xFF2C3135), c(0xFF282C30), c(0xFF24282B)), chipText = c(0xFFE9E3DC), chipBorder = c(0x40D9A27C),
        successFill = intArrayOf(c(0xFFE6B48E), c(0xFFD9A27C), c(0xFFC48C66)), successOn = c(0xFF1A1C1E),
        dangerFill = intArrayOf(c(0xFFE58B7F), c(0xFFD06A5E), c(0xFFB0544A)), dangerOn = c(0xFF1A1C1E),
        buttonRadius = 0.9f, glow = false,
        ring = intArrayOf(c(0xFFE6B48E), c(0xFFD9A27C), c(0xFFA9C1B8)), ringTrack = c(0x1FFFFFFF),
        swatch = intArrayOf(c(0xFF1D2023), c(0xFFD9A27C), c(0xFFA9C1B8))
    )

    val IRANIC = ThemeSpec(
        id = "iranic", title = "ایرانیک مینیمال", latin = "Iranic Minimal", summary = "کرم و استخوانی با سرمه‌ای و طلایی ملایم؛ اصیل و آرام",
        light = true,
        bg = intArrayOf(c(0xFFF7F0E3), c(0xFFF3EBDC), c(0xFFEDE3D1)),
        glows = listOf(ThemeGlow(0.5f, -0.08f, 0.9f, c(0x33E8C98A)), ThemeGlow(0.5f, 1.08f, 0.8f, c(0x141F2F4D))),
        vignette = 0, pattern = ThemePattern.GIRIH, patternColor = c(0xFF1F2F4D),
        surface = c(0xFFFDF9F2), surfaceLow = c(0xFFF8F1E4),
        raised = c(0xFFFEFAF3), raisedLow = c(0xFFF3E9D7),
        flat = c(0xFFFAF4E9), flatLow = c(0xFFF5EDDF),
        border = c(0xFFE3D4B9), borderLow = c(0xFFD5C2A1), inner = c(0x40C79A55), jewels = false,
        frame = ThemeFrame.HAIRLINE, frameWidth = 1.1f, shadow = c(0x24553C14), shadowDepth = 3f, gloss = 0f, radius = 0.95f,
        accent = c(0xFFB98A45), accentText = c(0xFF1F2F4D), accentDeep = c(0xFF8C6428), accentShadow = c(0xFF5E4219),
        metalLight = c(0xFFF6E3BA), metal = c(0xFFCBA466), metalDeep = c(0xFF96703A), metalShadow = c(0xFF5E4219),
        success = c(0xFF1E9C94), successText = c(0xFF15766F), successDeep = c(0xFF0F5C56),
        danger = c(0xFFB8443F), dangerText = c(0xFF9E2F2B), dangerDeep = c(0xFF7A1F1C),
        text = c(0xFF1C2433), muted = c(0xFF6A6457), dim = c(0xFF9A917F), titleColor = c(0xFF1A2540),
        onAccent = c(0xFFFFFFFF), scrim = c(0xFFF6EFE2), lapis = c(0xFFEFE4CF), lapisLight = c(0xFFE6D6B8),
        button = ThemeButton.GRADIENT,
        primary = intArrayOf(c(0xFF1F2F4D), c(0xFF6E6551), c(0xFFC79A55)), primaryHorizontal = true, primaryText = c(0xFFFFFFFF), primaryEdge = c(0x401F2F4D),
        secondary = intArrayOf(c(0xFFFDF9F2), c(0xFFFAF4E8), c(0xFFF6EEDF)), secondaryText = c(0xFF1F2F4D), secondaryBorder = c(0xFFD9C8A8),
        chip = intArrayOf(c(0xFFFAF4E8), c(0xFFF7F0E2), c(0xFFF3EADA)), chipText = c(0xFF2A3247), chipBorder = c(0xFFE0D2B8),
        successFill = intArrayOf(c(0xFF2B3F63), c(0xFF243655), c(0xFF1F2F4D)), successOn = c(0xFFFFFFFF),
        dangerFill = intArrayOf(c(0xFFC9524C), c(0xFFB8443F), c(0xFFA03733)), dangerOn = c(0xFFFFFFFF),
        buttonRadius = 0.9f, glow = false,
        ring = intArrayOf(c(0xFF1F2F4D), c(0xFF6E6551), c(0xFFC79A55)), ringTrack = c(0x1A1F2F4D),
        swatch = intArrayOf(c(0xFFF3EBDC), c(0xFF1F2F4D), c(0xFFC79A55))
    )

    val EMERALD = ThemeSpec(
        id = "emerald", title = "امرالد پریمیوم", latin = "Emerald Premium", summary = "سبز زمردی با طلایی و فیروزه‌ای؛ اشرافی و بازی‌محور",
        light = false,
        bg = intArrayOf(c(0xFF0A2019), c(0xFF0B241D), c(0xFF061511)),
        glows = listOf(ThemeGlow(0.5f, -0.06f, 0.9f, c(0x4026A07A)), ThemeGlow(0.5f, 1.05f, 0.85f, c(0x1FE3C27A))),
        vignette = c(0x80000000), pattern = ThemePattern.ARABESQUE, patternColor = c(0xFFE3C27A),
        surface = c(0xF5143A31), surfaceLow = c(0xF50F2C25),
        raised = c(0xFF184438), raisedLow = c(0xFF0E2A23),
        flat = c(0xEB12332B), flatLow = c(0xEB0E2822),
        border = c(0xFFF0D497), borderLow = c(0xFF8E6E2F), inner = c(0x40E3C27A), jewels = false,
        frame = ThemeFrame.HAIRLINE, frameWidth = 1.2f, shadow = c(0x7A000000), shadowDepth = 3.5f, gloss = 0.05f, radius = 1.05f,
        accent = c(0xFFE3C27A), accentText = c(0xFFF4DFA8), accentDeep = c(0xFFB08A3E), accentShadow = c(0xFF5E4519),
        metalLight = c(0xFFFBE3A6), metal = c(0xFFE6BE6A), metalDeep = c(0xFFB1832F), metalShadow = c(0xFF6E4A1A),
        success = c(0xFF5BC3A0), successText = c(0xFF9BE3C9), successDeep = c(0xFF1F7A5E),
        danger = c(0xFFE06A6A), dangerText = c(0xFFF5A3A3), dangerDeep = c(0xFF7A1F24),
        text = c(0xFFF2F0E6), muted = c(0xFFA9C2B8), dim = c(0xFF6F8E83), titleColor = c(0xFFF4DFA8),
        onAccent = c(0xFF0A1F1A), scrim = c(0xFF061511), lapis = c(0xFF164036), lapisLight = c(0xFF1E5546),
        button = ThemeButton.GRADIENT,
        primary = intArrayOf(c(0xFFEBCC8A), c(0xFFA9C690), c(0xFF5BC3A0)), primaryHorizontal = true, primaryText = c(0xFF0A1F1A), primaryEdge = c(0xFF3A3010),
        secondary = intArrayOf(c(0xFF1A473D), c(0xFF153B33), c(0xFF10302A)), secondaryText = c(0xFFF4DFA8), secondaryBorder = c(0x99E3C27A),
        chip = intArrayOf(c(0xFF163E35), c(0xFF13362E), c(0xFF10302A)), chipText = c(0xFFEDEBDF), chipBorder = c(0x59E3C27A),
        successFill = intArrayOf(c(0xFFF0D497), c(0xFFE3C27A), c(0xFFC9A55A)), successOn = c(0xFF0A1F1A),
        dangerFill = intArrayOf(c(0xFFE57B7B), c(0xFFD15C5E), c(0xFFB0444A)), dangerOn = c(0xFFFFFFFF),
        buttonRadius = 1f, glow = false,
        ring = intArrayOf(c(0xFFF0D497), c(0xFFE3C27A), c(0xFF5BC3A0)), ringTrack = c(0x1FFFFFFF),
        swatch = intArrayOf(c(0xFF0B241D), c(0xFFE3C27A), c(0xFF5BC3A0))
    )

    val IVORY = ThemeSpec(
        id = "ivory", title = "آیوری گلد", latin = "Ivory Gold Luxury", summary = "عاجی و سفید گرم با طلایی؛ مجلل، نرم و شفاف",
        light = true,
        bg = intArrayOf(c(0xFFFCF9F2), c(0xFFF8F1E4), c(0xFFF1E7D4)),
        glows = listOf(ThemeGlow(0.15f, -0.05f, 0.9f, c(0x99FFFFFF)), ThemeGlow(0.9f, 0.25f, 0.8f, c(0x26D9AE5C)), ThemeGlow(0.5f, 1.1f, 0.9f, c(0x1FB8892F))),
        vignette = 0, pattern = ThemePattern.DOTS, patternColor = c(0xFFB8892F),
        surface = c(0xFFFFFDF8), surfaceLow = c(0xFFFBF5E9),
        raised = c(0xFFFFFEFB), raisedLow = c(0xFFF8EFDD),
        flat = c(0xFFFDF9F1), flatLow = c(0xFFF9F2E5),
        border = c(0xFFEAD6AA), borderLow = c(0xFFD2B273), inner = c(0x30CFAE6B), jewels = false,
        frame = ThemeFrame.SOFT, frameWidth = 1.1f, shadow = c(0x338A6420), shadowDepth = 4f, gloss = 0.5f, radius = 1.15f,
        accent = c(0xFFC49A4A), accentText = c(0xFF8C6421), accentDeep = c(0xFF9A7230), accentShadow = c(0xFF6B4D1C),
        metalLight = c(0xFFFFF2CF), metal = c(0xFFDDB66A), metalDeep = c(0xFFAD8236), metalShadow = c(0xFF6E4A1A),
        success = c(0xFF2F9E8F), successText = c(0xFF1F7A6E), successDeep = c(0xFF16665B),
        danger = c(0xFFC8474F), dangerText = c(0xFFA8323A), dangerDeep = c(0xFF7E1F26),
        text = c(0xFF2A2218), muted = c(0xFF76695A), dim = c(0xFFA89A86), titleColor = c(0xFF2A2218),
        onAccent = c(0xFFFFFFFF), scrim = c(0xFFFBF7EE), lapis = c(0xFFF6ECD8), lapisLight = c(0xFFEEDDBA),
        button = ThemeButton.GRADIENT,
        primary = intArrayOf(c(0xFFC9A05A), c(0xFFAD812F), c(0xFF8F6A1E)), primaryHorizontal = false, primaryText = c(0xFFFFFFFF), primaryEdge = c(0x668A6420),
        secondary = intArrayOf(c(0xFFFFFEFA), c(0xFFFCF6EA), c(0xFFF8F0E0)), secondaryText = c(0xFF8C6421), secondaryBorder = c(0xFFDCC08A),
        chip = intArrayOf(c(0xFFFCF7ED), c(0xFFF8F1E3), c(0xFFF5ECDA)), chipText = c(0xFF3A3024), chipBorder = c(0xFFE6D3AE),
        successFill = intArrayOf(c(0xFFC9A05A), c(0xFFAD812F), c(0xFF8F6A1E)), successOn = c(0xFFFFFFFF),
        dangerFill = intArrayOf(c(0xFFDD5D64), c(0xFFCB4B53), c(0xFFB83C45)), dangerOn = c(0xFFFFFFFF),
        buttonRadius = 1f, glow = false,
        ring = intArrayOf(c(0xFF8C6421), c(0xFFB8892F), c(0xFFD9B05F)), ringTrack = c(0x29B8892F),
        swatch = intArrayOf(c(0xFFF8F1E4), c(0xFFC49A4A), c(0xFFFFFFFF))
    )

    val NEON = ThemeSpec(
        id = "neon", title = "نئون گلس", latin = "Neon Glass", summary = "بنفش، آبی و صورتی نئونی روی شیشه؛ پویا و جوان",
        light = false,
        bg = intArrayOf(c(0xFF140B2E), c(0xFF0F0A26), c(0xFF0A0819)),
        glows = listOf(ThemeGlow(0.12f, 0.08f, 0.75f, c(0x66A34DFF)), ThemeGlow(0.95f, 0.38f, 0.7f, c(0x4D3D7BFF)), ThemeGlow(0.45f, 1.02f, 0.8f, c(0x4DFF4FD8))),
        vignette = c(0x66000000), pattern = ThemePattern.NEON, patternColor = c(0xFFFFFFFF),
        surface = c(0x24FFFFFF), surfaceLow = c(0x14FFFFFF),
        raised = c(0x2EFFFFFF), raisedLow = c(0x17FFFFFF),
        flat = c(0x1AFFFFFF), flatLow = c(0x10FFFFFF),
        border = c(0xD9C08CFF), borderLow = c(0x995B8CFF), inner = c(0x1FFFFFFF), jewels = false,
        frame = ThemeFrame.GLOW, frameWidth = 1.2f, shadow = c(0x66000000), shadowDepth = 3f, gloss = 0.10f, radius = 1.2f,
        accent = c(0xFFB57CFF), accentText = c(0xFFDCC0FF), accentDeep = c(0xFF7B4FD6), accentShadow = c(0xFF3B2280),
        metalLight = c(0xFFEEE2FF), metal = c(0xFFB57CFF), metalDeep = c(0xFF6E52E0), metalShadow = c(0xFF2C1E6B),
        success = c(0xFF3EE6C8), successText = c(0xFF9BF5E6), successDeep = c(0xFF138C7B),
        danger = c(0xFFFF4F8B), dangerText = c(0xFFFF9EC0), dangerDeep = c(0xFF8A1C48),
        text = c(0xFFF4EFFF), muted = c(0xFFB8AED8), dim = c(0xFF8178A6), titleColor = c(0xFFFFFFFF),
        onAccent = c(0xFFFFFFFF), scrim = c(0xFF0A0819), lapis = c(0x552A1A66), lapisLight = c(0x773D2A99),
        button = ThemeButton.GLASS,
        primary = intArrayOf(c(0xFFC07CFF), c(0xFF9A7CFF), c(0xFF6E86FF)), primaryHorizontal = true, primaryText = c(0xFFFFFFFF), primaryEdge = c(0xFF8B5CFF),
        secondary = intArrayOf(c(0x2EFFFFFF), c(0x21FFFFFF), c(0x17FFFFFF)), secondaryText = c(0xFFE5D6FF), secondaryBorder = c(0x80B57CFF),
        chip = intArrayOf(c(0x21FFFFFF), c(0x17FFFFFF), c(0x12FFFFFF)), chipText = c(0xFFEDE7FF), chipBorder = c(0x4DB57CFF),
        successFill = intArrayOf(c(0xFFFF6FD8), c(0xFFC77CFF), c(0xFF8E7CFF)), successOn = c(0xFFFFFFFF),
        dangerFill = intArrayOf(c(0xFFFF6CA5), c(0xFFF24C8A), c(0xFFE0306E)), dangerOn = c(0xFFFFFFFF),
        buttonRadius = 1.2f, glow = true,
        ring = intArrayOf(c(0xFFFF5CD6), c(0xFFB57CFF), c(0xFF5B8CFF)), ringTrack = c(0x1FFFFFFF),
        swatch = intArrayOf(c(0xFF140B2E), c(0xFFB57CFF), c(0xFFFF5CD6))
    )

    val all: List<ThemeSpec> = listOf(COBALT, COPPER, IRANIC, EMERALD, IVORY, NEON)

    /** Shown until a style is chosen (the first launch) or when a stored style no longer exists. */
    val FALLBACK: ThemeSpec get() = EMERALD

    fun byId(id: String?): ThemeSpec = all.firstOrNull { it.id == id } ?: FALLBACK

    /** True once the user has picked one of the current styles. */
    fun isChosen(id: String?): Boolean = all.any { it.id == id }
}

/** Eighteen colour palettes. Geometry comes from the user's existing style and never changes on colour selection. */
object Themes {
    var geometryId:String="emerald"; private set
    private fun rgb(value:String)=java.lang.Long.parseLong(value,16).toInt() or 0xFF000000.toInt()
    private fun blend(a:Int,b:Int,t:Float):Int {
        fun part(shift:Int)=((((a ushr shift)and 255)*(1-t)+((b ushr shift)and 255)*t).toInt() shl shift)
        return 0xFF000000.toInt() or part(16) or part(8) or part(0)
    }
    private fun alpha(color:Int,value:Int)=(color and 0x00FFFFFF) or ((value and 255) shl 24)
    private fun palette(id:String,title:String,latin:String,light:Boolean,bg:String,surface:String,text:String,muted:String,accent:String,border:String):ThemeSpec {
        val shape=LegacyThemes.byId(geometryId)
        val b=rgb(bg);val card=rgb(surface);val ink=rgb(text);val quiet=rgb(muted);val brand=rgb(accent);val edge=rgb(border)
        val black=rgb("081722");val white=rgb("FFFFFF");val on=if(light)white else black
        val brandText=if(light)brand else blend(brand,white,.18f)
        val primary=intArrayOf(blend(brand,if(light)black else white,.04f),brand,blend(brand,if(light)black else white,.09f))
        val secondary=intArrayOf(card,blend(card,b,.12f),blend(card,b,.28f))
        val success=if(light)rgb("23715D")else rgb("87DFC2")
        val danger=if(light)rgb("AD354D")else rgb("FFADC0")
        return ThemeSpec(
            id=id,title=title,latin=latin,summary=if(light)"رنگ‌بندی روشن • $title" else "رنگ‌بندی تیره • $title",light=light,
            bg=intArrayOf(blend(b,card,.06f),b,blend(b,if(light)card else black,.18f)),
            glows=shape.glows.map{ThemeGlow(it.x,it.y,it.radius,alpha(brand,(it.color ushr 24)and 255))},
            vignette=alpha(if(light)b else black,(shape.vignette ushr 24)and 255),pattern=shape.pattern,patternColor=brand,
            surface=card,surfaceLow=blend(card,b,.18f),raised=blend(card,if(light)white else brand,.04f),raisedLow=blend(card,b,.12f),
            flat=blend(card,b,.12f),flatLow=blend(card,b,.26f),border=edge,borderLow=blend(edge,b,.28f),inner=alpha(brand,(shape.inner ushr 24)and 255),
            jewels=shape.jewels,frame=shape.frame,frameWidth=shape.frameWidth,shadow=alpha(if(light)brand else black,(shape.shadow ushr 24)and 255),
            shadowDepth=shape.shadowDepth,gloss=shape.gloss,radius=shape.radius,
            accent=brand,accentText=brandText,accentDeep=blend(brand,black,.23f),accentShadow=blend(brand,black,.53f),
            metalLight=blend(brand,white,.58f),metal=brand,metalDeep=blend(brand,black,.28f),metalShadow=blend(brand,black,.62f),
            success=success,successText=if(light)success else blend(success,white,.15f),successDeep=blend(success,black,.42f),
            danger=danger,dangerText=if(light)danger else blend(danger,white,.14f),dangerDeep=blend(danger,black,.42f),
            text=ink,muted=quiet,dim=blend(quiet,card,.25f),titleColor=ink,onAccent=on,scrim=b,lapis=blend(card,brand,.12f),lapisLight=blend(card,brand,.23f),
            button=shape.button,primary=primary,primaryHorizontal=shape.primaryHorizontal,primaryText=on,primaryEdge=blend(brand,black,.45f),
            secondary=secondary,secondaryText=brandText,secondaryBorder=edge,chip=secondary,chipText=ink,chipBorder=blend(edge,card,.25f),
            successFill=intArrayOf(success,blend(success,if(light)black else white,.04f),success),successOn=if(light)white else black,
            dangerFill=intArrayOf(danger,blend(danger,if(light)black else white,.04f),danger),dangerOn=if(light)white else black,
            buttonRadius=shape.buttonRadius,glow=shape.glow,ring=intArrayOf(brand,blend(brand,white,.25f),blend(brand,if(light)black else white,.1f)),
            ringTrack=alpha(brand,35),swatch=intArrayOf(b,brand,card)
        )
    }
    private fun create():List<ThemeSpec> = listOf(
        palette("emerald","جنگل زمرد","Emerald Grove",false,"071F1C","103B34","E7FAF3","A8C9BE","68D9B6","457F72"),
        palette("cobalt","یاقوت آبی","Blue Sapphire",false,"071B35","112F52","EDF5FF","ABCAE5","83BEFF","4C7599"),
        palette("copper","غروب مسی","Copper Dusk",false,"24130F","3E2720","FFF3E7","D7BEAD","F2B787","91684C"),
        palette("neon","شفق قطبی","Aurora",false,"130C2B","281C45","F4EFFF","C5B6DE","D1ADFF","81669D"),
        palette("iranic","مروارید","Pearl Atelier",true,"F3EDE1","FFFCF5","28241F","686057","805522","C4B18B"),
        palette("ivory","کریستال","Crystal Palace",true,"EAF2F7","FCFEFF","172A42","50677E","245D9C","A8C2D9"),
        palette("pomegranate","مخمل انار","Pomegranate Velvet",false,"290D1B","43172E","FFF0F5","DAB5C6","FFAACD","A36E89"),
        palette("caspian","اقیانوس کاسپین","Caspian Ocean",false,"061E2B","0C3547","E7F9FF","A2C8D6","70DBEE","3E7B8B"),
        palette("midnight","نیمه‌شب نقره‌ای","Silver Midnight",false,"0C1524","192A40","F0F6FF","AFBFD2","B5D1EF","647E9C"),
        palette("amethyst","بنفشهٔ سحر","Dawn Amethyst",false,"1D1230","33204C","F7EEFF","CBBDDD","D4B8FF","896BA6"),
        palette("rose","رز مهتاب","Moonlit Rose",true,"FBF0F4","FFFAFC","472436","76616E","9A3F69","E1B8CE"),
        palette("pistachio","پستهٔ بهاری","Spring Pistachio",true,"F0F6E9","FBFFF6","293924","65725D","4E7041","C5D8B5"),
        palette("saffron","زعفران کویر","Desert Saffron",false,"241B0A","3A2E15","FFF6DE","D3C4A2","F4CE70","96804D"),
        palette("coral","مرجان ساحلی","Coastal Coral",false,"291512","462922","FFF1EB","DCBEB2","FFB49A","A27664"),
        palette("lavender","لاوندر ابری","Cloud Lavender",true,"F4F0FC","FDFBFF","362849","746984","715494","DAC9EF"),
        palette("mint","نعنای یخی","Ice Mint",true,"EAF8F3","F8FFFC","1F4037","56756A","266C58","B7DCD0"),
        palette("sky","آسمان سحر","Dawn Sky",true,"EEF6FC","FCFEFF","213B52","5B7286","2E6591","BDD7EB"),
        palette("lotus","طلوع نیلوفر","Lotus Sunrise",false,"181A34","292E50","F1F1FF","B9C0E3","B3C0FF","727DA9")
    )
    var all:List<ThemeSpec> = create(); private set
    fun configureGeometry(id:String){val chosen=LegacyThemes.byId(id).id;if(chosen!=geometryId){geometryId=chosen;all=create()}}
    val COBALT get()=byId("cobalt");val COPPER get()=byId("copper");val IRANIC get()=byId("iranic")
    val EMERALD get()=byId("emerald");val IVORY get()=byId("ivory");val NEON get()=byId("neon")
    val FALLBACK get()=all.first{it.id=="emerald"}
    fun byId(id:String?):ThemeSpec=all.firstOrNull{it.id==id}?:FALLBACK
    fun isChosen(id:String?)=all.any{it.id==id}
}

/** The active style; [apply] copies its tokens into [Royal] so every existing widget follows it. */
object ThemeEngine {
    @Volatile var spec: ThemeSpec = Themes.EMERALD
        private set

    /** Changes with every switch, so cached artwork of the previous style is never reused. */
    @Volatile var version: Int = 0
        private set

    fun apply(next: ThemeSpec) {
        if (next === spec && version > 0) return
        spec = next
        version++
        Royal.night = next.onAccent
        Royal.scrim = next.scrim
        Royal.deep = next.raisedLow
        Royal.lapis = next.lapis
        Royal.lapisLight = next.lapisLight
        Royal.panelTop = next.surface
        Royal.panelBottom = next.surfaceLow
        Royal.gold = next.accent
        Royal.goldLight = next.accentText
        Royal.goldDeep = next.accentDeep
        Royal.bronze = next.accentShadow
        Royal.metalLight = next.metalLight
        Royal.metal = next.metal
        Royal.metalDeep = next.metalDeep
        Royal.metalShadow = next.metalShadow
        Royal.turquoise = next.success
        Royal.turquoiseLight = next.successText
        Royal.turquoiseDeep = next.successDeep
        Royal.crimson = next.danger
        Royal.crimsonLight = next.dangerText
        Royal.crimsonDeep = next.dangerDeep
        Royal.ivory = next.text
        Royal.muted = next.muted
        Royal.dim = next.dim
        Royal.title = next.titleColor
        Royal.alert = if (next.light) next.dangerText else 0xFFFF4F58.toInt()
    }
}

/**
 * The first picture before the app has drawn anything, in the colours of the chosen style (no white or navy flash):
 * Android 13+ keeps a splash theme per style for the next start; the web versions keep the colours for their
 * loading page (the host mirrors this preferences file into the browser's local storage).
 */
object LaunchLook {
    const val PREFS = "divan_launch"

    private fun hex(c: Int) = "#" + Integer.toHexString(c and 0xFFFFFF or 0x1000000).substring(1).uppercase()

    fun remember(activity: android.app.Activity, spec: ThemeSpec) {
        val look = org.json.JSONObject()
            .put("id", spec.id).put("light", spec.light)
            .put("b1", hex(spec.bg[0])).put("b2", hex(spec.bg[1])).put("b3", hex(spec.bg[2]))
            .put("title", hex(spec.accentText)).put("gold", hex(spec.accent)).put("muted", hex(spec.muted)).put("dim", hex(spec.dim))
            .toString()
        val prefs = activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getString("look", null) != look) prefs.edit().putString("look", look).apply()
        // Drop the opaque launch-theme override remembered by older installations.
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            activity.splashScreen.setSplashScreenTheme(0)
        }
    }
}

/** Bottom navigation of the main pages: home, leagues, history, ranking and settings. */
object NavBar {
    const val HOME = 0
    const val LEAGUES = 1
    const val HISTORY = 2
    const val RANKING = 3
    const val SETTINGS = 4

    private val items = listOf(
        Triple(HOME, RoyalIcon.HOME, "خانه"),
        Triple(LEAGUES, RoyalIcon.TROPHY, "لیگ‌ها"),
        Triple(HISTORY, RoyalIcon.HISTORY, "تاریخچه"),
        Triple(RANKING, RoyalIcon.CROWN, "رنک‌بندی"),
        Triple(SETTINGS, RoyalIcon.SETTINGS, "تنظیمات")
    )

    fun view(screen: Screen, selected: Int): android.view.View {
        val kit = screen.kit
        val host = screen.host
        val t = ThemeEngine.spec
        return android.widget.FrameLayout(host).apply {
            setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(8))
            addView(kit.horizontal(android.view.Gravity.CENTER).apply {
                background = PanelDrawable(kit.density, PanelStyle.RAISED, 22f)
                setPadding(kit.dp(6), kit.dp(5), kit.dp(6), kit.dp(9))
                items.forEach { (id, icon, label) ->
                    val on = id == selected
                    addView(kit.vertical(android.view.Gravity.CENTER_HORIZONTAL).apply {
                        setPadding(0, kit.dp(5), 0, kit.dp(5))
                        if (on) background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = kit.dpf(16f * t.radius)
                            setColor(Royal.alpha(Royal.gold, if (t.light) 0.12f else 0.16f))
                        }
                        addView(kit.icon(icon, if (on) Royal.goldLight else Royal.muted, 22))
                        addView(kit.text(label, TextStyle.CAPTION, if (on) Royal.goldLight else Royal.muted, android.view.Gravity.CENTER, 1).apply {
                            if (on) typeface = RoyalFonts.get(context, true)
                        })
                        isClickable = true
                        contentDescription = label
                        setOnClickListener {
                            kit.tap(it)
                            if (id == selected) return@setOnClickListener
                            when (id) {
                                HOME -> host.resetTo(HomeScreen(host))
                                LEAGUES -> host.resetTo(HomeScreen(host), LeagueListScreen(host))
                                HISTORY -> host.resetTo(HomeScreen(host), HistoryScreen(host))
                                RANKING -> host.resetTo(HomeScreen(host), RankingScreen(host))
                                SETTINGS -> host.resetTo(HomeScreen(host), SettingsScreen(host))
                            }
                        }
                    }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = kit.dp(2); marginEnd = kit.dp(2)
                    })
                }
            }, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
