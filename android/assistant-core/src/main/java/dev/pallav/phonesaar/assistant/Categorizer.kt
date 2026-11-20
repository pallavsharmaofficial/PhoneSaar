package dev.pallav.phonesaar.assistant

import java.util.Locale

enum class Category { CHAT, MONEY, TRAVEL, WORK, PHOTOS, MEDIA, SHOPPING, GOV_HEALTH, LEARNING, TOOLS, GAMES, OTHER }

/** Sorts an app into a category: explicit package map, then the system category, then label keywords. */
object Categorizer {
    // ApplicationInfo.CATEGORY_* values, kept as ints so this stays free of Android imports.
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7
    private const val CAT_ACCESSIBILITY = 8

    private val packageLists = mapOf(
        Category.CHAT to """com.whatsapp com.whatsapp.w4b org.telegram.messenger org.telegram.plus com.facebook.orca com.facebook.mlite
            com.google.android.apps.messaging com.google.android.dialer com.android.dialer com.android.mms com.android.contacts
            com.google.android.contacts com.truecaller com.snapchat.android com.instagram.android com.instagram.lite com.facebook.katana
            com.facebook.lite com.twitter.android com.skype.raider com.google.android.apps.tachyon us.zoom.videomeetings
            com.linkedin.android com.viber.voip org.thoughtcrime.securesms com.imo.android.imoim in.mohalla.sharechat com.google.android.gm
            com.samsung.android.dialer com.samsung.android.messaging com.samsung.android.contacts com.miui.contacts com.android.incallui
            com.microsoft.teams com.google.android.talk com.discord com.pinterest com.reddit.frontpage com.koo.app in.mohalla.video
            com.android.phone com.coloros.contacts com.vivo.contacts com.oneplus.contacts com.microsoft.office.outlook""",
        Category.MONEY to """com.phonepe.app com.google.android.apps.nbu.paisa.user net.one97.paytm in.org.npci.upiapp com.dreamplug.androidapp
            com.sbi.SBIFreedomPlus com.sbi.lotusintouch com.snapwork.hdfc com.csam.icici.bank.imobile com.axis.mobile com.msf.kbank.mobile
            com.canarabank.mobility com.bankofbaroda.mconnect com.freecharge.android com.mobikwik_new com.myairtelapp com.jio.myjio
            com.zerodha.kite3 com.nextbillion.groww in.upstox.app com.bharatpe.app com.google.android.apps.walletnfcrel com.paytmmoney
            in.amazon.pay com.amazon.mShop.android.pay com.infinitybank.imobile com.icicibank.pockets com.fisglobal.pnb com.pnb.onestop
            com.idfcfirstbank.optimus com.yesbank com.kotak.mobilebanking com.hdfc.bank.payzapp com.bajajfinserv.app com.indusind.indie
            com.lic.licmobile com.policybazaar.android com.angelbroking.angelone com.mobikwik.app com.slice.pay com.jar.app
            com.ebixcash.stp com.navi.android com.phonepe.business com.cred.club com.finbox.moneyview in.juspay.hypersdk""",
        Category.TRAVEL to """com.google.android.apps.maps com.google.android.apps.mapslite com.olacabs.customer com.ubercab in.redbus.android
            com.makemytrip com.goibibo cris.org.in.prs.ima com.ixigo com.ixigo.train.ixitrain com.whereismytrain.android com.mmi.maps
            com.google.earth in.goindigo.android com.oyo.consumer com.cleartrip.android com.easemytrip.android com.abhibus.android
            com.rapido.passenger com.airindia.mobile com.spicejet com.irctc.air in.org.npci.fastag com.nhai.fastag com.here.app.maps
            com.waze com.meru.customer com.indrive.android com.blablacar.android com.yatra.base com.airbnb.android com.nic.parivahan
            com.google.android.apps.travel.onthego com.cleartrip.cabs com.mapmyindia.app.navigation com.ride.ola""",
        Category.WORK to """com.rapido.rider com.ubercab.driver com.olacabs.oladriver in.swiggy.deliveryapp com.zomato.delivery
            com.zomato.deliveryx com.theporter.android.driverapp com.grofers.deliveryapp com.zepto.rider com.shadowfax.delivery
            com.dunzo.delivery com.ola.driver com.ubercab.eats com.meesho.supply.partner com.flipkart.deliveryapp com.amazon.flex
            in.amazon.mShop.flex com.delhivery.rider com.ekart.delivery com.dtdc.partner com.shiprocket.delivery com.rapido.captain
            com.ubercab.freight com.olacabs.driver com.blinkit.partner com.swiggy.partner com.urbanclap.partner com.uc.partner""",
        Category.PHOTOS to """com.google.android.apps.photos com.android.camera com.android.camera2 com.google.android.GoogleCamera
            com.sec.android.app.camera com.miui.gallery com.android.gallery3d com.sec.android.gallery3d com.coloros.gallery3d
            com.oneplus.gallery com.vivo.gallery com.niksoftware.snapseed com.picsart.studio com.camerasideas.instashot com.lightricks.facetune
            com.motorola.camera3 com.motorola.cn.gallery com.huawei.camera com.mi.android.globalcamera com.vivo.camera com.oppo.camera
            com.coloros.camera com.oneplus.camera com.google.android.apps.photosgo com.adobe.lrmobile com.canva.editor com.xiaomi.camera
            com.cyberlink.youcammakeup com.cyberlink.photodirector com.vsco.cam com.snap.camera com.bigwinepot.nwdn.international""",
        Category.MEDIA to """com.google.android.youtube com.google.android.apps.youtube.music com.spotify.music in.startv.hotstar
            com.jio.media.ondemand com.netflix.mediaclient com.amazon.avod.thirdpartyclient com.sonyliv com.graymatrix.did com.gaana
            com.jio.media.jiobeats com.bsbportal.music com.mxtech.videoplayer.ad com.mxtech.videoplayer.pro org.videolan.vlc com.google.android.videos
            com.amazon.mp3 com.google.android.music com.miui.player com.miui.videoplayer com.samsung.android.app.musicplayer
            com.hungama.myplay.activity com.next.innovation.takatak com.eros.now in.juspay.voot com.voot.app com.google.android.apps.youtube.kids
            com.youtube.shorts com.jiocinema com.zee5.zeeplex com.mubi com.audible.application com.shazam.android com.sonyliv.kids
            com.apple.android.music com.amazon.venezia.music com.google.android.apps.podcasts com.sec.android.app.music com.oppo.music
            com.coloros.video com.vivo.livewallpaper com.vlc.android com.hotstar.disney tv.twitch.android.app com.dailymotion.android""",
        Category.SHOPPING to """in.amazon.mShop.android.shopping com.amazon.mShop.android.shopping com.flipkart.android com.myntra.android
            com.meesho.supply com.ril.ajio com.fsn.nykaa com.bigbasket.mobileapp com.grofers.customerapp in.swiggy.android
            com.application.zomato com.zeptoconsumerapp com.dunzo.user com.jpl.jiomart com.tatadigital.tcp com.snapdeal.main
            com.olx.southasia com.lenskart.app com.firstcry.parenting com.flipkart.shopsy com.urbanclap.urbanclap com.dmart.dmartready
            com.magicpin.android com.eatsure.app com.dominos.india com.mcdonalds.gma.india com.pizzahut.india com.limeroad.app
            com.shopclues com.tatacliq.app com.nnnow.android com.purplle.app com.zepto.app com.milkbasket.app com.licious
            com.freshtohome.app com.swiggy.instamart com.blinkit.app com.eazydiner.android com.bookmyshow com.zomato.dining""",
        Category.GOV_HEALTH to """com.digilocker.android in.gov.digilocker.mobile in.gov.umang.negd.g2c in.gov.uidai.mAadhaarPlus nic.goi.aarogyasetu
            in.gov.cowin in.gov.nha.pmjay in.mygov.mobile in.gov.incometax in.gov.mea.mpassport com.phonegap.pharmeasy com.apollo.patientapp
            com.practo.droid com.aranoah.healthkart.plus com.google.android.apps.fitness com.fitbit.FitbitMobile com.netmeds.app
            in.gov.eshram in.gov.epfindia in.gov.abdm.abha com.abdm.abha in.gov.pmkisan com.nic.mparivahan com.nic.vahan
            com.cowin.app com.eka.care com.mfine.android com.tata1mg.consumer com.curefit.app com.healthifyme.basic com.hdfc.ergo
            in.gov.gst.app in.gov.ration.mera com.nic.ration in.gov.nvsp.voter com.eci.citizen in.nic.eci.voterhelpline in.gov.abdm.eka
            gov.in.pm.kisan com.sbi.sbicare com.starhealth.app com.careinsurance.app com.medibuddy.app com.docsapp.patients""",
        Category.LEARNING to """com.duolingo com.byjus.thelearningapp com.unacademyapp com.google.android.apps.classroom org.khanacademy.android
            com.vedantu.app xyz.penpencil.physicswala com.google.android.apps.books com.amazon.kindle com.pratilipi.mobile.android
            com.udemy.android org.coursera.android com.nptel.app com.cuemath.app com.toppr.app com.doubtnut.app com.simplilearn.app
            com.google.android.apps.bard com.google.android.apps.dictionary com.hellochinese com.english.speaking.app com.leverageedu.app
            com.testbook.tbapp com.adda247.app com.embibe.app com.whitehatjr.app com.cambly.app com.babbel.mobile.android.en
            com.memrise.android.memrisecompanion com.quizlet.quizletandroid com.kutumb.app com.vidyakosh.app com.diksha.app in.gov.diksha""",
        Category.TOOLS to """com.android.settings com.google.android.calculator com.android.calculator2 com.sec.android.app.popupcalculator
            com.miui.calculator com.google.android.deskclock com.android.deskclock com.google.android.calendar com.android.calendar
            com.google.android.apps.nbu.files com.google.android.documentsui com.android.documentsui com.mi.android.globalFileexplorer
            com.sec.android.app.myfiles com.google.android.keep com.google.android.apps.docs com.google.android.apps.docs.editors.docs
            com.google.android.apps.docs.editors.sheets com.google.android.apps.docs.editors.slides com.google.android.apps.translate
            com.android.chrome org.mozilla.firefox com.brave.browser com.android.browser com.sec.android.app.sbrowser com.opera.mini.native
            com.android.vending com.google.android.googlequicksearchbox com.android.soundrecorder com.google.android.apps.recorder
            com.sec.android.app.voicenote com.miui.notes com.google.android.apps.wellbeing com.google.android.apps.chromecast.app
            com.mi.globalbrowser com.miui.securitycenter com.miui.cleaner com.samsung.android.lool com.google.android.apps.authenticator2
            cn.xender com.xiaomi.midrop com.mi.android.globalminusscreen com.google.android.apps.safetyhub dev.pallav.phonesaar
            com.google.android.apps.nbu.paisa.merchant com.google.android.feedback com.android.stk com.miui.weather2 com.google.android.apps.weather
            com.samsung.android.app.notes com.google.android.inputmethod.latin com.touchtype.swiftkey com.microsoft.office.word
            com.microsoft.office.excel com.microsoft.office.powerpoint cn.wps.moffice_eng com.adobe.reader com.dropbox.android
            com.microsoft.skydrive com.google.android.apps.dynamite com.google.android.projection.gearhead com.truecaller.dialer.settings""",
        Category.GAMES to """com.dts.freefireth com.dts.freefiremax com.supercell.clashofclans com.supercell.clashroyale com.supercell.brawlstars
            com.king.candycrushsaga com.kiloo.subwaysurf com.mojang.minecraftpe com.ludo.king com.imangi.templerun2 com.roblox.client
            com.activision.callofduty.shooter com.tencent.ig com.pubg.imobile com.mpl.mobile com.app.dream11 com.zupee.app
            com.miniclip.eightballpool com.zynga.words3 com.gameloft.android.ANMP.GloftA9HM com.nazara.chotabheem com.playrix.homescapes
            com.playrix.gardenscapes com.kitkagames.fallbuddies com.innersloth.spacemafia com.garena.game.codm com.fungames.sniper3d
            com.ea.gp.fifamobile com.nianticlabs.pokemongo com.hcg.ludoclub com.moonactive.coinmaster com.tencent.tmgp.pubgmhd
            com.outfit7.mytalkingtomfree com.mobilelegends.mi com.moonton.mobilelegends com.lilithgame.roc.gp com.rovio.angrybirds
            com.google.android.play.games com.gamezop.app com.pocketgems.android.stack com.ludoking.ludo com.snakeio.game""",
    )

    // Whole-prefix rules for families such as bank apps.
    private val prefixes = listOf(
        "com.sbi." to Category.MONEY, "com.hdfc" to Category.MONEY, "com.icici" to Category.MONEY, "com.axis" to Category.MONEY,
        "in.gov." to Category.GOV_HEALTH, "gov.in." to Category.GOV_HEALTH, "com.google.android.apps.photos" to Category.PHOTOS,
        "com.supercell." to Category.GAMES, "com.miniclip." to Category.GAMES, "com.king." to Category.GAMES,
    )

    private val byPackage: Map<String, Category> = buildMap {
        for ((cat, list) in packageLists) for (p in list.split(Regex("\\s+"))) if (p.isNotBlank()) putIfAbsent(p, cat)
    }

    // Ordered: the first matching rule wins, so specific groups come before broad ones.
    private val keywordRules: List<Pair<Category, List<String>>> = listOf(
        Category.WORK to listOf("captain", "driver", "delivery partner", "partner app", "rider app", "कैप्टन", "ड्राइवर", "डिलीवरी पार्टनर"),
        Category.GOV_HEALTH to listOf(
            "aadhaar", "aadhar", "digilocker", "umang", "arogya", "aarogya", "cowin", "health", "doctor", "medic", "pharma",
            "hospital", "passport", "voter", "income tax", "epf", "ration", "ayushman", "govt", "gov.", "सरकार", "आधार", "आरोग्य", "स्वास्थ्य", "दवा", "अस्पताल",
        ),
        Category.MONEY to listOf("upi", "pay", "bank", "wallet", "loan", "bhim", "insurance", "invest", "mutual fund", "stock", "credit", "बैंक", "पे", "वॉलेट", "पैसे"),
        Category.TRAVEL to listOf("maps", "cab", "taxi", "train", "flight", "ticket", "travel", "metro", "navigation", "hotel", "नक्शा", "मैप", "रेल", "यात्रा"),
        Category.CHAT to listOf("chat", "call", "message", "sms", "messenger", "dialer", "contacts", "mail", "meet", "चैट", "कॉल", "संदेश", "संपर्क", "मैसेज"),
        Category.PHOTOS to listOf("photo", "camera", "gallery", "selfie", "picture", "फोटो", "कैमरा", "गैलरी"),
        Category.MEDIA to listOf("video", "music", "song", "player", "radio", "movie", "cinema", "podcast", "गाना", "संगीत", "वीडियो", "रेडियो", "फिल्म"),
        Category.SHOPPING to listOf("shop", "store", "mart", "cart", "buy", "grocery", "food", "bazaar", "market", "खरीद", "दुकान", "बाज़ार", "बाजार"),
        Category.LEARNING to listOf("learn", "study", "class", "school", "course", "quiz", "exam", "english", "dictionary", "book", "पढ़ाई", "सीख", "किताब", "स्कूल"),
        Category.GAMES to listOf("game", "ludo", "puzzle", "racing", "गेम", "खेल"),
        Category.TOOLS to listOf("setting", "calculator", "clock", "file", "manager", "cleaner", "browser", "notes", "flashlight", "torch", "recorder", "calendar", "keyboard", "सेटिंग", "कैलकुलेटर", "घड़ी", "टॉर्च"),
    )

    /** [androidCategory] is ApplicationInfo.category (or -1 when undefined). */
    fun categorize(packageName: String, androidCategory: Int, label: String): Category {
        byPackage[packageName]?.let { return it }
        for ((p, c) in prefixes) if (packageName.startsWith(p)) return c
        fromAndroid(androidCategory)?.let { return it }
        val l = label.lowercase(Locale.ROOT)
        for ((cat, words) in keywordRules) if (words.any { l.contains(it) }) return cat
        return Category.OTHER
    }

    private fun fromAndroid(c: Int): Category? = when (c) {
        CAT_GAME -> Category.GAMES
        CAT_AUDIO, CAT_VIDEO -> Category.MEDIA
        CAT_IMAGE -> Category.PHOTOS
        CAT_SOCIAL -> Category.CHAT
        CAT_MAPS -> Category.TRAVEL
        CAT_PRODUCTIVITY, CAT_ACCESSIBILITY -> Category.TOOLS
        else -> null
    }

    internal val mappedPackageCount: Int get() = byPackage.size
}
