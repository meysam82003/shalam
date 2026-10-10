package com.meysam.divanemtiaz

/** What a last-move card asks the narrator to do once it is drawn. */
enum class CardAction {
    /** Nothing to choose: the effect is shown and the player leaves (or stays). */
    PLAIN,
    /** The exiting player names another player and guesses his role. */
    GUESS_ROLE,
    /** The exiting player picks one or more players. */
    PICK_PLAYERS,
    /** The exiting player picks a player who is already out of the game. */
    PICK_OUT_PLAYER,
    /** The effect happens on the coming night (a night job appears). */
    NEXT_NIGHT
}

/**
 * The 27 last-move cards (کارت حرکت آخر) with the source app's names and explanations, in deck order.
 * [requires] is a role that must be in the game for the card to be selectable.
 */
enum class MafiaLastCard(
    val faName: String,
    val tagline: String,
    val glyph: MafiaGlyph,
    val action: CardAction,
    val picks: Int = 0,
    val requires: MafiaRole? = null,
    /** True when the player stays in the game after the card (unless a guess fails). */
    val stays: Boolean = false
) {
    INTELLIGENCE("هوش و ذکاوت", "حدس نقش و بازگشت", MafiaGlyph.HEAD, CardAction.GUESS_ROLE, 1),
    NOSTRADAMUS_BEAUTIFUL_MIND("ذهن زیبا (نوستراداموس)", "یافتن نوستراداموس", MafiaGlyph.CRYSTAL, CardAction.GUESS_ROLE, 1, MafiaRole.NOSTRADAMUS),
    JACK_SPARROW_BEAUTIFUL_MIND("ذهن زیبا (جک اسپارو)", "یافتن جک اسپارو", MafiaGlyph.ANCHOR, CardAction.GUESS_ROLE, 1, MafiaRole.JACK_SPARROW),
    SHERLOCK_HOLMES_BEAUTIFUL_MIND("ذهن زیبا (شرلوک هلمز)", "یافتن شرلوک هلمز", MafiaGlyph.PIPE, CardAction.GUESS_ROLE, 1, MafiaRole.SHERLOCK_HOLMES),
    RED_CARPET("فرش قرمز", "ورود قطعی به دفاعیه", MafiaGlyph.ROSE, CardAction.PICK_PLAYERS, 1),
    GREEN_MILE("مسیر سبز", "مصونیت از رأی", MafiaGlyph.LEAF, CardAction.PICK_PLAYERS, 1),
    VERTIGO("سرگیجه", "بی‌اثر شدن ۲۴ ساعته", MafiaGlyph.WIND, CardAction.NEXT_NIGHT, 1),
    INSOMNIA("بی خوابی", "شب بدون زد و خورد", MafiaGlyph.HOURGLASS, CardAction.PLAIN),
    FINAL_SHOT("شلیک نهایی", "شلیک به جای مافیا", MafiaGlyph.PISTOL, CardAction.NEXT_NIGHT, 1),
    CENSUS("سر شماری", "اعلام تعداد هر گروه", MafiaGlyph.SCROLL, CardAction.PLAIN),
    EXHUMATION("نبش قبر", "افشای نقش یک خارج‌شده", MafiaGlyph.TOMBSTONE, CardAction.PICK_OUT_PLAYER, 1),
    GOD_FATHER_PRESENT("اعلام حضور", "حضور پدرخوانده", MafiaGlyph.FEDORA, CardAction.PLAIN, 0, MafiaRole.GODFATHER),
    SECOND_CHANCE("فرصت دوباره", "ماندن در بازی", MafiaGlyph.HEART, CardAction.PLAIN, 0, null, true),
    THIRTEEN_LIE("دروغ سیزده", "یک دروغ و خروج", MafiaGlyph.THEATER, CardAction.PLAIN),
    GOODBYE("خداحافظی", "خروج بی‌کلام", MafiaGlyph.HAND, CardAction.PLAIN),
    PRIVATE_WILL("وصیت خصوصی", "وصیت به یک نفر", MafiaGlyph.BOOK, CardAction.PLAIN),
    DARE_OR_TRUTH("جرات یا حقیقت", "جرات یا حقیقت", MafiaGlyph.QUESTION, CardAction.PICK_PLAYERS, 1),
    CARD_EXCHANGE("کارت به کارت", "جابه‌جایی دو نقش", MafiaGlyph.CARDS, CardAction.NEXT_NIGHT, 2),
    FACE_OFF("تغییر چهره", "تعویض نقش خود", MafiaGlyph.MASK, CardAction.NEXT_NIGHT, 1),
    SILENCE_OF_THE_LAMBS("سکوت بره ها", "سکوت یک روزه", MafiaGlyph.BELL, CardAction.PICK_PLAYERS, 2),
    SHOW_IDENTITY("افشای هویت", "نمایش نقش", MafiaGlyph.BADGE, CardAction.PLAIN),
    HANDCUFF("دستبند", "گرفتن توانایی یک شب", MafiaGlyph.CUFFS, CardAction.PICK_PLAYERS, 1),
    LUCK("بخت و اقبال", "قرعهٔ مرگ دو نفره", MafiaGlyph.STAR, CardAction.PICK_PLAYERS, 1),
    BLACK_NIGHT("شب سیاه", "شب مافیا و مستقل", MafiaGlyph.MOON, CardAction.PLAIN),
    NIGHT_LIKE_DAY("شب مثل روز روشن", "علنی شدن شب", MafiaGlyph.BULB, CardAction.PLAIN),
    BAD_CHANCE("بد شانس", "خروج تصادفی همراه", MafiaGlyph.SKULL, CardAction.PLAIN),
    LAST_VENOM("زهر آخر", "زهر بی‌نجات", MafiaGlyph.FLASK, CardAction.NEXT_NIGHT, 1);

    val explain: String get() = when (this) {
        INTELLIGENCE -> "بازیکن در حال ترک بازی با داشتن این کارت باید از هوش خود استفاده نماید، یک نقش از نقش هایی که در روند بازی اعلام نشده و یا مستقیم نمی داند را بر مبنای حدس برملا و پس از تایید صحت این افشاگری توسط گرداننده می تواند به بازی برگردد"
        NOSTRADAMUS_BEAUTIFUL_MIND -> "اگر صاحب این کارت بتواند فقط نوستراداموس را از بین بازیکنان داخل بازی شناسایی کند. به بازی برمیگردد و نوستراداموس بجای او از بازی خارج میشود.\nاگر نوستراداموس این کارت را انتخاب کند٬ بدون نیاز به استعلام به بازی باز میگردد اما سپر وی از بین رفته و با شلیک شب کشته خواهد شد"
        JACK_SPARROW_BEAUTIFUL_MIND -> "اگر صاحب این کارت بتواند نقش جک اسپارو را درست حدس بزند به جای او به بازی بر میگردد.\nاگر نقش جک اسپارو در روزهای قبل شو شده باشد، بازیکن صاحب کارت باید بدنبال فرد طلسم شده توسط جک اسپارو باشد یعنی چنانچه بازیکن طلسم شده را درست حدس بزند جک اسپارو از بازی خارج میشود و آن بازیکن به بازی بر میگردد."
        SHERLOCK_HOLMES_BEAUTIFUL_MIND -> "اگر بازیکن اخراجی بتواند نقش شرلوک هلمز را حدس بزند در بازی میماند و به جای او شرلوک هلمز از بازی خارج خواهد شد"
        RED_CARPET -> "بازیکن در حال ترک بازی با داشتن این کارت بازیکنی را متهم میکند و فرش قرمزی برای قرارگرفتن در رای گیری پایان آن روز برایش پهن میکند تا در هر شرایطی به دفاعیه وارد شود"
        GREEN_MILE -> "بازیکن در حال ترک بازی با داشتن این کارت می تواند یک بازیکن را در مسیر سبز آن روز قرار دهد که تحت هر اتهامی در رای گیری قرار نگیرد"
        VERTIGO -> "بازیکن در حال ترک بازی با داشتن این کارت یک نفر را به صورت پنهانی (در شب) به راوی معرفی می کند و آن شخص در هر نقشی که باشد 24 ساعت (یک شب و یک روز) نمی تواند از قابلیت خود استفاده کند البته خود شخص این موضوع را نمی داند بلکه تمامی حرکات او در طول روز و شب بی اثر خواهد بود"
        INSOMNIA -> "امشب کسی نمی خوابد، زد و خوردی وجود ندارد و کسی کشته نمی شود. مستقیم به روز بعد میرویم. احتمالا بازیکن ها خسته و خواب آلود به متهم کردن یا حمایت از یکدیگر خواهند پرداخت"
        FINAL_SHOT -> "بازیکن در حال ترک بازی با داشتن این کارت به بازداشتی موقت میرود و بعد از رای گیری دور دوم برخواهد گشت و به جای مافیا شلیک شب را به انتخاب خودش انجام خواهد داد، بازیکن بعد از شلیک نهایی کاملا از بازی خارج می شود"
        CENSUS -> "با داشتن این کارت گرداننده باید تعداد نقش های مافیایی، شهروندی و همچنین مستقل در بازی را اعلام کند"
        EXHUMATION -> "اگر کسی از بازی بیرون رفته به انتخاب این شخص نقشش گفته می شود."
        GOD_FATHER_PRESENT -> "با داشتن این کارت گرداننده باید حضور و یا عدم حضور پدرخوانده را به همه اعلام کند"
        SECOND_CHANCE -> "از بازی خارج نمی شوی و در بازی می مانی"
        THIRTEEN_LIE -> "بازیکن در حال ترک بازی با داشتن این کارت باید یک دروغ درباره خودش و در رابطه با بازی بگوید و پس از تایید گرداننده از بازی خارج می شود."
        GOODBYE -> "بازیکن با داشتن این کارت هیچ کاری انجام نمیدهد و بدون هیچ کلامی از بازی خارج خواهد شد"
        PRIVATE_WILL -> "بازیکن اخراجی به صورت خصوصی فقط به یک بازیکن وصیتی میکند و پس از آن از بازی خارج خواهد شد"
        DARE_OR_TRUTH -> "بازیکن دارای کارت جرات یا حقیقت یک نفر را از بین بازیکنان حاضر در بازی انتخاب میکند\nآن فرد حق این انتخاب را دارد که جرات انتخاب کند یا حقیقت\nدر انتخاب جرات بازیکن از بازی بیرون میرود و در حقیقت در بازی میماند به شرط اینکه سوال بازیکن دارای کارت را با تایید گرداننده درست جواب دهد"
        CARD_EXCHANGE -> "بازیکن دارای کارت دو نفر را انتخاب میکند و آن دو نفر نقش هایشان با هم جابه جا میشود"
        FACE_OFF -> "بازیکن قبل از خروج کارت نقش خود را با یکی از بازیکنان عوض می کند."
        SILENCE_OF_THE_LAMBS -> "بازیکن اخراجی با نشان کردن دو نفر٬ آنان را بمدت یک روز سایلنت کرده و افرادی که نشان شده باشند٬ فردا حتی در دفاع امکان صحبت کردن ندارند. اگر تعداد بازیکنان به نصف رسید٬ در سکوت بره ها یک نفر سایلنت خواهد شد."
        SHOW_IDENTITY -> "اگر بازیکن اخراجی این کارت را انتخاب کند٬ گرداننده بدون نیاز به استعلام جمعی نقش دقیق او را فاش میکند و او از بازی بیرون میرود. در این حالت امکان بازگشت به بازی توسط کنستانتین را ندارد."
        HANDCUFF -> "بازیکن اخراجی با کارت دستبند هرکس را نشان کند٬ توانمندی های آن شب را از وی میگیرد.\nنکته: اگر تنها یک مافیا در بازی مانده باشد و کارت دستبند دریافت کرده باشد٬ همچنان امکان شلیک خواهد داشت."
        LUCK -> "یک نفر را انتخاب می کند و گرداننده بین این دو بازیکن قرعه مرگ میاندازد و کسی که ببرد تو بازی می ماند."
        BLACK_NIGHT -> "در شب سیاه نقش های شهروندی نمیتوانند از نقش های خود استفاده کنند واین شب متعلق به گروه مافیا و مستقل است"
        NIGHT_LIKE_DAY -> "تمامی اتفاقات شب برای همه علنی خواهد شد و به همه گفته میشود که چه نقشی چه کاری انجام داده است"
        BAD_CHANCE -> "یکی از نقش های حاضر در بازی به صورت تصادفی انتخاب میشود و همراه بازیکن اخراجی از بازی خارج خواهد شد"
        LAST_VENOM -> "دارنده کارت زهر آخر در شب یک زهر به یکی از بازیکنان میدهد و آن شخص بدون هیچ نجاتی در روز از بازی بیرون خواهد رفت"
    }
}
