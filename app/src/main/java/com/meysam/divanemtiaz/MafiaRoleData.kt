package com.meysam.divanemtiaz

// Generated from the narrator-app rules spec (roles.json). Names, explanations, intro prompts and options follow the source app.

enum class MafiaSide(val title: String) { CITY("شهروند"), MAFIA("مافیا"), INDEPENDENT("مستقل") }

data class MafiaOptionDef(val label: String, val values: List<String>, val default: String, val isSwitch: Boolean = false)

enum class MafiaRole(
    private val baseName: String, private val baseSide: MafiaSide, private val baseGlyph: MafiaGlyph, private val baseTagline: String,
    /** Position in the night button list (lower first); 0 = no night button of its own. */
    private val baseWake: Int, private val baseIntro: Int, val nightSound: String
) {
    CITIZEN("شهروند ساده", MafiaSide.CITY, MafiaGlyph.CITIZEN, "تحلیل و رأی", 0, 71, ""),
    PROFESSIONAL("حرفه ای", MafiaSide.CITY, MafiaGlyph.TARGET, "شلیک شبانه", 34, 37, "mafia_shot_sound"),
    SNIPER("اسنایپر", MafiaSide.CITY, MafiaGlyph.CROSSHAIR, "شلیک دقیق", 35, 38, "mafia_shot_sound"),
    INHERITOR("وارث", MafiaSide.CITY, MafiaGlyph.TREE, "ارث بردن نقش", 3, 27, ""),
    SUSPECT("مظنون", MafiaSide.CITY, MafiaGlyph.QUESTION, "استعلام مثبت", 0, 26, ""),
    COMMANDER("فرمانده", MafiaSide.CITY, MafiaGlyph.STAR, "تأیید شلیک", 36, 39, ""),
    DETECTIVE("کارآگاه", MafiaSide.CITY, MafiaGlyph.MAGNIFIER, "استعلام هویت", 41, 41, ""),
    SNOWMAN("آدم برفی", MafiaSide.CITY, MafiaGlyph.SNOWFLAKE, "گلولهٔ برفی", 42, 42, ""),
    DOCTOR("دکتر", MafiaSide.CITY, MafiaGlyph.MED_CROSS, "نجات در شب", 39, 34, "mafia_heart_sound"),
    PROTECTOR("محافظ", MafiaSide.CITY, MafiaGlyph.HAND, "خنثی‌سازی بمب", 0, 36, ""),
    OCEAN("اوشن", MafiaSide.CITY, MafiaGlyph.WAVE, "تیم اوشن", 49, 32, ""),
    ATTAR("عطار", MafiaSide.CITY, MafiaGlyph.LEAF, "زهر و پادزهر", 1, 35, ""),
    SAINT("قدیس", MafiaSide.CITY, MafiaGlyph.BELL, "خنثی کردن افسون", 37, 33, ""),
    SALVATION_ANGEL("فرشته نجات", MafiaSide.CITY, MafiaGlyph.WINGS, "بازگرداندن کشته", 67, 63, ""),
    CHAPLIN("چاپلین", MafiaSide.CITY, MafiaGlyph.BOWLER, "محافظت از خرید", 14, 50, ""),
    SMITH("آهنگر", MafiaSide.CITY, MafiaGlyph.ANVIL, "زره آهنین", 15, 64, "mafia_heart_sound"),
    HERO("رویین تن", MafiaSide.CITY, MafiaGlyph.HELMET, "نامیرا در شب", 0, 51, ""),
    SPIDER("عنکبوت", MafiaSide.CITY, MafiaGlyph.SPIDER, "تار عنکبوت", 38, 52, ""),
    INTERROGATOR("بازپرس", MafiaSide.CITY, MafiaGlyph.GAVEL, "بازپرسی دو نفر", 44, 46, ""),
    RESEARCHER("محقق", MafiaSide.CITY, MafiaGlyph.BOOK, "پیوند با مافیا", 31, 44, ""),
    COMMANDO("تکاور", MafiaSide.CITY, MafiaGlyph.HOURGLASS, "شلیک متقابل", 33, 70, "mafia_shot_sound"),
    GUNMAN("تفنگدار", MafiaSide.CITY, MafiaGlyph.PISTOL, "پخش تفنگ", 50, 40, ""),
    JUPITER("ژوپیتر", MafiaSide.CITY, MafiaGlyph.BOLT, "محافظت از تبدیل", 21, 45, ""),
    CITIZEN_KANE("همشهری کین", MafiaSide.CITY, MafiaGlyph.SCROLL, "افشای مافیا", 64, 47, ""),
    CONSTANTINE("کنستانتین", MafiaSide.CITY, MafiaGlyph.PENTAGRAM, "بازگرداندن به بازی", 66, 62, ""),
    RUSSIAN_ROULETTE("رولت روسی", MafiaSide.CITY, MafiaGlyph.CYLINDER, "تفنگ رولت", 43, 43, ""),
    SACRIFICE("فدایی", MafiaSide.CITY, MafiaGlyph.HEART, "فدا شدن", 0, 58, ""),
    COWBOY("کابوی", MafiaSide.CITY, MafiaGlyph.COWBOY, "دوئل در روز", 0, 59, ""),
    SUICIDE_BOMBER("بمبر", MafiaSide.CITY, MafiaGlyph.DYNAMITE, "انفجار در روز", 0, 60, ""),
    DIE_HARD("جان سخت", MafiaSide.CITY, MafiaGlyph.SHIELD, "زره و استعلام", 59, 49, ""),
    MAYOR("شهردار", MafiaSide.CITY, MafiaGlyph.CROWN, "وتو در رأی‌گیری", 0, 65, ""),
    SALESMAN("فروشنده", MafiaSide.CITY, MafiaGlyph.BRIEFCASE, "گرفتن توانایی", 62, 54, ""),
    REPORTER("خبرنگار", MafiaSide.CITY, MafiaGlyph.NEWSPAPER, "ردیابی مذاکره", 32, 57, ""),
    HACKER("هکر", MafiaSide.CITY, MafiaGlyph.LAPTOP, "لیست خطرناک", 52, 55, ""),
    CLEVER("نخبه", MafiaSide.CITY, MafiaGlyph.BULB, "کشف نقش", 46, 56, ""),
    MASON("فراماسون", MafiaSide.CITY, MafiaGlyph.COMPASS, "عضوگیری گروه", 48, 30, ""),
    TYLER("تایلر", MafiaSide.CITY, MafiaGlyph.GLOVE, "پسر ماسون", 0, 31, ""),
    SLEEP_WALKER("خوابگرد", MafiaSide.CITY, MafiaGlyph.SLEEP, "خواباندن مافیا", 12, 66, ""),
    PRIEST("کشیش", MafiaSide.CITY, MafiaGlyph.CROSS, "وقت اضافه", 61, 67, ""),
    PSYCHOLOGIST("روانپزشک", MafiaSide.CITY, MafiaGlyph.HEAD, "ساکت کردن", 60, 53, ""),
    GUARDIAN("نگهبان", MafiaSide.CITY, MafiaGlyph.WALL, "خنثی کردن گروگان", 13, 69, ""),
    GUARD("مراقب", MafiaSide.CITY, MafiaGlyph.LOCK, "محافظت از بازجویی", 63, 48, ""),
    SPECIAL_DETECTIVE("کارآگاه ویژه", MafiaSide.CITY, MafiaGlyph.BINOCULARS, "کشف مستقل‌ها", 45, 68, ""),
    SABA("صبا", MafiaSide.CITY, MafiaGlyph.WIND, "قرض گرفتن نقش", 47, 61, ""),
    MAFIA("مافیا ساده", MafiaSide.MAFIA, MafiaGlyph.MASK, "شلیک گروهی", 25, 23, ""),
    GODFATHER("پدرخوانده", MafiaSide.MAFIA, MafiaGlyph.FEDORA, "رهبر مافیا", 25, 2, "mafia_shot_sound"),
    PABLO("پابلو", MafiaSide.MAFIA, MafiaGlyph.COINS, "جانشین پدرخوانده", 24, 28, ""),
    CAST_AWAY("دور افتاده", MafiaSide.MAFIA, MafiaGlyph.PALM, "یافتن مافیا", 26, 29, ""),
    LOVER("معشوقه", MafiaSide.MAFIA, MafiaGlyph.ROSE, "جانشین پدرخوانده", 25, 8, ""),
    STRONG_MAN("مرد قوی", MafiaSide.MAFIA, MafiaGlyph.DUMBBELL, "قتل بی‌دفاع", 25, 17, "mafia_shot_sound"),
    WITCHER("جادوگر", MafiaSide.MAFIA, MafiaGlyph.WITCH, "بازتاب توانایی", 16, 3, "mafia_joker_laugh"),
    VOODOO("افسونگر", MafiaSide.MAFIA, MafiaGlyph.DOLL, "کلمهٔ ممنوعه", 19, 18, ""),
    NATO("ناتو", MafiaSide.MAFIA, MafiaGlyph.GLOBE, "حدس نقش و حذف", 25, 13, "mafia_shot_sound"),
    MATADOR("ماتادور", MafiaSide.MAFIA, MafiaGlyph.HORNS, "بستن توانایی", 17, 19, ""),
    ILLUSIONIST("شعبده باز", MafiaSide.MAFIA, MafiaGlyph.TOPHAT, "بستن ۲۴ ساعته", 18, 20, ""),
    NATASHA("ناتاشا", MafiaSide.MAFIA, MafiaGlyph.MOON, "سکوت مطلق", 30, 11, ""),
    BOMBER("بمب گذار", MafiaSide.MAFIA, MafiaGlyph.BOMB, "بمب‌گذاری", 20, 5, ""),
    GOOD_MAN("ساول گودمن", MafiaSide.MAFIA, MafiaGlyph.SCALES, "خریداری شهروند", 25, 12, "mafia_shot_sound"),
    FRAUD("شیاد", MafiaSide.MAFIA, MafiaGlyph.CARDS, "خنثی کردن کارآگاه", 29, 10, "mafia_joker_laugh"),
    WRECKER("خرابکار", MafiaSide.MAFIA, MafiaGlyph.HAMMER, "خراب کردن تفنگ", 51, 6, ""),
    YAKUZA("یاکوزا", MafiaSide.MAFIA, MafiaGlyph.KATANA, "تبدیل به مافیا", 22, 7, ""),
    TERRORIST("تروریست", MafiaSide.MAFIA, MafiaGlyph.FLAME, "ترور در روز", 25, 21, ""),
    CHARLATAN("شارلاتان", MafiaSide.MAFIA, MafiaGlyph.THEATER, "وارونه کردن استعلام", 28, 9, "mafia_joker_laugh"),
    DOCTOR_LECTOR("دکتر لکتر", MafiaSide.MAFIA, MafiaGlyph.MUZZLE, "نجات مافیا", 27, 4, "mafia_heart_sound"),
    BODYGUARD("بادیگارد", MafiaSide.MAFIA, MafiaGlyph.BADGE, "سپر پدرخوانده", 25, 16, ""),
    INVESTIGATOR("بازجو", MafiaSide.MAFIA, MafiaGlyph.CUFFS, "بازجویی نقش", 65, 15, ""),
    POISON_MAKER("سم ساز", MafiaSide.MAFIA, MafiaGlyph.FLASK, "زهر با تأخیر", 25, 14, "mafia_shot_sound"),
    SPY("جاسوس", MafiaSide.MAFIA, MafiaGlyph.EYE, "نفوذی مافیا", 25, 22, ""),
    THIEF("دزد", MafiaSide.MAFIA, MafiaGlyph.KEY, "دزدیدن توانایی", 23, 25, ""),
    CHURCHILL("چرچیل", MafiaSide.INDEPENDENT, MafiaGlyph.VICTORY, "شرط خروج", 53, 72, "mafia_shot_sound"),
    NOSTRADAMUS("نوستراداموس", MafiaSide.INDEPENDENT, MafiaGlyph.CRYSTAL, "پیش‌بینی بازی", 6, 73, ""),
    SHERLOCK_HOLMES("شرلوک هلمز", MafiaSide.INDEPENDENT, MafiaGlyph.PIPE, "سلاخی و تصاحب نقش", 11, 74, ""),
    JACK_SPARROW("جک اسپارو", MafiaSide.INDEPENDENT, MafiaGlyph.ANCHOR, "طلسم", 54, 75, ""),
    ZODIAC("زودیاک", MafiaSide.INDEPENDENT, MafiaGlyph.ZODIAC, "شلیک و نامه", 56, 77, "mafia_shot_sound"),
    JIGSAW("جیگساو", MafiaSide.INDEPENDENT, MafiaGlyph.PUZZLE, "بازی اره", 57, 78, ""),
    FELON("جانی", MafiaSide.INDEPENDENT, MafiaGlyph.TOMBSTONE, "شلیک مستقل", 55, 76, "mafia_shot_sound"),
    CORONA("کرونا", MafiaSide.INDEPENDENT, MafiaGlyph.VIRUS, "سرایت بیماری", 5, 79, ""),
    KILLER("قاتل", MafiaSide.INDEPENDENT, MafiaGlyph.DAGGER, "قتل بی‌دفاع", 58, 80, "mafia_shot_sound"),
    JOKER("جوکر", MafiaSide.INDEPENDENT, MafiaGlyph.JESTER, "برد با رأی", 0, 81, ""),
    // Roles of the scenarios (rules defined as data in MafiaGenericRoles) and free slots for the narrator's own roles.
    JUDGE("قاضی", MafiaSide.CITY, MafiaGlyph.GAVEL, "نگه داشتن در رأی", 0, 90, ""),
    LAWYER("وکیل", MafiaSide.CITY, MafiaGlyph.BRIEFCASE, "دفاع و وکالت", 47, 91, ""),
    HERO_N("قهرمان", MafiaSide.CITY, MafiaGlyph.HELMET, "محافظ شهر", 48, 92, ""),
    PICKPOCKET("دست‌کج", MafiaSide.CITY, MafiaGlyph.HAND, "دست‌کجی شبانه", 46, 93, ""),
    SORCERER_C("جادوگر شهر", MafiaSide.CITY, MafiaGlyph.WITCH, "جادوی شبانه", 49, 94, ""),
    TOUGH("گردن‌کلفت", MafiaSide.CITY, MafiaGlyph.DUMBBELL, "یک جان اضافه", 0, 95, ""),
    GRAVEDIGGER("گورکن", MafiaSide.CITY, MafiaGlyph.TOMBSTONE, "نقش مردگان", 60, 96, ""),
    HITMAN("قاتل حرفه‌ای", MafiaSide.MAFIA, MafiaGlyph.DAGGER, "قتل حرفه‌ای", 22, 97, ""),
    JALAB("جلب", MafiaSide.MAFIA, MafiaGlyph.CUFFS, "جلب شبانه", 21, 98, ""),
    BOMBMAKER("بمب‌ساز", MafiaSide.MAFIA, MafiaGlyph.BOMB, "ساخت بمب", 20, 99, ""),
    INJECTOR("آمپول‌زن", MafiaSide.MAFIA, MafiaGlyph.SYRINGE, "آمپول شبانه", 19, 100, ""),
    SULTAN("سلطان", MafiaSide.MAFIA, MafiaGlyph.CROWN, "رهبر دربار", 25, 101, ""),
    MERCENARY("مزدور", MafiaSide.MAFIA, MafiaGlyph.COINS, "پاپوش برای رعیت", 23, 102, ""),
    EXECUTIONER("جلاد", MafiaSide.MAFIA, MafiaGlyph.KATANA, "شکار و نشان", 24, 103, ""),
    SARBEDAR("سربدار", MafiaSide.CITY, MafiaGlyph.SHIELD, "انتقام سربداری", 0, 104, ""),
    SCRIBE("کاتب", MafiaSide.CITY, MafiaGlyph.SCROLL, "حق وصیت", 45, 105, ""),
    ELDER("ریش‌سفید", MafiaSide.CITY, MafiaGlyph.HEAD, "خارج کردن از دفاع", 0, 106, ""),
    NIGHTWALKER("شب‌گرد", MafiaSide.CITY, MafiaGlyph.MOON, "یک شب دست‌نیافتنی", 30, 107, ""),
    MASKED("نقابدار", MafiaSide.MAFIA, MafiaGlyph.MASK, "مافیای پنهان", 24, 108, ""),
    REVENANT("رونانت", MafiaSide.CITY, MafiaGlyph.TOMBSTONE, "یک روز مهلت", 0, 109, ""),
    WEREWOLF("ورولف", MafiaSide.INDEPENDENT, MafiaGlyph.HORNS, "زخم شب‌های زوج", 54, 110, ""),
    POISONER("پویزنر", MafiaSide.INDEPENDENT, MafiaGlyph.FLASK, "سم با تأخیر", 55, 111, ""),
    CONSORT("کانسورت", MafiaSide.MAFIA, MafiaGlyph.CUFFS, "مسدود کردن", 21, 112, ""),
    JANITOR("جنیتور", MafiaSide.MAFIA, MafiaGlyph.BRIEFCASE, "حذف وصیت", 22, 113, ""),
    SHADOW("شدو", MafiaSide.MAFIA, MafiaGlyph.EYE, "کنترل نقش", 23, 114, ""),
    DUELMAN("دوعل‌من", MafiaSide.MAFIA, MafiaGlyph.PISTOL, "تفنگ دوئل", 20, 115, ""),
    SILENCER("سایلنسر", MafiaSide.MAFIA, MafiaGlyph.BELL, "سکوت کامل", 19, 116, ""),
    ROJAK("روژاک", MafiaSide.CITY, MafiaGlyph.SHIELD, "ضد جیگساو", 50, 117, ""),
    CRUSADER("کروسایدر", MafiaSide.CITY, MafiaGlyph.CROSS, "محافظت قوی", 40, 118, ""),
    THOUSAND_FACES("هزارچهره", MafiaSide.INDEPENDENT, MafiaGlyph.THEATER, "تصاحب نقش", 56, 119, ""),
    DEPUTY("نایب", MafiaSide.INDEPENDENT, MafiaGlyph.BADGE, "حامی هزارچهره", 0, 120, ""),
    WICKED("شیطان‌صفت", MafiaSide.INDEPENDENT, MafiaGlyph.SPIDER, "پیوند بازیکنان", 57, 121, ""),
    KAFIR("کافر", MafiaSide.MAFIA, MafiaGlyph.HORNS, "رهبر کافران", 25, 122, ""),
    ASSASSIN("قاتل کافران", MafiaSide.MAFIA, MafiaGlyph.DAGGER, "شلیک قطعی", 22, 123, ""),
    HAFTKHAT("هفت‌خط", MafiaSide.MAFIA, MafiaGlyph.CARDS, "دو حدس نقش", 23, 124, ""),
    SAGHI("ساقی", MafiaSide.MAFIA, MafiaGlyph.FLASK, "مست کردن", 20, 125, ""),
    TRAITOR("خائن", MafiaSide.MAFIA, MafiaGlyph.MASK, "خیانت پنهان", 0, 126, ""),
    GAMBLER("قمارباز", MafiaSide.MAFIA, MafiaGlyph.CARDS, "سنگ کاغذ قیچی", 24, 127, ""),
    VILLAGE_THIEF("دزد روستا", MafiaSide.CITY, MafiaGlyph.HAND, "توقف کار شب", 31, 128, ""),
    COMPARER("کارآگاه مقایسه‌ای", MafiaSide.CITY, MafiaGlyph.SCALES, "مقایسهٔ دو نفر", 41, 129, ""),
    NINJA("نینجا", MafiaSide.MAFIA, MafiaGlyph.KATANA, "شلیک مافیا", 25, 130, ""),
    BODYGUARD_C("بادیگارد شهر", MafiaSide.CITY, MafiaGlyph.SHIELD, "محافظت از هدف", 38, 131, ""),
    TERROR_C("ترور", MafiaSide.CITY, MafiaGlyph.DYNAMITE, "ترور تا رأی دوم", 0, 132, ""),
    SCORPION("اسکورپیون", MafiaSide.CITY, MafiaGlyph.TARGET, "شلیک به تریتور", 52, 133, ""),
    TRAITOR_S("تریتور", MafiaSide.INDEPENDENT, MafiaGlyph.DYNAMITE, "کودتا", 8, 134, ""),
    SPECIALIST_S("متخصص کودتا", MafiaSide.INDEPENDENT, MafiaGlyph.BADGE, "هم‌پیمان تریتور", 0, 135, ""),
    LOBBYIST("لابی‌من", MafiaSide.MAFIA, MafiaGlyph.COINS, "لابی وضعیت", 23, 136, ""),
    SHADOW_M("سایه", MafiaSide.MAFIA, MafiaGlyph.EYE, "سایهٔ مافیا", 24, 137, ""),
    EXPERT("متخصص", MafiaSide.CITY, MafiaGlyph.MED_CROSS, "درمان", 39, 138, ""),
    MARSHAL("مارشال", MafiaSide.CITY, MafiaGlyph.BADGE, "مارشال شهر", 34, 139, ""),
    RANGER("رنجر", MafiaSide.CITY, MafiaGlyph.COMPASS, "رنجر شهر", 35, 140, ""),
    OFFICER("آفیسر", MafiaSide.CITY, MafiaGlyph.SHIELD, "آفیسر شهر", 36, 141, ""),
    KNIGHT("شوالیه", MafiaSide.CITY, MafiaGlyph.HELMET, "استعلام شوالیه", 41, 142, ""),
    ROYALIST("رویالیست", MafiaSide.CITY, MafiaGlyph.CROWN, "وفادار ساده", 0, 143, ""),
    REBEL("یاغی", MafiaSide.MAFIA, MafiaGlyph.PISTOL, "ترور پس از رئیس", 0, 144, ""),
    HACKER_M("هکر مافیا", MafiaSide.MAFIA, MafiaGlyph.LAPTOP, "اختلال توانایی", 22, 145, ""),
    GUIDE("راهنما", MafiaSide.CITY, MafiaGlyph.COMPASS, "بیدار کردن هدف", 43, 146, ""),
    MINER("مین‌گذار", MafiaSide.CITY, MafiaGlyph.BOMB, "مین‌گذاری", 44, 147, ""),
    SOLDIER("سرباز", MafiaSide.CITY, MafiaGlyph.HELMET, "سرباز", 0, 148, ""),
    HEADMAN("کدخدا", MafiaSide.CITY, MafiaGlyph.CROWN, "بیدار کردن یک نفر", 45, 149, ""),
    NEWSBRINGER("خبررسان", MafiaSide.CITY, MafiaGlyph.NEWSPAPER, "خبر شبانه", 46, 150, ""),
    MILTON("جان میلتون", MafiaSide.MAFIA, MafiaGlyph.HORNS, "قبض روح", 25, 151, ""),
    VILLAIN("شرور", MafiaSide.MAFIA, MafiaGlyph.SKULL, "تهدید", 23, 152, ""),
    HANNIBAL("هانیبال", MafiaSide.MAFIA, MafiaGlyph.KATANA, "جذب به تیم", 24, 153, ""),
    HENRY("هنری", MafiaSide.CITY, MafiaGlyph.MED_CROSS, "نجات", 39, 154, ""),
    ARTHUR("آرتور", MafiaSide.CITY, MafiaGlyph.MAGNIFIER, "دو استعلام", 41, 155, ""),
    FREDERICK("فردریگ", MafiaSide.CITY, MafiaGlyph.CARDS, "شناسایی کارت", 42, 156, ""),
    ESCOBAR("پابلو اسکوبار", MafiaSide.MAFIA, MafiaGlyph.FEDORA, "رئیس و سلاخ", 25, 157, ""),
    JUAN("خوآن", MafiaSide.MAFIA, MafiaGlyph.CUFFS, "گرفتن توانایی", 22, 158, ""),
    BLANCO("بلانکو", MafiaSide.MAFIA, MafiaGlyph.COINS, "خرید شهروند", 24, 159, ""),
    WEATHERMAN("کارشناس آب‌وهوا", MafiaSide.CITY, MafiaGlyph.WIND, "تغییر هوا", 32, 160, ""),
    SWEEPER("رفتگر", MafiaSide.CITY, MafiaGlyph.LEAF, "محافظت از یخبندان", 33, 161, ""),
    ELIXIR_WITCH("جادوگر بیماری", MafiaSide.MAFIA, MafiaGlyph.WITCH, "قاتل شب", 25, 162, ""),
    REBEL_E("یاغی بیماری", MafiaSide.MAFIA, MafiaGlyph.PISTOL, "انشعاب", 24, 163, ""),
    ASI("عاصی", MafiaSide.INDEPENDENT, MafiaGlyph.HORNS, "رقیب جادوگر", 56, 164, ""),
    ELIXIR("اکسیر", MafiaSide.CITY, MafiaGlyph.FLASK, "درمان", 39, 165, ""),
    CHAPAR("چاپار", MafiaSide.CITY, MafiaGlyph.WIND, "پیک شهر", 40, 166, ""),
    BUTCHER("سلاخ", MafiaSide.MAFIA, MafiaGlyph.KATANA, "سلاخی", 24, 167, ""),
    GANGSTER("گنگستر", MafiaSide.MAFIA, MafiaGlyph.PISTOL, "گنگستر تنها", 58, 168, ""),
    CIRCULE("سیرکوله", MafiaSide.CITY, MafiaGlyph.EYE, "سیرکوله", 56, 169, ""),
    INSPECTOR("بازرس", MafiaSide.CITY, MafiaGlyph.MAGNIFIER, "بازرسی", 60, 170, ""),
    DEXTER("دکستر", MafiaSide.CITY, MafiaGlyph.DAGGER, "قاتل شهر", 33, 171, ""),
    PRIVATE_EYE("کارآگاه خصوصی", MafiaSide.CITY, MafiaGlyph.MAGNIFIER, "استعلام خصوصی", 34, 172, ""),
    NATALI("ناتلی", MafiaSide.MAFIA, MafiaGlyph.DAGGER, "حدس همراه شلیک", 24, 173, ""),
    POTON("پوتون", MafiaSide.MAFIA, MafiaGlyph.ANVIL, "حدس زره", 23, 174, ""),
    JONATHAN("جاناتان", MafiaSide.CITY, MafiaGlyph.CROSSHAIR, "کشتن یک‌باره", 36, 175, ""),
    MONICA("مونیکا", MafiaSide.CITY, MafiaGlyph.BELL, "سکوت یا حذف رأی", 44, 176, ""),
    HERO_P("قهرمان محافظ", MafiaSide.CITY, MafiaGlyph.HELMET, "حفاظت از سایه", 37, 177, ""),
    ARCHITECT("معمار", MafiaSide.CITY, MafiaGlyph.ANVIL, "کارت معمار", 45, 178, ""),
    AVENGER("انتقام‌جو", MafiaSide.CITY, MafiaGlyph.DAGGER, "انتقام", 35, 179, ""),
    ELITE_SNIPER("اسنایپر الیت", MafiaSide.CITY, MafiaGlyph.CROSSHAIR, "شلیک دقیق", 36, 180, ""),
    SHERIFF_W("کلانتر غرب", MafiaSide.CITY, MafiaGlyph.STAR, "مجوز اسلحه", 30, 181, ""),
    WILSON("برادران ویلسون", MafiaSide.MAFIA, MafiaGlyph.PISTOL, "تبهکار", 0, 182, ""),
    BOUNTY("شکارچی جایزه", MafiaSide.INDEPENDENT, MafiaGlyph.TARGET, "شکار جایزه", 0, 183, ""),
    FRAMER("تهمت‌زن", MafiaSide.MAFIA, MafiaGlyph.COINS, "تهمت", 23, 184, ""),
    ZAHHAK("ضحاک", MafiaSide.MAFIA, MafiaGlyph.HORNS, "رهبر اهریمن", 25, 185, ""),
    OWL("بوف", MafiaSide.MAFIA, MafiaGlyph.WINGS, "پر زهرآلود", 24, 186, ""),
    AFRASIAB("افراسیاب", MafiaSide.MAFIA, MafiaGlyph.KATANA, "پهلوان تورانی", 23, 187, ""),
    RUSTAM("رستم", MafiaSide.CITY, MafiaGlyph.HELMET, "پهلوان", 36, 188, ""),
    JAMASB("جاماسب", MafiaSide.CITY, MafiaGlyph.CRYSTAL, "استعلام خونی", 41, 189, ""),
    SIMORGH("سیمرغ", MafiaSide.CITY, MafiaGlyph.WINGS, "پر سیمرغ", 38, 190, ""),
    AHRIMAN("اهریمن", MafiaSide.MAFIA, MafiaGlyph.HORNS, "رهبر تاریکی", 25, 191, ""),
    DARK("تاریک", MafiaSide.MAFIA, MafiaGlyph.MOON, "پیش‌بینی شوالیه", 23, 192, ""),
    AHRIMAN_CHILD("بچه اهریمن", MafiaSide.MAFIA, MafiaGlyph.HORNS, "پیش‌بینی دعوت", 24, 193, ""),
    PSYCHER("روانگر", MafiaSide.MAFIA, MafiaGlyph.HEAD, "پیش‌بینی استاد", 22, 194, ""),
    ARCHER("تیرانداز", MafiaSide.CITY, MafiaGlyph.TARGET, "تیر شب", 35, 195, ""),
    ROSHANA("روشنا", MafiaSide.CITY, MafiaGlyph.BULB, "دعوت به روشنایی", 45, 196, ""),
    MASTER("استاد", MafiaSide.CITY, MafiaGlyph.BOOK, "هدف استاد", 46, 197, ""),
    CURTIS("کرتیس", MafiaSide.CITY, MafiaGlyph.BADGE, "فرماندهٔ نجات", 0, 198, ""),
    PARTISAN("پارتیزان", MafiaSide.CITY, MafiaGlyph.PISTOL, "مامور نجات", 0, 199, ""),
    PASCAL("دکتر پاسکال", MafiaSide.CITY, MafiaGlyph.MED_CROSS, "دو نجات", 39, 200, ""),
    PILOT("خلبان", MafiaSide.CITY, MafiaGlyph.WINGS, "زره و محافظ رأی", 0, 201, ""),
    KESSLER("کسلر", MafiaSide.MAFIA, MafiaGlyph.FEDORA, "رئیس گشتاپو", 0, 202, ""),
    REINHART("راینهارت", MafiaSide.MAFIA, MafiaGlyph.EYE, "حدس نقش پیش از رأی", 0, 203, ""),
    GESTAPO("گشتاپو", MafiaSide.MAFIA, MafiaGlyph.MASK, "مامور گشتاپو", 0, 204, ""),
    CUSTOMER("مشتری", MafiaSide.INDEPENDENT, MafiaGlyph.COINS, "ساید با اسلحه", 0, 205, ""),
    KINGSLAYER("شاه‌کش", MafiaSide.MAFIA, MafiaGlyph.CROWN, "معکوس یا حدس", 23, 206, ""),
    POLICE_CHIEF("رئیس پلیس", MafiaSide.CITY, MafiaGlyph.BADGE, "تیر پس از رأی", 0, 207, ""),
    NEGOTIATOR("مذاکره‌کننده", MafiaSide.MAFIA, MafiaGlyph.BRIEFCASE, "مذاکره یک‌باره", 0, 208, ""),
    ARMORED("زره‌پوش", MafiaSide.CITY, MafiaGlyph.HELMET, "یک زره", 0, 209, ""),
    SHERIFF("کلانتر", MafiaSide.CITY, MafiaGlyph.BADGE, "شلیک نامحدود", 34, 210, ""),
    GUNSMITH("تفنگ‌ساز", MafiaSide.CITY, MafiaGlyph.ANVIL, "سه نوع تفنگ", 35, 211, ""),
    FLAMER("فلیمر", MafiaSide.MAFIA, MafiaGlyph.THEATER, "تغییر استعلام", 24, 212, ""),
    JOKER_M("جوکر مافیا", MafiaSide.MAFIA, MafiaGlyph.JESTER, "معکوس کردن استعلام", 24, 213, ""),
    INFORMANT("خبرچین", MafiaSide.MAFIA, MafiaGlyph.EYE, "جاسوس مافیا", 0, 214, ""),
    BRIDGET("دکتر بریجت", MafiaSide.CITY, MafiaGlyph.MED_CROSS, "نجات شب", 30, 215, ""),
    MORENO("مورنو", MafiaSide.CITY, MafiaGlyph.PISTOL, "تفنگ جنگی و مشقی", 33, 216, ""),
    BONAPARTE("بناپارت", MafiaSide.CITY, MafiaGlyph.COMPASS, "بیدار کردن با کد", 34, 217, ""),
    MARTINEZ("مارتینز", MafiaSide.CITY, MafiaGlyph.CROSSHAIR, "یک شلیک و یک سلاخی", 35, 218, ""),
    CUSTOM_01("نقش سفارشی 1", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_02("نقش سفارشی 2", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_03("نقش سفارشی 3", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_04("نقش سفارشی 4", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_05("نقش سفارشی 5", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_06("نقش سفارشی 6", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_07("نقش سفارشی 7", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_08("نقش سفارشی 8", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_09("نقش سفارشی 9", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_10("نقش سفارشی 10", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_11("نقش سفارشی 11", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_12("نقش سفارشی 12", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_13("نقش سفارشی 13", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_14("نقش سفارشی 14", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_15("نقش سفارشی 15", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_16("نقش سفارشی 16", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_17("نقش سفارشی 17", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_18("نقش سفارشی 18", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_19("نقش سفارشی 19", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_20("نقش سفارشی 20", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_21("نقش سفارشی 21", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_22("نقش سفارشی 22", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_23("نقش سفارشی 23", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_24("نقش سفارشی 24", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_25("نقش سفارشی 25", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_26("نقش سفارشی 26", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_27("نقش سفارشی 27", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_28("نقش سفارشی 28", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_29("نقش سفارشی 29", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_30("نقش سفارشی 30", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_31("نقش سفارشی 31", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_32("نقش سفارشی 32", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_33("نقش سفارشی 33", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_34("نقش سفارشی 34", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_35("نقش سفارشی 35", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_36("نقش سفارشی 36", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_37("نقش سفارشی 37", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_38("نقش سفارشی 38", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_39("نقش سفارشی 39", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_40("نقش سفارشی 40", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_41("نقش سفارشی 41", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_42("نقش سفارشی 42", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_43("نقش سفارشی 43", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_44("نقش سفارشی 44", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_45("نقش سفارشی 45", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_46("نقش سفارشی 46", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_47("نقش سفارشی 47", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_48("نقش سفارشی 48", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_49("نقش سفارشی 49", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_50("نقش سفارشی 50", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_51("نقش سفارشی 51", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_52("نقش سفارشی 52", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_53("نقش سفارشی 53", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_54("نقش سفارشی 54", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_55("نقش سفارشی 55", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_56("نقش سفارشی 56", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_57("نقش سفارشی 57", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_58("نقش سفارشی 58", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_59("نقش سفارشی 59", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, ""),
    CUSTOM_60("نقش سفارشی 60", MafiaSide.CITY, MafiaGlyph.QUESTION, "", 0, 200, "");

    val isMulti: Boolean get() = this == CITIZEN || this == MAFIA

    /** Original name, before scenario names or the narrator's edits. */
    val defaultName: String get() = baseName
    val defaultSide: MafiaSide get() = baseSide
    val defaultGlyph: MafiaGlyph get() = baseGlyph
    val defaultTagline: String get() = baseTagline
    val defaultWake: Int get() = baseWake
    val defaultIntroOrder: Int get() = baseIntro
    val faName: String get() = MafiaRoleRegistry.name(this) ?: baseName
    val side: MafiaSide get() = MafiaRoleRegistry.def(this)?.side ?: baseSide
    val glyph: MafiaGlyph get() = MafiaRoleRegistry.def(this)?.glyph ?: baseGlyph
    val tagline: String get() = MafiaRoleRegistry.def(this)?.tagline ?: baseTagline
    val wakeOrder: Int get() {
        val d = MafiaRoleRegistry.def(this)
        if (d?.wakes == false) return 0
        return MafiaRoleRegistry.scenarioWake[this] ?: d?.wakeOrder ?: baseWake
    }
    val introOrder: Int get() = MafiaRoleRegistry.def(this)?.introOrder ?: baseIntro
    /** A free slot for a role the narrator adds himself. */
    val isCustomSlot: Boolean get() = name.startsWith("CUSTOM_")
    /** Rules defined as data (scenario roles and the narrator's own roles), not hand-coded. */
    val isGeneric: Boolean get() = ordinal > JOKER.ordinal || MafiaRoleRegistry.overrides(this)
    /** Built-in roles whose rules are data (scenario roles and custom slots). */
    val isDataRole: Boolean get() = ordinal > JOKER.ordinal
}

object MafiaRoleText {
    fun explain(role: MafiaRole): String =
        (MafiaRoleRegistry.user[role]?.explain ?: MafiaGenericRoles.builtin[role]?.explain ?: baseExplain(role).replace(role.defaultName, role.faName))
            .ifBlank { MafiaRoleRegistry.describe(role) }

    fun intro(role: MafiaRole): String =
        (MafiaRoleRegistry.user[role]?.intro ?: MafiaGenericRoles.builtin[role]?.intro ?: baseIntro(role).replace(role.defaultName, role.faName))
            .ifBlank { if (role.side == MafiaSide.MAFIA) "${role.faName} خودش را به راوی و بقیه افراد مافیا معرفی کند" else "${role.faName} خودش را به راوی معرفی کند" }

    private fun baseExplain(role: MafiaRole): String = when (role) {
        MafiaRole.CITIZEN -> "شهروند ساده نقش خاصی ندارد و جزء شهروندان است. مثل بقیه شهروندان باید تلاش کند از لا به لای داده‌ها و صحبت‌ها و لایک‌ها و دیس‌لایک‌ها و تمامی اتفاقات شب و روزهای بازی، مافیاها را پیدا کند و با رای دادن درست، آنها را یک به یک در روز از بازی بیرون بیندازد و شهر را نجات دهد. او و تیمش برنده بازی خواهند بود تنها و تنها اگر تمامی مافیاهای بازی یکی یکی از بازی بیرون بروند."
        MafiaRole.PROFESSIONAL -> "او به تعداد مشخص شده در بازی می‌تواند به یک مافیا شلیک کند، اما با شلیک اشتباه به شهروندان خودش از بازی خارج می‌شود. کار او به عنوان یک شهروند، این است که مافیا و یا مستقل شکار کند. یعنی اگر برایش قطعی و مسجل شد که کسی مافیا و یا مستقل است، خودش می‌تواند در شب بیدار شود و مستقیما او را مورد هدف قرار دهد. در این صورت اگر دکتر لکتر و یا دکتر او را نجات نداده باشد، آن مافیا و یا مستقل از بازی خارج می‌شود. نکته مهم این است که اگر اشتباه کند و قصد کشتن یک شهروند را داشته باشد تیرش به خطا می‌رود و آن شهروند از بازی بیرون نمی‌رود و در عوض خودش به جریمه ی این بی‌دقتی، از بازی حذف خواهد شد و دیگر قابلیت بازگشت به بازی را نخواهد داشت. پس در هر لحظه از شلیک کردنش دارد با جان خودش هم بازی می‌کند!\nالبته حرفه ای میتواند تعدادی زره داشته باشد که در صورت شلیک به او از شلیک جان سالم به در ببرد"
        MafiaRole.SNIPER -> " اسنایپر در گروه شهروندان ميباشد که به تعداد مشخص شده در بازی تير دارد و  تیرش قطعا کشنده خواهد بود البته به شرطی که فرمانده در بازی باشد و تیرش را تایید کند و در صورت نبود فرمانده تیرش همانند حرفه ای است اگر به اشتباه شهروند را بزند خودش از بازی بیرون خواهد رفت و دیگر قابلیت بازگشت به بازی را نخواهد داشت \n البته این فرمانده است که تیر او را تایید یا رد میکند و اگر او در بازی نباشد وظیفه اش را فرمانده بر عهده دارد"
        MafiaRole.INHERITOR -> "وارث شخصی است که در شب معارفه از خواب بیدار میشود و یکی را انتخاب میکند که از او ارث بری کند\nدر صورتی که شخص انتخاب شده نقش شهروندی داشته باشد او تا زمانی که شهروندش در بازی باشد نامیرا خواهد بود و زمانی که شهروندش از بازی بیرون برود نقش او را به ارث خواهد برد و نامیرایی اش از بین خواهد رفت بدیهی است که اگر یکی از نقش های مستقل و یا مافیا یی را انتخاب کند نامیرایی ندارد و با مردن شخص انتخاب شده نقشش را به ارث نخواهد برد"
        MafiaRole.SUSPECT -> "مظنون بازی یک شهروند ساده است اما استعلامش برای کارآگاه همیشه مثبت است او به همراه شهروندان ساده بازی میتواند در عملیات سوداگری پدرخوانده به گروه مافیا بپیوندد پس باید مراقب ایفای نقش خود باشد"
        MafiaRole.COMMANDER -> "فرمانده بازی پس از آنکه اسنایپر تیرش را شلیک کرد بیدار می شود و می تواند تایید یا عدم تایید به شلیک اسنایپر دهد و در صورتی که عدم تایید بدهد تیر اصابت نخواهد کرد و گلوله به خشاب اسنایپر باز خواهد گشت البته او در نبود اسنایپر خود وظیفه تیراندازی را بر عهده دارد و باید مراقب شلیک های خود باشد چرا که اگر اشتباه کند و به شهروندان شلیک کند خودش از بازی خارج خواهد شد و دیگر قابلیت بازگشت به بازی را نخواهد داشت."
        MafiaRole.DETECTIVE -> "او به تعداد مشخص شده در بازی میتواند استعلام یک نفر را از گرداننده برای تعیین وضعیت شهروند یا مافیا بگیرد. البته پدرخوانده همیشه به او شهروند معرفی می‌شود. کارآگاه، شهروندی است که هر شبی که توانایی انجام کار دارد یک فرصت استعلام دارد. به این شکل که از گرداننده بازی سوال می‌کند که فلان بازیکن مافیا هست یا خیر. با جواب‌هایی که از گرداننده می‌گیرد سعی می‌کند تیم شهروندی مطمئنی تشکیل دهد و به جنگ مافیاها برود. نکته مهم این است که استعلام پدرخوانده برای کارآگاه همیشه منفی است و همچنین اگرشارلاتان شخصی را انتخاب کرده باشد گرداننده جواب معکوس در خصوص آن شخص به کارآگاه بر میگرداند و او را به اشتباه خواهد انداخت.\nواگر شیاد او را انتخاب کند هر کسی را که استعلام بگیرد گرداننده به او جواب منفی خواهد داد"
        MafiaRole.SNOWMAN -> "آدم برفی جز شهروندان هست و یک گلوله برفی دارد که در شب شلیک می کند اگر به شهروند و یا یکی از افراد مستقل شلیک کند راوی در روز میگوید گلوله برفی آب نشد و در شب بعد، گلوله دست آن شخص خواهد بود (در ابتدای شروع کار شخص صاحب گلوله برفی مشخص میشود).شما با این قابلیت می توانید حلقه شهروندی شکل دهید.\n البته هیچ اجباری در پرتاپ گلوله برفی وجود ندارد"
        MafiaRole.DOCTOR -> "او میتواند به تعداد مشخص شده در بازی  جان یک نفر را از شلیک شب نجات دهد. او جان خودش را نیز به هر تعداد که در بازی مشخص شده است  می‌تواند نجات دهد. این نقش هم جزو نقش‌های کلاسیک و قدیمی بازی‌های مافیا به شمار می‌رود. دکتر، شهروندی است که فرصت دارد تا کسی را از تیر خوردن نجات دهد. قاعدتا دکتر باید خود را جای مافیاها بگذارد و سعی کند حدس بزند که امشب مافیاهای بازی چه کسی را برای کشتن انتخاب کرده‌اند. اگر حدس او درست باشد، دکتر یار شهروندش را از خطر تیر خوردن و حذف از بازی نجات می‌دهد و در بازی نگهش می‌دارد.\n دکتر در بازی چون ناآگاه است میتواند مافیاها و یا مستقل ها را از خطر تیرخوردن نجات دهد پس باید مراقب باشد که کسی را به اشتباه نجات ندهد"
        MafiaRole.PROTECTOR -> "محافظ یکی از اعضای تیم شهروندی است که در شب ها هیچگونه فعالیتی ندارد، فعالیت او در روز و هنگامی که بمب گذار در بین شهروندان بمب گذاری کند آغاز خواهد شد\nاو میتواند به جای بازیکنی که بمب در مقابلش قرار گرفته اقدام به خنثی سازی بمب کند یا میتواند از انجام این کار خودداری کند\nسوال در خصوص خنثی سازی بمب از محافظ در خواب نیمروزی انجام میشود\nاگر بمب در مقابل او قرار گرفته باشد رمز خنثی سازی به او گفته میشود\nاگر شعبده باز نقش او را مسدود کرده باشد او توانایی خنثی سازی بمب را نخواهد داشت"
        MafiaRole.OCEAN -> "اوشن یکی از شهروندان است و به تعداد مشخص شده در طول بازی میتواند یکی از شهروندان رو بیدار کنه و به تیم خود اضافه کند. یارهای اوشن در شب با هم میتوانند مشورت کنند ولی حق این را ندارند که در خصوص نقش های خود صحبت کنند. اگر اوشن به اشتباه مافیا یا یکی از نقش های مستقل را بیدار کند، صبح روز بعد خودش به تنهایی از بازی خارج خواهد شد."
        MafiaRole.ATTAR -> "عطار شب به تعداد قابلیت هایی که دارد از خواب بیدار میشود و به یک نفر زهر میخوراند\n فردای آن شب (در روز) گرداننده اعلام میکند که زهر وارد بازی شده است در ابتدای شب گرداننده باید تکلیف سم را مشخص کند\nدر ابتدای شب همه افراد حاضر در بازی باید برای خروج بازیکنی که زهر دارد رای گیری کنند و حداقل یاید نصف بازیکنان منهای 1 رای داشته باشد تا رای به حد نصاب خروج برسد ولی در نهایت این عطار است که تصمیم میگیرد که پادزهر را به بازیکن بدهد و یا خیر \nدر صورتی که عطار در بازی نباشد و یا به هر دلیلی قدرت انجام کار نداشته باشد ملاک خروج بازیکن همان تعداد آرا می باشد\nالبته اگر در تنظیمات نقش پدرخوانده پادزهر عطار روشن باشد پدرخوانده از زهر مصون است و فقط اعلام می شود که عطار پادزهر را به بازیکن داد حتی در صورتی که عطار مایل به دادن پادزهر نباشد و یا عطار از بازی بیرون رفته باشد\nسپس عطار تصمیم میگیرد که پادزهر را به او بدهد یا خیر\nدر صورتی که به هر دلیل پادزهر داده نشود شخص در همان لحظه از بازی بیرون خواهد رفت"
        MafiaRole.SAINT -> "قدیس نقش مقابل افسونگر است و اگر انتخاب های قدیس و افسونگر یکی باشد کار افسونگر ترتیب اثر داده نخواهد شد و همچنین قدیس از ست شدن کلمه بر روی خودش مصون است"
        MafiaRole.SALVATION_ANGEL -> "فرشته نجات یکی از شهروندان است که به تعداد مشخص شده در طول بازی، می‌تواند یکی از افراد کشته‌شده در همان شب را به انتخاب خود به بازی بازگرداند. در واقع او آخرین نفری است که در شب از خواب بیدار میشود و گرداننده به او لیست کشته های آن شب را میدهد اگر بخواهد، میتواند یک نفر رو به بازی بازگرداند.\nدر این سناریو اولاً فرشته نجات اجباری برای استفاده از قابلیتش ندارد و در صورتی که خودش جزو کشته ها آن شب باشد، تنها می‌تواند خودش رو نجات دهد.\nاگر فرشته نجات خودش جز کشته‌ها باشد، گرداننده باید مرحله‌ بیدار کردن فرشته نجات را به صورت تصنعی انجام دهد تا مشخص نشود که فرشته نجات کیست؟"
        MafiaRole.CHAPLIN -> "چاپلین د رگروه شهروندان وظیفه محافظت از شهروندان ساده و نقش های قابل خریداری را دارد به طوری که در شب اول بازی یک نفر را انتخاب میکند و آن شخص اگر قابل خریداری باشد تا  انتهای بازی در مقابل خریداری تیم مافیا در امان خواهد بود"
        MafiaRole.SMITH -> "آهنگر نقش مقابل مرد قوی است او از افراد گروه شهروندان است و میتواند به ازای انتخاب های مرد قوی به افراد انتخاب شده زره بدهد این زره برای تمام طول بازی در اختیار بازیکن قرار میگیرد و از بازیکنان در مقابل شلیک محافظت میکند این زره در روز نیز از خروج بازیکن جلوگیری میکند اگر فردی مورد حمله قرار بگیرد و توسط آهنگر محافظت شده باشد زره از تنش خواهد افتاد"
        MafiaRole.HERO -> "از اعضای تیم شهروندی است و در شب نامیراست\nولی در روز هیچ سپری ندارد و همانند بازیکن معمولی است.\nرویین تن در عملیات سوداگری،  خرید یا مذاکره همانند یک شهروند ساده می باشد و باید خیلی مواظب باشد که نقشش افشا نشود چون هر لحظه ممکن است مافیاها سراغش بیایند."
        MafiaRole.SPIDER -> "عنکبوت می تواند با توجه به تصمیم گرداننده به تعدادی که به او اعلام می شود چند نفر را به هم ببافد ! و اگر آن اشخاص در طول روز به هر دلیلی از بازی خارج شوند بافته شده ها نیز خارج می شوند."
        MafiaRole.INTERROGATOR -> "از اعضای تیم شهروندی است که در یکی از شب ها دو نفر از افراد مضنون را برای بازپرسی به گرداننده معرفی میکند.\nروز بعد ، پس از اعلام وقایع شب، دو نفری که در بازپرسی هستند باید از خودشان دفاع کنند سپس تمام بازیکنان به خواب نیمروزی خواهند رفت و مجددا از بازپرس سوال می شود که آیا برای این دونفر رای گیری اجباری انجام بشود یا ادامه بازپرسی لغو گردد.\nاگر بازپرس رای گیری اجباری را انتخاب کند تمام بازیکنان موظف به دادن رای به یکی از آن دو نفر هستند و بازیکنی که بیشترین رای را دارد از بازی خارج میشود و بلافاصله سایدش توسط گرداننده به همه اعلام میشود و  سپس بازی با روند عادی یعنی صحبت کردن همه بازیکنان و رای گیری روزانه ادامه می یابد‌."
        MafiaRole.RESEARCHER -> "از اعضای تیم شهروندی است و  هر شب می تواند خودش را به یک نفر پیوند بزند اگر  فرد انتخاب شده از اعضای مافیا (به جز پدرخوانده) باشد\n محقق آن شب یا روز بعد به هر نحوی از بازی خارج بشود، مافیای انتخاب شده نیز از بازی خارج میشود.\nدر صورت انتخاب اعضای شهروندی، مستقل و یا پدرخوانده ، محقق به تنهایی از بازی خارج میشود."
        MafiaRole.COMMANDO -> "تکاور شهروندی است که زمانی که مورد حمله گروه مافیا قرار میگیرد حق تیر اندازی دارد.\nتکاور بازی در شبی که تیر اندازی میکند باید مطمئن باشد شخصی را که مورد هدف قرار میدهد مافیا باشد و اگر به اشتباه شهروند را مورد هدف قرار دهد خودش از بازی خارج می شود\nالبته اگر پدرخوانده را بزند هیچ کس از بازی خارج نمیشود."
        MafiaRole.GUNMAN -> "تفنگدار تفنگ های زیادی دارد ولی به دلیل کهولت سن نمیتواند از آنها استفاده کند\nبنابراین او به ناچار تفنگ هایش را به دیگران می دهد تا استفاده کنند\nافرادی که در شب تفنگ دریافت کرده اند از نوع تفنگی که گرفته اند خبر ندارند (تفنگ مشقی و تفنگ جنگی) و فقط در طول روز بعدی پیش از شروع رای گیری حق استفاده از تفنگ شان را دارند.\nدر ضمن شخصی که تفنگ دریافت کرده است اجباری برای شلیک ندارد و اگر در تنظیمات نقش، فایربک تنظیم شده باشد باید از اسلحه خود استفاده کند و در صورتی که از اسلحه خود استفاده نکند و تقنگ جنگی در دست داشته باشد تفنگ به سمت خودش نشانه میرود و او را مورد هدف قرار میدهداما در صورت اقدام به تیراندازی، این کار در روز و مقابل چشم همگان انجام میشود\nپس از شلیک تفنگ توسط افراد، راوی اعلام می کند که تفنگ جنگی بوده یا مشقی. در صورتی که تفنگ مشقی باشد هیچ اتفاقی نمی افتد، اما در صورت جنگی بودن تفنگ شخصی که مورد هدف قرار گرفته از بازی خارج می شود \nتفنگدار نمیتواند به خودش تفنگ جنگی بدهد و فقط مشقی می تواند بدهد. \nاگر کسی که تفنگ دریافت کرده باشد کشته شب باشد ، تفنگ به تفنگدار برمیگردد و می تواند در شب های بعدی آن را به افراد دیگری بدهد."
        MafiaRole.JUPITER -> "از شهروند ساده های شهر، رویین تن و جان سخت بدون زره در مقابل یاکوزایی شدن و پیوستن به تیم مافیا جلوگیری میکند.او به تعداد تنظیم شده در بازی میتواند انجام وظیفه نماید"
        MafiaRole.CITIZEN_KANE -> "شهروندی است که در یکی از شب ها به انتخاب خود به دعوت گرداننده یکی از بازیکنان را نشان می دهد\nاگر یک مافیا را درست نشان کرده باشد صبح روز بعد گرداننده ساید مافیای نشان شده را در جمع افشا می کند.و همشهری کین شب بعد کشته می شود.\nدکتر توانایی نجات او را ندارد.اما اگر نشانش از ساید مافیا نبود گرداننده هیچ چیزی اعلام نخواهد کرد و همشهری کین در بازی خواهد ماند و استعلامش از بین خواهد رفت.\nاگر او یا نشانش کشته شوند عملیات شب وی اجرا نشده و از بین نمی رود و همچنان باقی می ماند.استعلام پدرخوانده برای همشهری کین مافیایی است."
        MafiaRole.CONSTANTINE -> "نقش کنستانتین به تعداد مشخص شده در تنظیمات نقش میتواند از بازیکنان خارج از بازی (اجماع روز یا کشته شب) که افشای هویت نشدند را به بازی برگرداند.\nلازم به ذکر است توانایی و تعداد قابلیت های بازیکنی که بازی بر میگردد هیچ گونه تغییری نمیکند و با همان وضعیت در زمان خروج، ادامه بازی را انجام می دهد"
        MafiaRole.RUSSIAN_ROULETTE -> "رولت روسی : کسی که این نقش را در بازی دارد یک تفنگ در شب به یکی می دهد سپس به راوی بازی یک عدد از 3 تا 7 اعلام می کند سپس کسی که در روز تفنگ دارد می تواند به یکی شلیک کند و آن شخص تیر خورده مجدد تیر می زند تا به عدد اعلامی برسد و سپس آن شخص از بازی خارج می شود."
        MafiaRole.SACRIFICE -> "نقش فدایی در شب هیچ فعالیتی ندارد.\nولی در روز یک بار میتواند خود را فدای شهر کند و به جای کشته روز (اجماع رای گیری ، کشته صاحب تیر جنگی،  کشته ترور شده توسط تروریست و ....)  از بازی خارج شود.\nلازم به ذکر است فدایی با اعلام کلمه فدایی این اجازه را دارد که از توانایی نقشش استفاده کند پس اگر توسط روانپزشک یا هر نحوی دیگری ساکت باشد قادر به استفاده از توانایی خود نیست"
        MafiaRole.COWBOY -> "کابوی جز تیم شهروندان است و در هر جای بازی که حس کند ممکن است در روز با رای گیری از بازی خارج شود می تواند قبل از شروع رای گیری با افشای نقش خود می تواند یک نفر را با خود از بازی بیرون ببرد البته پس از خروج فرد انتخاب شده حق انتخاب کارت حرکت آخر را دارد."
        MafiaRole.SUICIDE_BOMBER -> "بمبر بازی جز تیم شهروندان است و اگر در شب مورد حمله قرار بگیرد و کشته شود دو نفر سمت راست و چپ او نیز می میرند و همچنین در طول روز قبل از رای گیری هر زمان بخواهد می تواند با اعلام کلمه بمبر برود و بین دو نفر بترکد و هر سه نفر از بازی خارج شوند."
        MafiaRole.DIE_HARD -> "با شلیک اول از سوی مافیا کشته نمی‌شود و البته از شلیک اول نیز بی‌خبر است. گرفتن استعلام نفرات خارج شده از بازی، به عهده اوست و او میتواند به تعدا مشخص شده در بازی استعلام وضعیت بگیرد. او وظیفه دارد هرگاه که لازم دانست، در شب درخواست اعلام وضعیت از گرداننده داشته باشد. اگر این اتفاق بیفتد، گرداننده صبح روز بعد، پس از اعلام نتایج و کشته‌ها، تکلیف همه نقش‌هایی که تا آن زمان از بازی بیرون رفته‌اند را مشخص می‌کند. یعنی می‌گوید از بازی، این تعداد مافیا با این نقش‌ها و این تعداد شهروند با این نقش‌ها بیرون رفته‌اند. به این شکل، تکلیف بازی برای کسانی که هنوز داخل بازی هستند روشن می‌شود. جان سخت یک جان اضافه هم دارد و با اولین باری که تیر بخورد از بازی حذف نمی‌شود. البته در شبی که تیر میخورد اگر دکتر او را نجات دهد همچنان زره بر تن باقی می ماند"
        MafiaRole.MAYOR -> "او به تعداد مشخص شده در بازی می‌تواند دور دوم رای‌گیری را ملغی کند و از خروج یک بازیکن جلوگیری نماید، یا یکی از افراد مورد اتهام را بدون رای‌گیری به صورت مستقیم از بازی خارج کند. به این شکل، شهردار می‌تواند رای‌گیری دور دوم در روز را ملغی کند و باعث شود هیچ‌کس از بازی در صبح بیرون نرود. البته ذکر این نکته حائز اهمیت است که اگر شهردار خود در دفاعیه باشد قابلیت انجام هیچ کاری را ندارد"
        MafiaRole.SALESMAN -> "فقط یک بار میتواند در طول بازی نقش یک بازیکن را از او تا انتهای بازی بگیرد و بازیکن هیچ قابلیتی نداشته باشد البته اگر فروشنده جان سخت را نتخاب کند اگر جان سخت زره بر تن داشته باشد فقط زره او را می گیرد در غیر اینصورت قابلیت استعلام گرفتن را از او میگیرد"
        MafiaRole.REPORTER -> "خبرنگار یکی از اعضای شهر است که بعد از انجام خرید کار خود را شروع می‌کند و احتمال دارد تا آخر بازی خریدی صورت نگیرد و او همانند شهروند ساده}، ناآگاه باقی بماند.\nاز طرفی معمولاً مذاکره در انتهای بازی انجام می‌شود و زمانی است که خبرنگار می‌تواند اتفاقات شهر را پردازش کند.\nبهتر است خبرنگار خود را جای مافیای ذهنی‌اش بگذارد و بررسی کند اگر جای آنها بود، با کدام یک از شهروندان مذاکره می‌کرد.\nدر اصل شرایط به گونه‌ای است که خبرنگار بعد از خریداری، استعلام شهروندهای ذهنی خود را می‌گیرد تا بداند آنها به مافیا تبدیل شده‌اند یا خیر.\nهمچنین خبرنگار می‌تواند از ذهن شهروندی خود استفاده کند و اگر تناقضی در صحبت‌های شهروند ذهنی خود دید، در شب استعلام او را از گرداننده بخواهد.\nگفتنی است اگر مافیا درخواست خریداری کند و خریداری با موفقیت انجام نشود هم، خبرنگار در شب بیدار می‌شود و استعلام بازیکنان مشکوک را می‌گیرد و فعالیت او با ناموفقی و یا موفقیت‌آمیز بودن خریداری ارتباطی ندارد."
        MafiaRole.HACKER -> "هکر جزء گروه شهروندان است و باید با قابلیتی که دارد مافیاهای بازی را هک کند\nهکر در شب دوم بازی (پس از ۲ روز صحبت) لیستی به گرداننده بازی ارائه میکند این لیست شامل 3 نفر از بازیکنان حاضر در بازی است\nصبح روز بعد گرداننده بازی گزارشی از وضعیت لیست به بازیکنان ارائه میکند\nاما گزارش بازی به چه صورت بیان میشود؟\nهنگامی که تنها ۱ نفر در لیست جز گروه مافیا باشد لیست خطرناک اعلام می شود\nاگر 2 نفر از آنها مافیا باشد و یا هیچ مافیایی در لیست نباشد لیست غیر خطرناک اعلام میشود\nو اما اگر هر ۳ نفر در لیست مافیا باشند انفجار رخ میدهد و در روز بعد هر 3 نفر از بازی خارج میشوند\n\nتوضیحات کلی :\nاگر در لیست یک پدرخوانده باشد به شرطی که قدرتش توسط فروشنده گرفته نشده باشد همانند شهروند عادی با او برخورد میشود و نتیجه لیست اعلام میشود اما اگر قدرت بازیکن توسط فروشنده گرفته شده باشد حتما مافیا در نظر گرفته می شود\nاگر پدرخوانده همراه دو مافیای دیگر در لیست باشد باز هم انفجار رخ میدهد\nاگر در روز قبل از شب دوم کارت بی خوابی برای بازیکن بیاید و ناچار به بی خوابی باشیم دیگر هکر قدرت انجام کار را نخواهد داشت\nهیچ گاه اسامی موجود در لیست به بازیکنان اعلام نخواهد شد\nبهتر است از این نقش زمانی استفاده کنید که تعداد مافیا در شهر بیشتر از 6 نفر است"
        MafiaRole.CLEVER -> "نخبه یکی از شهروندان است و تمام توانش را به کار میبندد تا بتواند شهروندان را در شناسایی بهتر مافیا ها کمک کند\nوظیفه او در شب این است که از گرداننده میتواند 2 نوع درخواست داشته باشد\n1-\\tیا نقش یک بازیکن را از او بپرسد (برای مثال بپرسد فلان شخص چه نقشی دارد) و راوی نقش دقیق را به نخبه میگوید (نه ساید بازی اش را)\n2-\\tدر مورد صاحب یک نقش از گرداننده میتواند سوال بپرسد (برای مثال بپرسد دکتر بازی کیست؟) او نیمتواند در مورد صاحب نقش های مافیایی سوال بپرسد\nنکته : اگر نخبه در خروج کارت حرکت آخر هوش و ذکاوت را انتخاب کند نمیتواند یکی از نقشهایی را که میشناسد اعلام کند در این صورت گرداننده جواب معکوس به او خواهد داد و از بازی خارج میشود"
        MafiaRole.MASON -> "فراماسون بازی جز تیم شهروندان است و هر شب بیدار می شود و کسی که فکر می کند شهروند است را به گرداننده نشان می دهد و اگر آن شخص شهروند باشد بیدار می شود و همدیگر را می شناسند و این روند همینجور جلو می رود البته این شهروندان فقط همدیگر را می شناسند و حق اشاره و یا حرف زدن درباره نقششان در بازی باهم را ندارند و فقط می توانند مشورت کنند ولی اگر به اشتباه در شبی مافیا و یا مستقلی را بیدار کنند همه شهروندان گروه ماسون باهم از بازی حذف می شوند.\nالبته این جاسوس است که میتواند وارد گروه فراماسون شود و آنها را بشناسد و در صورت حذف شدن گروه فراماسون همه گروه ها به جز جاسوس از بازی خارج میشوند\n البته این نکته را هم باید ذکر کرد که اگر تایلر و جاسوس در کنار فراماسون در بازی باشند در شب معارفه فراماسون باید یکی از این دو نفر را انتخاب کند و بازی بعد از آن ادامه پیدا خواهد کرد البته به فراماسون گفته نمیشود که بازیکن انتخابی تایلر است یا جاسوس در صورتی که تایلر در گروه ماسون باشد بیدار کردن افراد بعد از فراماسون به عهده تایلر است"
        MafiaRole.TYLER -> "تایلر و یا بچه ماسون بازی در بازی مافیا شب معارفه با فراماسون بازی چشم باز می کند و همدیگر را می شناسند و برای تشکیل تیم ماسونی مشورت می کنند.\n وظیفه او همکاری با فراماسون برای پیدا کردن شهروندان خود است اگر او و فراماسون در بازی باشند حتما باید یکی را به عنوان شهروند انتخاب کنند و اگر فراماسون در بازی نباشد او وظیفه تشکیل حلقه شهروندی را دارد و اجباری برای بزرگتر کردن این حلقه نیست"
        MafiaRole.SLEEP_WALKER -> "خوابگرد پیش از همه بازیکنان در شب از خواب بیدار شده و یک بار در طول شب میتواند در ازای اینکه کل گروه مافیا شب نتوانند کاری انجام دهند، خود را فدا کند و در فردای آن شب از بازی خارج می‌شود."
        MafiaRole.PRIEST -> "کشیش بازی در طول شب می تواند اسم تعدادی از بازیکنان را که این تعداد در هنگام انتخاب نقش قابل تنظیم است را به گرداننده بگوید و این افراد در روز بعد پس از اتمام صحبت ها حق صحبت اضافی دارند و همچنین اگر سایلنسر کسی را سکوت داده باشد از این افراد از سایلنتی در می آید."
        MafiaRole.PSYCHOLOGIST -> "روانپزشک به تعدا مشخص شده در بازی می‌تواند قدرت مکالمه بازیکنی را بابت روان‌درمانی از او بگیرد و او را به مدت ۲۴ساعت ساکت کند. او یک شهروند است و می‌تواند هر شبی که بیدار شد یک نفر را انتخاب نماید. فرد مورد نظر ۲۴ ساعت لال می‌شود و حق حرف زدن ندارد. البته می‌تواند تایید و عدم تایید نشان دهد و در رای‌گیری‌ها شرکت نماید و همچنین اگر در همان روز سکوت با رای اکثریت از بازی خارج شود برخی از کارت های حرکت آخر مانند سرگیجه و هوش و ذکاوت که نیاز به صحبت کردن دارد برای او بدون استفاده خواهد بود"
        MafiaRole.GUARDIAN -> "نگهبان نقش مقابل ماتادور و شعبده باز است که اولین نفر قبل از گروه مافیا بیدار می شود و از نقش یک یا دو نفر در برابر ماتادور و یا شعبده باز نگهبانی می کند و اجازه نمی دهد نقش این افراد گرو گرفته شود.\nاگر نگهبان ماتادور  و یا شعبده باز را مورد انتخاب قرار دهد شب توانایی شان را برای آن شب از دست خواهند داد.\nنگهبان در روز زره دارد و در صورتی که در طول روز به هر نحوی از بازی خارج شود زره اش می افتد و نقش اش توسط راوی اعلام می شود و از آن به بعد به عنوان شهروند ساده به بازی ادامه میدهد."
        MafiaRole.GUARD -> "مراقب یکی از شهروندان است که هرشبی که بخواهد کارش را انجام دهد یکی نفر را انتخاب میکند و آن یک نفر در همان شب از دست بازجو در امان خواهد بود و بازجو قدرت بازجویی آن شخص را ندارد"
        MafiaRole.SPECIAL_DETECTIVE -> "هر شب بخواهد می تواند یک نفر را انتخاب کند ، گرداننده به او می گوید که شخصیت مستقل است یا نه.\nالبته او فقط به تعداد مشخص شده میتواند استعلام منفی بگیرد و بیش از آن باعث خروج بی قید و شرطش از بازی میشود"
        MafiaRole.SABA -> "نقش صبا در بازی مافیا شباهت زیادی به نقش دزد دارد؛ با این تفاوت که صبا برای شهروندان بازی می کند. این تفاوت فقط یک تفاوت صوری نیست بلکه صبا باید هوشیارتر از دزد باشد چراکه در مجموع از هویت بازیکنان دیگر ناآگاه است و باید تلاش کند تا حد ممکن بهترین نقش را برای دزدی انتخاب کند\n\nنقش صبا در بازی مافیا:\nنحوه استفاده صبا از نقش خود در طول بازی به این صورت است که شب با اعلام گرداننده، از خواب بیدار می‌شود، نقش یکی از بازیکنان را به دلخواه خود از گرداننده استعلام می‌کند.\nگرداننده نقش را به او اعلام می‌کند و تا فردا شب همان موقع، نقش از آنِ صبا می‌شود. ضمن آنکه بازیکنی که دارای آن نقش بوده است نیز مطلع می‌شود که نمی‌تواند از نقش خود در ۲۴ ساعت آینده استفاده کند.\n\nقابلیت های صبا در بازی مافیا:\nدزدی نقش یکی از بازیکنان به مدت ۲۴ ساعت (به تعدا مشخص شده)\nقدرت استفاده از آن نقش در روز و شب بعد\nمطلع شدن از نقش بازیکنی که نقش را از او دزدیده است که نوعی قابلیت کارآگاهی است\n\nمحدودیت‌های نقش صبا در مافیا:\nالزام به استفاده نقش در شب دزدی: صبا نمی‌تواند از انجام نقشی که دزدیده است، سرپیچی کند\n\nسایر جزئیات نقش صبا:\n۱. ترتیب بیدار شدن نقش صبا با توجه به اینکه حتماً باید در ۲۴ ساعت آینده از نقش دزدیده شده استفاده کند، جزو آخرین موارد است.\n۲. چنانچه صبا نقش دزد را بدزدد، می‌تواند یک شب اضافه از قابلیت خود استفاده کند؛ چراکه نقش دزد نیز مشابه نقش صبا مربوط به دزدی نقش است و بنابراین نقش صبا را مضاعف می‌کند.\n۳. اگر صبا نقش پدرخوانده را بدزدد، اجازه ندارد همراه با مافیا از خواب بیدار شود و صرفاً در آن شب، استعلام پدرخوانده اصلی، مثبت می‌شود.\n4. بهتر است در بازی‌های با جمعیت بیش از ۱۵ نفر مورد استفاده قرار گیرد."
        MafiaRole.MAFIA -> "ساده ترین مافیای بازی است. او از اعضای مافیاست که در شب با همراهی خود به پدرخوانده}، دکتر لکتر ، شارلاتان و دیگر اعضای مافیا مشورت می‌دهد. کار اصلی مافیاها در تمامی سبک‌های بازی مافیا این است که مراقب رفتار و گفتارشان در طول روز باشند و به اصطلاح سوتی ندهند و با هماهنگی با سایر اعضای تیم مافیا، سعی کنند نقش یک شهروند را بازی کنند و شهروندان را در روز به جان یکدیگر بیندازند. مافیا ساده قابلیت خاصی ندارد و همه حواسش باید به این باشد که در قامت یک مافیا، درست عمل کند و بهانه دست کسی ندهد."
        MafiaRole.GODFATHER -> "درحقیقت همان رئیس مافیاست و نقش رهبری و هدایت تیم مافیا را به عهده می گیرد. کشته ی هر شب مافیا، که با شور و مشورت مافیاها با هم انتخاب می شود، توسط پدرخوانده به گرداننده اعلام نهایی می گردد. البته اگر پدرخوانده در بازی نباشد بقیه اعضای مافیا این وظیفه را بر عهده دارند جواب استعلام پدرخوانده بر اساس تعداد تنظیم در انتخاب نقش برای کارآگاه شهروند است و تعیین شلیک شب از طرف گروه مافیا به عهده اوست.\nپدرخوانده میتواند دارای قابلیت هایی مانند داشتن زره، حس ششم و سوداگری باشد که در انتخاب نقش قابل تنظیم است"
        MafiaRole.PABLO -> "پابلو با تیم مافیا بیدار نمیشود ولی پدرخوانده خود را میشناسد در صورتی که پدرخوانده از بازی خارج شود چه در فاز شب یا فاز روز پابلو در شب بعدی میتواند نقش یک نفر را با پدرخوانده جا به جا کند آن شخص در سایدی که باشد چه شهروند چه مافیا و حتی چه مستقل به پدرخوانده تبدیل میشود و پدرخوانده نقش آن شخص را میکیرد یا به اصطلاح تغییر چهره میشود پابلو در طول کل بازی یکبار قابلیت انتخاب دارد و پس از انتخاب با یاران خود بیدار میشود"
        MafiaRole.CAST_AWAY -> "او نه مافیا را میشناشد و نه مافیا ها اورا می شناسند\nاو در سه شب اول بازی میتواند استعلام یکی از یاران خود را بگیرد و به شرط درست بودن استعلامش به گروه مافیا ملحق میشود و با آنها چشم باز میکند و به پاداش این استعلام فرصت استفاده از یک تیر اضافه در شلیک شب را با مشورت یارانش دارد\nدر صورتی که دور افتاده نتواند در سه شب اول بازی یکی از یاران خود را پیدا کند در روز چهارم از بازی خارج خواهد شد"
        MafiaRole.LOVER -> "او معشوقه پدرخوانده است و پدرخوانده به او عشق میورزد\nبا مرگ او پدرخوانده به قدری عصبانی خواهد شد که میتواند به 2 نفر حمله کند و آنها را از پای درآورد\nالبته به شرطی که در بازی باشد"
        MafiaRole.STRONG_MAN -> "مرد قوی یکی از افراد گروه مافیا است، او چنان قوی است که هر کسی را مورد هدف گلوله خود قرار دهد خواهد مرد البته به شرطی که آهنگر به آن شخص زرهی نداده باشد مرد قوی در ازای انجام کارش شلیک شب را از گروهش خواهد گرفت و به جای شلیک شب کار خودش را انجام میدهد"
        MafiaRole.WITCHER -> "جادوگر یکی از یاران پرقدرت مافیا است که هر کسی را در شب انتخاب کند نتیجه عملیات شخص انتخاب شده به خودش باز میگردد یعنی اگر کارآگاه را انتخاب کند انگاه کارآگاه استعلام خودش را گرفته و یا اگر دکتر را انتخاب کند انگار دکتر خودش را نجات داده"
        MafiaRole.VOODOO -> "افسونگر بازی جز تیم مافیا می باشد و هر شب می تواند یک کلمه ۴ حرف به بالا را روی یک نفر افسون کند و گرداننده در اول روز اعلام می کند که چه کلمه ای در آن روز انتخاب شده است و اگر آن شخص فردا در روز از آن کلمه استفاده کند از بازی حذف می شود و افسونگر دیگر قابلیت انتخاب نخواهد داشت اما اگر کسی به خاطر گفتن کلمه افسونگر از بازی خارج نشود افسونگر میتواند مجددا کلمه ای را برای یک بازیکن افسون کند."
        MafiaRole.NATO -> "جزء تیم مافیا می باشد و در طول بازی می تواند به تعداد مشخصی از توانایی ناتویی خود استفاده کند و نقش یک شهروند یا مستقل را حدس بزنند.\nدر صورت درست بودن حدسش آن شخص تحت هر شرایطی از بازی خارج می شود و در صورت اشتباه بودن حدس ناتو هیچ اتفاقی نمی افتد.\nلازم به ذکر است گروه مافیا در صورتی که از قابلیت ناتویی استفاده کند شات شب ندارد ."
        MafiaRole.MATADOR -> "ماتادور که یار هایش را می شناسد ولی به تنهایی کارش را انجام میدهد؛\nهر شب یکی از شهروند هارا گروگان میگیرد در صورتی که آن شهروند نقش دار باشد ، نمیتواند آن شب از توانایی اش استفاده کند. ماتادور حق ندارد برای دو شب متوالی یک نفر را گروگان بگیرد."
        MafiaRole.ILLUSIONIST -> "شعبده باز یکی از اعضای تیم مافیا است\nنقش او شباهت بسیاری با نقش ماتادور دارد ولی با این تفاوت که شعبده باز اگر شخصی را انتخاب کند آن شخص به مدت 24 ساعت نمیتواند از نقشش استفاده کند چه در روز و چه در شب\nنکته 1 : اگر شعبده باز کسی را انتخاب کند که صاحب تیر جنگی است تیر آن شخص به تیر مشقی تبدیل خواهد شد\nنکته 2 : شعبده باز در دو شب متوالی نمیتواند یک نفر را انتخاب کند\nنکته 3 : اگر شعبده باز محافظ را انتخاب کند و محافظ توسط زودیاک مورد حمله قرار بگیرد این محافظ است که از بازی خارج خواهد شد"
        MafiaRole.NATASHA -> "ناتاشا یکی از نقش های کلیدی تیم مافیاست.این نقش یکی از نقش های سناریو کلاسیک به حساب می آید.ناتاشا در تیم مافیا به تعداد مشخص شده میتواند یکی را انتخاب کند تا آن شخص در فاز روز ساکت شود.\nفرد ساکت شده در فاز روز نمیتواند حرف بزند.همچنین هیچ گونه اکت و لایک یا دیس لایکی نمیتواند بدهد.اگر هر کدام از این اشتباهات را مرتکب بشود از بازی خارج میشود.یکی از مهمترین ویژگی های ناتاشا این هست که میتواند برای شهروند نمایی، تیم خود را , و حتی خودش را نیز ساکت کند."
        MafiaRole.BOMBER -> "بمب گذار یکی از یاران مافیا است که به تعداد مشخص شده در طول بازی میتواند در مقابل یکی از بازیکن‌ها بمب قرار دهد\n. ابتدای روز گرداننده اعلام میکند که در مقابل این فرد بمب قرار گرفته است و پس از اتمام صحبت بازیکنان، بمب باید خنثی شود.پیش از خنثی سازی بمب توسط بازیکن در خواب نیمروزی از محافظ در خصوص خنثی سازی بمب سوال پرسیده خواهد شد.\nدر صورتی که محافظ تمایل به خنثی سازی بمب را داشته باشد باید عددی که بمب گذار در شب فعالسازی به گرداننده اعلام کرده را اعلام کند تا بمب خنثی شود\n در صورتی که محافظ تمایل به خنثی سازی بمب نداشته باشد پس از بیداری اعلام میشود که محافظ تمایل به خنثی سازی نداشت و خود بازیکن باید بمب را خنثی کند\n نکته 1 : در صورتی که بمب گذار بمب را در مقابل محافظ قرار دهد عدد خنثی سازی به محافظ گفته میشود و بمب خنثی خواهد شد\nنکته 2 : در صورتی که هر کدام از بازیکنان (چه محافظ و چه خود بازیکن) نتوانند بمب را خنثی کنند بدون هیچ وصیتی و بلادرنگ از بازی خارج خواهند شد \nنکته 3 : در صورتی محافظ نقشش توسط شعبده باز گرفته شده باشد محافظ توانایی خنثی سازی بمب را نخواهد داشت و اگر بمب در مقابل خود محافظ قرار بگیرد بمب قطعا منفجر خواهد شد"
        MafiaRole.GOOD_MAN -> "یکی از چالش‌های مافیا در شهر، یافتن شهروند ساده در بازی است. زمانی که یکی از یاران مافیا بیرون می‌رود، آنها به دنبال اضافه کردن بازیکنان به گروه خود هستند.\nبهتر است ساول گودمن، زمانی که یک اتفاقی به نفع شهروندان در شهر می‌افتد، به صحبت‌ها دقت کند و اگر از بازیکنان کسی آگاهی داشت که به اتفاقات شب مربوط می‌شد، برای خرید مورد مذاکره قرار ندهد.\nبه طور مثال اگر یک شب بازی نجات درست داشت یا حرفه ای درست به هدف زد، ساول گودمن باید دقت کند که استدلال کدام بازیکن به اطلاعات شب گذشته برمی‌گردد تا شهروندان نقش‌دار را انتخاب نکند.\nهمچنین در مقابل ساول گودمن، خبرنگار وجود دارد که بعد از خرید می‌تواند استعلام شهروند خریداری شده را بگیرد و اگر استعلام مافیا را بگیرد هم با پاسخ منفی مواجه خواهد شد.\nاگر در بازی فقط ساول گودمن باقی ماند و همه شهروندان به او به عنوان شهروند نگاه کردند و شک‌شان روی یک شهروند دیگر بود، بهتر است که در آن شب خریدی صورت نگیرد.\nچون به احتمال زیاد کارآگاه استعلام شهروند مشکوک را می‌گیرد و چون استعلام پدرخوانده هم منفی است، ممکن است حدس بزند او رئیس مافیا باشد.\nدر صورتی که اگر خرید صورت بگیرد، مشخص می‌شود که ساول گودمن در بازی است و شهروندیِ بازیکنی که گره شده برای کارآگاه مسجل شود.\nگفتنی است یکی از سناریوها برای مافیا این است که یکی از یاران خود را به شهروندان معرفی کرده و با خروج او یک شهروند جدید را به گروه اضافه کنند.\nاین سناریو غالباً برای افراد حرفه‌ای مناسب است و از آنجایی که موفقیت خرید پنجاه پنجاه است، چنین ریسکی می‌تواند برعکس نتیجه دهد."
        MafiaRole.FRAUD -> "شیاد یکی از اعضای گروه مافیا است\nوظیفه اش یافتن کارآگاه است و باید کار او را خراب کند\nشیاد بازی باید به دنبال کارآگاه بازی باشد و در صورتی که بتواند کارآگاه را انتخاب کند، استعلام هر شخص برای کارآگاه منفی خواهد بود البته به شرطی که شیاد در سرگیجه نباشد\nشیاد بازی به تنهایی کارش را انجام می دهد."
        MafiaRole.WRECKER -> "خرابکار جز تیم مافیا هست و به تعداد مشخص شده در بازی به ازای تفنگی که تفنگدار به شهر می دهد می تواند روی افراد خرابکاری کند ! یعنی اگر آن شخص تفنگ جنگی داشته باشد و در روز تیر بزند و خرابکار آن روز روی آن شخص خرابکاری کرده باشد تیر به خود شخص بر می گردد و از بازی خارج می شود."
        MafiaRole.YAKUZA -> "یاکوزا جز تیم مافیا می باشد و وقتی احساس کند که نقشش برای شهروندان نمایان شده است می تواند یکی را انتخاب کند و آن شخص از آن به بعد مافیا خواهد شد (به شرط داشتن نقش شهروند ساده، رویین تن و یا جان سخت که زره بر تن نداشته باشد) و خود یاکوزا می میرد (اگر دکتر او را نجات نداده باشد).اگر دکتر به اشتباه یاکوزا را نجات دهد یاکوزا در بازی می ماند و اگر قابلیتش باقیمانده باشد میتواند از قابلیتش استفاده کند\nنکته : دکتر لکتر نمیتواند یاکوزا را در صورت انجام عملیاتش نجات دهد"
        MafiaRole.TERRORIST -> "تروریست در اکثر سناریوها جز ساید مافیا قرار دارد و اعضای مافیا را می شناسد در طول روز پیش  از رای گیری دوم با اعلام کلمه تروریست میتواند یک نفر را همراه خود از بازی بیرون ببرد ....\nالبته ذکر این نکته نیز خالی از اهمیت نیست که با اخراج تروریست در روز میتواند بعد از کارت حرکت اخر یک نفر را با خود از بازی بیرون ببرد..."
        MafiaRole.CHARLATAN -> "یکی از اعضای نقش دار تیم مافیا است هر شبی که بخواهد کارش را انجام دهد بیدار میشود با کمک پدرخوانده یکی از بازیکنان حاضر در بازی را انتخاب می کند اگر کارآگاه بخواهد همان شب استعلام آن فرد را بگیرد گرداننده به او جواب معکوس میدهد. شارلاتان در بازی به تعداد مشخص شده میتواند کارش را انجام دهد او حق انتخاب پدرخوانده را نیز ندارد به شرطی که قدرت پدرخوانده توسط فروشنده گرفته نشده باشد\nلازم به ذکر است شارلاتان در عملکرد کارآگاه ویژه بی تاثیر است"
        MafiaRole.DOCTOR_LECTOR -> "دکتر ساید مافیاست و به تعداد مشخص شده در بازی می تواند هر شب یک نفر از ساید مافیا را در مقابل تیر خوردن و بیماری کرونا نجات بدهد.\nاو جان خودش را نیز به تعداد مشخص شده می تواند نجات بدهد"
        MafiaRole.BODYGUARD -> "بادیگارد یک زره بر تن دارد و بار اول از شلیک شب آسیبی نمی‌بیند؛\nالبته این نکته را در نظر داشته باشید که اگر صبا و یا دزد (انتخاب اول) او را انتخاب کنند او در آن شب زره بر تن ندارد و با شلیک از بازی بیرون میرود\nهمچنین تا زمانی که در بازی باشد، شلیک شب بر روی پدرخوانده تاثیری ندارد. و اگر تیری به سمت پدرخوانده شلیک شد به سمت بادیگارد می‌رود"
        MafiaRole.INVESTIGATOR -> "نقش بازجو شباهت زیادی به نقش کارآگاه در بازی مافیا دارد؛ با این تفاوت که کارآگاه یکی از شهروندان است اما بازجو به عنوان یک مافیا از قابلیتش استفاده می‌کند.\nالبته این تنها تفاوت این دو نقش نیست.زمانی که بازجو از گرداننده استعلام وضعیت می‌گیرد یا اصطلاحا حکم بازجویی به گرداننده می‌دهد، از نقش بازیکن مورد نظر و هم‌چنین کاری را که در آن شب به انجام رسانده است، مطلع می‌شود.\nمثلا، گرداننده به بازجو اعلام می‌کند که این بازیکن حرفه ای است و در این شب به این بازیکن شلیک کرده است.\nالبته بازجو نمیتواند کسی را که مراقب از او حفاظت کرده است را بازجویی کند"
        MafiaRole.POISON_MAKER -> "یکی از اعضای گروه مافیا است که وظیفه اش ساختن سم و خوراندن آن به شهروندان است\nهنگامی که سم ساز میخواهد کارش را انجام دهد، مافیا تیر شب ندارد اما هیچکس از این موضوع اطلاعی ندارد جز خود مافیا.\nسم ساز تنها یکبار میتواند از قابلیتش استفاده کند\nولی هنگامی که این کار را بکند بازیکنی که به او سم خورانده شود پس از گذشت چند روز (هنگام انتخاب نقش مشخص می شود) بدون هیچگونه نجاتی از بازی بیرون می رود و حتما می میرد"
        MafiaRole.SPY -> "جاسوس بازی جز تیم مافیا است و اگر فراماسون بازی او را بیدار کند تیم ماسونی از بازی خارج نمی شوند و جاسوس همانند شهروند به آن ها می پیوندد."
        MafiaRole.THIEF -> "دزد یکی از اعضای گروه مافیا است ولی با مافیا بیدار نمی شود و بقیه مافیا ها را نمی شناسد هر چند گروه مافیا نیز او را نمی شناسند\nدزد در شب بیدار میشود و اسم دو نفر را به گرداننده اعلام می کند اگر نفر اول قابلیت شب داشته باشد قابلیتش بر روی نفر دوم اعمال میشود\nاما این نقش استثناعاتی نیز دارد\n•\\tاگر نفر اول شهروند ساده و یا مافیا ساده باشد هیچ اتفاقی نخواهد افتاد\n•\\tاگر نفر اول پدرخوانده باشد شلیکش بر روی نفر دوم خواهد بود\n•\\tاگر نفر اول حرفه ای باشد شلیکش بر روی نفر دوم خواهد بود\n•\\tاگر نفر اول دکتر و یا دکتر لکتر باشد نفر دوم نجات داده میشود\n•\\tاگر نفر اول نخبه و یا کارآگاه باشد دستگیر میشود\n•\\tاگر نفر اول فروشنده و یا صبا باشد یکی به قابلیت هایش اضافه میشود\n•\\tاگر نفر اول شارلاتان باشد نفر دوم استعلامش معکوس میشود\n•\\tاگر نفر اول روانپزشک باشد نفر دوم ساکت میشود\n•\\tاگر نفر اول جان سخت باشد جان سخت آن شب زره اش را از دست میدهد\n•\\tدزد به جای هکر، سم ساز، شهردار، فرشته نجات و ساول گودمن هیچ تصمیمی نمیتواند بگیرد\n\nدزد همیشه باید دقت کند که چه کسی را انتخاب میکند هرچند هیچگاه از انتخاب خود اطمینان ندارد\nاگر ساول گودمن در بازی باشد با خروج دزد نمیتوانند خریداری را انجام دهند چون از خروج او اطلاعی در دست ندارند مگر اینکه جان سخت استعلام خروج را گرفته باشد"
        MafiaRole.CHURCHILL -> "چرچیل در شب معارفه یک نفر را به گرداننده معرفی میکند و اگر آن شخص در روز دوم به بعد از بازی خارج شود چرچیل پس از آن نامیرا خواهد شد، البته چرچیل در روز اول بازی یک زره بر تن دارد که از شلیک در امان خواهد بود\nچرچیل دو قابلیت دیگر هم دارد یک قابلیت آن سلاخی بازیکنان است در صورتی که بتواند نقش یک بازیکن را درست حدس بزند آن بازیکن از بازی خارج خواهد شد و قابلیت دیگر آن شلیک است ولی نمیتواند به کسی که در روز معارفه بازی انتخاب کرده است شلیک کند و از این دو قابلیت نمیتواند به طور همزمان استفاده کند\nشرط پیروزی چرچیل در ماندن بین 3 نفر آخر است به طوری که یکی از آنها مافیا و یکی دیگر شهروند باشد و اگر بین دو شهروند باشد باید یکی از آنها را متقاعد کند که با او دست بدهند و اگر بین دو مافیا باشد بازند بازی خواهد بود\n"
        MafiaRole.NOSTRADAMUS -> "در شب معارفه گرداننده او را بیدار می کند.\nنوستراداموس به انتخاب خود چند بازیکن را به گرداننده نشان می دهد.\nسپس گرداننده به او تعداد مافیا های موجود در میان انتخاب های او را اعلام می کند و پیش بینی او مبنی بر پیروزی یکی از دو ساید را از او می پرسد.\nنوستراداموس پیش بینی می کند شهروندان برنده خواهند شد یا مافیا ها.\nاز این پس او برای برنده شدن سایدی که انتخاب کرده است تلاش می کند.بدون آنکه دیگر افراد بدانند وی به چه سایدی پیوسته است\nشرط پیروزی نوستراداموس، پیروزی ساید انتخابی او در شب مغارفه می باشد البته به شرطی که خودش داخل بازی باشد.\nنوستراداموس در مقابل شلیک از طرف هر سایدی مصون است و فقط با حس ششم پدرخوانده یا اجماع روز از بازی خارج خواهد شد\nلازم به ذکر است اگر پدرخوانده جز انتخاب های او باشد در اعلام تعداد مافیا شهروندی محاسبه میگردد.\nنوستراداموس در صورتی که دستبند به دست باشد یا نقشش توسط فروشنده گرفته شده باشد یا ماتادور نقش او را گرو گرفته باشد و یا با کارت حرکت آخر ذهن زیبا به بازی برگردد با شلیک شب از بازی خارج خواهد شد در صورتی که دکتر او را نجات نداده باشد"
        MafiaRole.SHERLOCK_HOLMES -> "شرلوک هلمز یک مستقل است که میتواند شهروند و یا مافیا باشد اگر او با حس ششم خود بتواند یکی از نقش های حاضر در بازی را حدس بزند و اگر حدسش درست باشد نقش خود را با بازیکن انتخابی عوض میکند و بازیکن انتخابی را سلاخی می کند و در ساید او با نقش جدید قرار می گیرد هرچند میتواند به عنوان یک مستقل عمل کند و هیچ نقشی را حدس نزند در این صورت در صورتی که بین 3 نفر آخر بازی باشد برنده بازی خواهد بود او همچنین در 2 شب اول بازی از هر گونه تیری مصون است"
        MafiaRole.JACK_SPARROW -> "یکی دیگر از جذابترین نقش های مستقل در بازی مافیا هست\nهر شب یک نفر را به عنوان بازیکن طلسم شده انتخاب میکند چنانچه آن بازیکن همان شب یا روز بعد به هر دلیلی از بازی خارج بشود  جک هم از بازی خارج میشود و روز بعد توسط راوی اعلام خواهد شد.\nنقش جک در شب و روز دارای زره می باشد ولی با سلاخی یا حس ششم پدرخوانده ، از بازی خارج میشود.\nنکات:\nنقش جک هر شب باید یک نفر غیر تکراری را انتخاب کند و تا پایان انتخاب تمام بازیکنان ، نمی تواند بازیکن تکراری انتخاب کند.  زمانی که تمام بازیکنان داخل بازی یک بار انتخاب شدند، انتخابها ریست میشود  و دوباره می تواند بازیکن تکراری انتخاب کند.\nدر روز چنانچه با اجماع رای گیری یا هر روش دیگری مانند تیر تفنگدار، ترور و… خارج بشود توسط راوی نقشش افشا می شود و به بازی بر میگردد و تا آخر بازی قابلیت انتخاب طلسم دیگری ندارد و از آن لحظه به بعد فقط با کشته شدن فرد طلسم شده در شب قبل از بازی خارج می شود.\n* کارت حرکت آخر ذهن زیبا با اندکی تغییرات نسبت به قبل می باشد بدین صورت که اگر  بازیکنی کارت ذهن زیبا را انتخاب کند و نقش جک اسپارو را قبل شو شدنش درست حدس بزند بجای جک اسپارو به بازی بر میگردد و جک اسپارو از بازی خارج می شود و چنانچه نقش جک اسپارو در روزهای قبل شو شده باشد بازیکنی که کارت ذهن زییا را انتخاب میکند باید نفر طلسم شده جک اسپارو را درست حدس بزند تا بتواند به جای جک اسپارو به بازی برگردد.\n* لازم به ذکر است اگر نقش جک اسپارو از بازی خارج بشود کارت ذهن زیبا از بین کارت های حرکت آخر غیر فعال و برای روزهای بعد قابلیت انتخاب نخواهد داشت.\nشرط پیروزی جک اسپارو:\n۱. تمام اعضای مافیا از بازی خارج بشوند.\n۲. بازی به سه نفر برسد."
        MafiaRole.ZODIAC -> "زودیاک یک نقش مستقل هست که باید به تنهایی در مقابل دو تیم شهروند و مافیا بجنگد.\nاو تنها در شب های زوج اجازه تیراندازی دارد و در شب شات نمیشود او میتواند در همه شب ها برای بقیه افراد یک نامه ای بنویسد و این نامه در روز توسط گرداننده خوانده خواهد شد او در روز توسط تفنگ، احماع رای گیری و یا بمب از بازی خارج خواهد شد.\nنکته 1 : زودیاک در مقابل حس ششم و یا شلیک قاتل در امان نخواهد بود\nنکته 2 : زودیاک اگر به اشتباه محافظ را مورد هدف قرار دهد خودش از بازی خارج خواهد شد\nنکته 3 : زودیاک در صورتی که بین 3 نفر آخر باشد پیروز بازی خواهد بود"
        MafiaRole.JIGSAW -> "جیگساو در فاز شب نامیرا است هر شب توسط گرداننده بازی بیدار شده و ۳ نفر را انتخاب میکند و به یک نفر آنها به انتخاب خود ارّه میدهد ، در فاز روز پس از پایان صحبت ها آنها به اتاق گاز جیگساو فرا خوانده میشوند و گرداننده اعلام می کند اره دست کیست اره دست هر کس باشد آن 2 نفر فرصت دارند تا به طور آزاد از خود دفاع کنند بعد از دفاعیه کسی که اره دارد باید یکی از آن 2 نفر را با اره بزند و از بازی خارج کند جیگساو خودش را هم میتواند جز انتخاب شده ها قرار دهد اما اره دست خودش نمیتواند بدهد شرط پیروزی جیگساو در باقی ماند بین سه نفر آخر است در صورتی که هیچ مافیایی در بازی نباشد"
        MafiaRole.FELON -> "نقش جانی یکی از نقش های جذابی است  که در گروه مستقل عمل میکند\nبه طوری که در روز های زوج اجازه شلیک دارد و در روزهای فرد از شلیک شب در امان است\nشرط پیروزی او در گرو باقیماندن در بین 3 نفر آخر است"
        MafiaRole.CORONA -> "کرونا جزء نقش های مستقل است که یک نقش بسیار جذاب و هیجانی به شمار میرود\nدر شب معارفه به تعداد مشخصی نفرات را آلوده می کند.\nشب اول دوره مخفی بیماری است و بیماری سرایت نخواهد کرد.\nدر آغاز شب دوم، بیماری می تواند منتقل شود یعنی اگر کسی به هر دلیلی آن شخص یا اشخاص را انتخاب یا عملیاتی روی آنها انجام دهد شب اول بیماری خودش شروع می شود مانند شات مافیا، استعلام کارآگاه، نجات دکتر ، شلیک حرفه ای ، انتخاب به عنوان نفر دوم دزد و .... \nدر پایان شب سوم بازیکنان بیمار جزء کشته های شب از بازی خارج می شوند مگر اینکه توسط فرشته نجات به بازی برگردند\nالبته اگر دکتر یا دکتر لکتر خودشان آلوده باشند نمی توانند کسی را از شر ویروس کرونا خلاص کنند.\nدکتر شهر یا دکتر لکتر می توانند با انتخاب بیمارانی که شب اول ابتلا هستند از انتشار بیماری جلوگیری کنند.\nکرونا خودش ناقل بیماری نیست و باعث انتشار نمی شود.\nاگر کرونا به هر دلیلی (شات شب یا خروج روز شامل رای گیری، تیر جنگی صاحب تفنگ یا ترور و ....)  از بازی خارج شود . انتشار ویروس کرونا متوقف میشود ولی آثار بیماران قبلی تا زمان مرگ شان ادامه خواهد داشت.\nبرای پیروزی کرونا نیاز است که خودش حتما در بازی حضور داشته باشند و تمام بازیکنان حاضر در شهر به ویروس آلوده باشند."
        MafiaRole.KILLER -> "او قاتل است وبسیار خطرناک، هنگامی که قاتل شهر یک نفر را میکشد تحت هیچ شرایطی درمان نخواهد شد و از بازی بیرون خواهد رفت.\nمگر اینکه قاتل آن شب از بین مردگان آن را زنده کند\nقاتل هر شب میتواند تیراندازی کند و هنگامی که به او شلیک میشود همانند یک شهروند عادی با او برخورد خواهد شد\nشرط پیروزی قاتل در خروج پدرخوانده، دکتر، کارآگاه ویژه و همه نقش های مستقل دیگر است"
        MafiaRole.JOKER -> "جوکر در مافیا بازی یکی از شهروندان است که به صورت مستقل عمل میکند\nشرط پیروزی جوکر در این است که در طول روز علیه او اجماع شود.\nبرخلاف بقیه بازیکنان که در طول بازی تلاش می‌کنند، در روز با اجماع رای بقیه بازیکنان بیرون نروند، جوکر برعکس تلاش می‌کند که همه به او مشکوک شوند و اجماع رای‌ها برای او باشد، چون در این شرایط است که او برنده محسوب می‌شود."
        else -> ""
    }

    private fun baseIntro(role: MafiaRole): String = when (role) {
        MafiaRole.CITIZEN -> "شهروند ساده خودش را به راوی معرفی کند"
        MafiaRole.PROFESSIONAL -> "حرفه ای خودش را به راوی معرفی کند"
        MafiaRole.SNIPER -> "اسنایپر خودش را به راوی معرفی کند"
        MafiaRole.INHERITOR -> "وارث خودش را به راوی معرفی کند"
        MafiaRole.SUSPECT -> "مظنون خودش را به راوی معرفی کند"
        MafiaRole.COMMANDER -> "فرمانده خودش را به راوی معرفی کند"
        MafiaRole.DETECTIVE -> "کارآگاه خودش را به راوی معرفی کند"
        MafiaRole.SNOWMAN -> "آدم برفی خودش را به راوی معرفی کند"
        MafiaRole.DOCTOR -> "دکتر خودش را به راوی معرفی کند"
        MafiaRole.PROTECTOR -> "محافظ خودش را به راوی معرفی کند"
        MafiaRole.OCEAN -> "اوشن خودش را به راوی معرفی کند"
        MafiaRole.ATTAR -> "عطار خودش را به راوی معرفی کند"
        MafiaRole.SAINT -> "قدیس خودش را به راوی معرفی کند"
        MafiaRole.SALVATION_ANGEL -> "فرشته نجات خودش را به راوی معرفی کند"
        MafiaRole.CHAPLIN -> "چاپلین خودش را به راوی معرفی کند"
        MafiaRole.SMITH -> "آهنگر خودش را به راوی معرفی کند"
        MafiaRole.HERO -> "رویین تن خودش را به راوی معرفی کند"
        MafiaRole.SPIDER -> "عنکبوت خودش را به راوی معرفی کند"
        MafiaRole.INTERROGATOR -> "بازپرس خودش را به راوی معرفی کند"
        MafiaRole.RESEARCHER -> "محقق خودش را به راوی معرفی کند"
        MafiaRole.COMMANDO -> "تکاور خودش را به راوی معرفی کند"
        MafiaRole.GUNMAN -> "تفنگدار خودش را به راوی معرفی کند"
        MafiaRole.JUPITER -> "ژوپیتر خودش را به راوی معرفی کند"
        MafiaRole.CITIZEN_KANE -> "همشهری کین خودش را به راوی معرفی کند"
        MafiaRole.CONSTANTINE -> "کنستانتین خودش را به راوی معرفی کند"
        MafiaRole.RUSSIAN_ROULETTE -> "رولت روسی خودش را به راوی معرفی کند"
        MafiaRole.SACRIFICE -> "فدایی خودش را به راوی معرفی کند"
        MafiaRole.COWBOY -> "کابوی خودش را به راوی معرفی کند"
        MafiaRole.SUICIDE_BOMBER -> "بمبر خودش را به راوی معرفی کند"
        MafiaRole.DIE_HARD -> "جان سخت خودش را به راوی معرفی کند"
        MafiaRole.MAYOR -> "شهردار خودش را به راوی معرفی کند"
        MafiaRole.SALESMAN -> "فروشنده خودش را به راوی معرفی کند"
        MafiaRole.REPORTER -> "خبرنگار خودش را به راوی معرفی کند"
        MafiaRole.HACKER -> "هکر خودش را به راوی معرفی کند"
        MafiaRole.CLEVER -> "نخبه خودش را به راوی معرفی کند"
        MafiaRole.MASON -> "فراماسون خودش را به راوی معرفی کند"
        MafiaRole.TYLER -> "تایلر خودش را به راوی معرفی کند"
        MafiaRole.SLEEP_WALKER -> "خوابگرد خودش را به راوی معرفی کند"
        MafiaRole.PRIEST -> "کشیش خودش را به راوی معرفی کند"
        MafiaRole.PSYCHOLOGIST -> "روانپزشک خودش را به راوی معرفی کند"
        MafiaRole.GUARDIAN -> "نگهبان خودش را به راوی معرفی کند"
        MafiaRole.GUARD -> "مراقب خودش را به راوی معرفی کند"
        MafiaRole.SPECIAL_DETECTIVE -> "کارآگاه ویژه خودش را به راوی معرفی کند"
        MafiaRole.SABA -> "صبا خودش را به راوی معرفی کند"
        MafiaRole.MAFIA -> "مافیا ساده خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.GODFATHER -> "پدرخوانده خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.PABLO -> "پابلو بیدار شود و لایک پدرخوانده را ببیند"
        MafiaRole.CAST_AWAY -> "دور افتاده خودش را به راوی معرفی کند"
        MafiaRole.LOVER -> "معشوقه خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.STRONG_MAN -> "مرد قوی خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.WITCHER -> "جادوگر را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.VOODOO -> "افسونگر خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.NATO -> "ناتو خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.MATADOR -> "ماتادور خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.ILLUSIONIST -> "شعبده باز خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.NATASHA -> "ناتاشا خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.BOMBER -> "بمب گذار خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.GOOD_MAN -> "ساول گودمن خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.FRAUD -> "شیاد خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.WRECKER -> "خرابکار خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.YAKUZA -> "یاکوزا خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.TERRORIST -> "تروریست خودش را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.CHARLATAN -> "شارلاتان خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.DOCTOR_LECTOR -> "دکتر لکتر خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.BODYGUARD -> "بادیگارد خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.INVESTIGATOR -> "بازجو خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.POISON_MAKER -> "سم ساز خودش را به راوی و بقیه افراد مافیا معرفی کند"
        MafiaRole.SPY -> "جاسوس را به راوی و بقیه اعضای مافیا معرفی کند"
        MafiaRole.THIEF -> "دزد خودش را به راوی معرفی کند"
        MafiaRole.CHURCHILL -> "چرچیل خودش را به راوی معرفی کند"
        MafiaRole.NOSTRADAMUS -> "نوستراداموس خودش را به راوی معرفی کند"
        MafiaRole.SHERLOCK_HOLMES -> "شرلوک هلمز خودش را به راوی معرفی کند"
        MafiaRole.JACK_SPARROW -> "جک اسپارو خودش را به راوی معرفی کند"
        MafiaRole.ZODIAC -> "زودیاک خودش را به راوی معرفی کند"
        MafiaRole.JIGSAW -> "جیگساو خودش را به راوی معرفی کند"
        MafiaRole.FELON -> "جانی خودش را به راوی معرفی کند"
        MafiaRole.CORONA -> "کرونا خودش را به راوی معرفی کند"
        MafiaRole.KILLER -> "قاتل خودش را به راوی معرفی کند"
        MafiaRole.JOKER -> "جوکر خودش را به راوی معرفی کند"
        else -> ""
    }
}

object MafiaRoleOptions {
    fun of(role: MafiaRole): List<MafiaOptionDef> = if (role.isGeneric) MafiaGenericRoles.options(role) else baseOf(role)

    private fun baseOf(role: MafiaRole): List<MafiaOptionDef> = when (role) {
        MafiaRole.CITIZEN -> listOf(
            MafiaOptionDef("تعداد شهروند ساده", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55"), "1")
        )
        MafiaRole.PROFESSIONAL -> listOf(
            MafiaOptionDef("تعداد تیر", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2"),
            MafiaOptionDef("تعداد زره", listOf("0", "1", "2", "3", "4"), "1")
        )
        MafiaRole.SNIPER -> listOf(
            MafiaOptionDef("تعداد تیر", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.DETECTIVE -> listOf(
            MafiaOptionDef("تعداد استعلام", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.DOCTOR -> listOf(
            MafiaOptionDef("تعداد نجات کل", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞"),
            MafiaOptionDef("تعداد نجات خود", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1"),
            MafiaOptionDef("نجات شب اول", listOf("1", "2", "3"), "1")
        )
        MafiaRole.OCEAN -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.ATTAR -> listOf(
            MafiaOptionDef("تعداد زهر", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.SALVATION_ANGEL -> listOf(
            MafiaOptionDef("تعداد نجات", listOf("1", "2", "3"), "1")
        )
        MafiaRole.SMITH -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.SPIDER -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4"), "2")
        )
        MafiaRole.COMMANDO -> listOf(
            MafiaOptionDef("تعداد شلیک", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.GUNMAN -> listOf(
            MafiaOptionDef("تعداد تفنگ جنگی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1"),
            MafiaOptionDef("تعداد تفنگ مشقی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2"),
            MafiaOptionDef("فایر بک", listOf("بله", "خیر"), "بله", true)
        )
        MafiaRole.JUPITER -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.CONSTANTINE -> listOf(
            MafiaOptionDef("تعداد نجات", listOf("1", "2", "3"), "1")
        )
        MafiaRole.RUSSIAN_ROULETTE -> listOf(
            MafiaOptionDef("تعداد رولت روسی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.DIE_HARD -> listOf(
            MafiaOptionDef("تعداد استعلام", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.MAYOR -> listOf(
            MafiaOptionDef("تعداد مداخله در رای گیری", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.SALESMAN -> listOf(
            MafiaOptionDef("تعداد قابلیت", listOf("1", "2", "3"), "1")
        )
        MafiaRole.CLEVER -> listOf(
            MafiaOptionDef("تعداد استعلام", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.MASON -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞"),
            MafiaOptionDef("اجبار به انتخاب در شب معارفه", listOf("بله", "خیر"), "بله", true)
        )
        MafiaRole.PRIEST -> listOf(
            MafiaOptionDef("تعداد نفرات در هر شب", listOf("1", "2", "3", "4"), "1"),
            MafiaOptionDef("تعداد دفعات انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.PSYCHOLOGIST -> listOf(
            MafiaOptionDef("تعداد سکوت", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.GUARDIAN -> listOf(
            MafiaOptionDef("نگهبانی شب اول", listOf("1", "2", "3"), "2")
        )
        MafiaRole.GUARD -> listOf(
            MafiaOptionDef("تعداد مراقبت", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2"),
            MafiaOptionDef("مراقبت شب اول", listOf("1", "2", "3"), "2")
        )
        MafiaRole.SPECIAL_DETECTIVE -> listOf(
            MafiaOptionDef("تعداد استعلام", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2"),
            MafiaOptionDef("تعداد استعلام منفی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "3")
        )
        MafiaRole.SABA -> listOf(
            MafiaOptionDef("تعداد نقش دزدی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.MAFIA -> listOf(
            MafiaOptionDef("تعداد مافیا ساده", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55"), "1")
        )
        MafiaRole.GODFATHER -> listOf(
            MafiaOptionDef("تعداد استعلام منفی", listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞"),
            MafiaOptionDef("تعداد زره", listOf("0", "1", "2", "3"), "1"),
            MafiaOptionDef("حس ششم", listOf("بله", "خیر"), "بله", true),
            MafiaOptionDef("سوداگری", listOf("بله", "خیر"), "خیر", true),
            MafiaOptionDef("پادزهر عطار", listOf("بله", "خیر"), "بله", true)
        )
        MafiaRole.STRONG_MAN -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.NATO -> listOf(
            MafiaOptionDef("تعداد ناتویی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.NATASHA -> listOf(
            MafiaOptionDef("تعداد سکوت", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.BOMBER -> listOf(
            MafiaOptionDef("تعداد بمب گذاری", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.FRAUD -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.WRECKER -> listOf(
            MafiaOptionDef("تعداد خرابکاری", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.YAKUZA -> listOf(
            MafiaOptionDef("تعداد یاکوزایی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.CHARLATAN -> listOf(
            MafiaOptionDef("تعداد انتخاب مافیا", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1"),
            MafiaOptionDef("تعداد انتخاب شهروند", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.DOCTOR_LECTOR -> listOf(
            MafiaOptionDef("تعداد نجات کل", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞"),
            MafiaOptionDef("تعداد نجات خود", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.INVESTIGATOR -> listOf(
            MafiaOptionDef("تعداد بازجویی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1")
        )
        MafiaRole.POISON_MAKER -> listOf(
            MafiaOptionDef("زمان اثر سم (روز)", listOf("1", "2", "3", "4"), "2")
        )
        MafiaRole.THIEF -> listOf(
            MafiaOptionDef("تعداد نقش دزدی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "2")
        )
        MafiaRole.CHURCHILL -> listOf(
            MafiaOptionDef("تعداد شلیک", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "1"),
            MafiaOptionDef("تعداد سلاخی", listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "∞"), "∞")
        )
        MafiaRole.NOSTRADAMUS -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3", "4", "5", "6"), "3")
        )
        MafiaRole.JIGSAW -> listOf(
            MafiaOptionDef("تعداد انتخاب", listOf("1", "2", "3"), "1")
        )
        MafiaRole.CORONA -> listOf(
            MafiaOptionDef("انتخاب شب معارفه", listOf("1", "2", "3"), "1"),
            MafiaOptionDef("تعداد زره", listOf("0", "1", "2", "3"), "1")
        )
        MafiaRole.KILLER -> listOf(
            MafiaOptionDef("تعداد زره", listOf("0", "1", "2", "3"), "1")
        )
        MafiaRole.JOKER -> listOf(
            MafiaOptionDef("تعداد زره", listOf("0", "1", "2", "3"), "1")
        )
        else -> emptyList()
    }
}
