package dev.pallav.phonesaar.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private val labels = listOf("WhatsApp", "YouTube", "PhonePe", "Google Pay", "Rapido Captain", "Paytm", "Instagram", "Maps", "Calculator", "Sound Cloud")

    private fun p(t: String) = CommandParser.parse(t, labels)
    private fun open(t: String) = (p(t) as Command.OpenApp).label

    // Open app: English
    @Test fun openEnglish() = assertEquals("YouTube", open("open youtube"))
    @Test fun openEnglishCase() = assertEquals("WhatsApp", open("OPEN WhatsApp!"))
    @Test fun launchWord() = assertEquals("PhonePe", open("launch phonepe"))
    @Test fun openWithPlease() = assertEquals("Instagram", open("please open instagram"))
    @Test fun openMultiWordLabel() = assertEquals("Rapido Captain", open("open rapido captain"))
    @Test fun openTypo() = assertEquals("WhatsApp", open("open whatsap"))
    @Test fun bareLabel() = assertEquals("YouTube", open("youtube"))
    @Test fun openUnknownAppHasNoLabel() = assertEquals(null, open("open zzzqqq"))

    // Open app: Hinglish
    @Test fun kholo() = assertEquals("WhatsApp", open("WhatsApp kholo"))
    @Test fun kholna() = assertEquals("YouTube", open("youtube kholna"))
    @Test fun kholDo() = assertEquals("YouTube", open("youtube khol do"))
    @Test fun openKaro() = assertEquals("YouTube", open("youtube open karo"))
    @Test fun kholiye() = assertEquals("PhonePe", open("phonepe kholiye"))
    @Test fun phonePeSpaced() = assertEquals("PhonePe", open("phone pe kholo"))
    @Test fun gpayAlias() = assertEquals("Google Pay", open("gpay kholo"))
    @Test fun googlePayWords() = assertEquals("Google Pay", open("google pay khol do"))
    @Test fun instaAlias() = assertEquals("Instagram", open("insta kholo"))
    @Test fun rapidoPrefix() = assertEquals("Rapido Captain", open("rapido kholo"))

    // Open app: Devanagari
    @Test fun whatsappDeva() = assertEquals("WhatsApp", open("व्हाट्सएप खोलो"))
    @Test fun youtubeDeva() = assertEquals("YouTube", open("यूट्यूब खोलो"))
    @Test fun phonepeDeva() = assertEquals("PhonePe", open("फ़ोनपे खोलो"))
    @Test fun phonepeDevaNoNukta() = assertEquals("PhonePe", open("फोनपे खोलो"))
    @Test fun gpayDeva() = assertEquals("Google Pay", open("गूगल पे खोलो"))
    @Test fun rapidoDeva() = assertEquals("Rapido Captain", open("रैपिडो खोलो"))
    @Test fun paytmDeva() = assertEquals("Paytm", open("पेटीएम खोल दो"))
    @Test fun mapsDeva() = assertEquals("Maps", open("मैप्स खोलिए"))
    @Test fun calculatorDeva() = assertEquals("Calculator", open("कैलकुलेटर खोलो"))
    @Test fun bareDeva() = assertEquals("WhatsApp", open("व्हाट्सएप"))
    @Test fun devaPhoneticFallback() = assertEquals("Instagram", open("इंस्टाग्राम चलाओ"))

    // Call
    @Test fun callNumber() = assertEquals(Command.Call("9876543210", null), p("call 9876543210"))
    @Test fun callSpacedNumber() = assertEquals(Command.Call("9876543210", null), p("call 98765 43210"))
    @Test fun callPlusNumber() = assertEquals(Command.Call("+919876543210", null), p("call +91 98765 43210"))
    @Test fun callDevaDigits() = assertEquals(Command.Call("9876543210", null), p("९८७६५४३२१० पर कॉल करो"))
    @Test fun callShortNumber() = assertEquals(Command.Call("112", null), p("dial 112"))
    @Test fun callName() = assertEquals(Command.Call(null, "mom"), p("call mom"))
    @Test fun callNameHinglish() = assertEquals(Command.Call(null, "mummy"), p("mummy ko phone karo"))
    @Test fun callNameHinglishLagao() = assertEquals(Command.Call(null, "ramesh"), p("ramesh ko phone lagao"))
    @Test fun callNameDeva() = assertEquals(Command.Call(null, "रमेश"), p("रमेश को फोन लगाओ"))
    @Test fun callNameDevaKaal() = assertEquals(Command.Call(null, "पापा"), p("पापा को कॉल करो"))
    @Test fun callNobody() = assertEquals(Command.Call(null, null), p("call"))
    @Test fun phoneFastIsNotCall() = assertEquals(Command.Boost, p("phone fast karo"))

    // Torch
    @Test fun torchOn() = assertEquals(Command.Torch(true), p("torch on"))
    @Test fun torchOff() = assertEquals(Command.Torch(false), p("torch off"))
    @Test fun torchBand() = assertEquals(Command.Torch(false), p("torch band karo"))
    @Test fun torchChalu() = assertEquals(Command.Torch(true), p("torch chalu karo"))
    @Test fun flashlight() = assertEquals(Command.Torch(true), p("turn on the flashlight"))
    @Test fun torchDevaOn() = assertEquals(Command.Torch(true), p("टॉर्च चालू करो"))
    @Test fun torchDevaOff() = assertEquals(Command.Torch(false), p("टॉर्च बंद करो"))
    @Test fun torchBujha() = assertEquals(Command.Torch(false), p("torch bujha do"))

    // Alarm
    @Test fun alarmHinglishSubah() = assertEquals(Command.Alarm(6, 0), p("subah 6 baje alarm lagao"))
    @Test fun alarmEnglishClock() = assertEquals(Command.Alarm(6, 30), p("alarm at 6:30 am"))
    @Test fun alarmDotClock() = assertEquals(Command.Alarm(6, 30), p("alarm at 6.30 am"))
    @Test fun alarmPm() = assertEquals(Command.Alarm(18, 0), p("set alarm for 6 pm"))
    @Test fun alarmAttachedPm() = assertEquals(Command.Alarm(18, 15), p("alarm 6:15pm"))
    @Test fun alarmShaamDeva() = assertEquals(Command.Alarm(19, 0), p("शाम 7 बजे अलार्म लगाओ"))
    @Test fun alarmBareShaamDeva() = assertEquals(Command.Alarm(19, 0), p("शाम 7 बजे"))
    @Test fun alarmRaat() = assertEquals(Command.Alarm(22, 0), p("raat 10 baje alarm"))
    @Test fun alarmRaatEarly() = assertEquals(Command.Alarm(2, 0), p("raat 2 baje alarm lagao"))
    @Test fun alarmDopahar() = assertEquals(Command.Alarm(14, 0), p("dopahar 2 baje alarm"))
    @Test fun alarmWordNumber() = assertEquals(Command.Alarm(6, 0), p("सुबह छह बजे अलार्म लगाओ"))
    @Test fun alarmSaadhe() = assertEquals(Command.Alarm(6, 30), p("subah saadhe 6 baje alarm"))
    @Test fun alarmPaune() = assertEquals(Command.Alarm(6, 45), p("paune 7 baje alarm lagao"))
    @Test fun alarm24h() = assertEquals(Command.Alarm(17, 0), p("alarm at 17:00"))
    @Test fun alarmMidnight() = assertEquals(Command.Alarm(0, 0), p("alarm 12 am"))
    @Test fun alarmNoTime() = assertEquals(Command.Alarm(null, null), p("alarm lagao"))
    @Test fun alarmDevaDigits() = assertEquals(Command.Alarm(7, 30), p("सुबह ७:३० बजे अलार्म"))

    // Timer
    @Test fun timerHinglish() = assertEquals(Command.Timer(300), p("5 minute ka timer"))
    @Test fun timerEnglish() = assertEquals(Command.Timer(600), p("timer 10 min"))
    @Test fun timerSeconds() = assertEquals(Command.Timer(30), p("set a timer for 30 seconds"))
    @Test fun timerHours() = assertEquals(Command.Timer(7200), p("2 ghante ka timer"))
    @Test fun timerDeva() = assertEquals(Command.Timer(300), p("5 मिनट का टाइमर"))
    @Test fun timerDevaHours() = assertEquals(Command.Timer(3600), p("१ घंटे का टाइमर लगाओ"))
    @Test fun timerCombined() = assertEquals(Command.Timer(5400), p("timer 1 hour 30 minutes"))
    @Test fun timerAttached() = assertEquals(Command.Timer(120), p("2min timer"))
    @Test fun timerWordNumber() = assertEquals(Command.Timer(600), p("das minute ka timer"))

    // Health, boost
    @Test fun batteryHinglish() = assertEquals(Command.Health, p("battery kitni hai"))
    @Test fun batteryDeva() = assertEquals(Command.Health, p("बैटरी कितनी है"))
    @Test fun phoneHaal() = assertEquals(Command.Health, p("phone ka haal"))
    @Test fun phoneHaalDeva() = assertEquals(Command.Health, p("फोन का हाल बताओ"))
    @Test fun boostEnglish() = assertEquals(Command.Boost, p("speed up phone"))
    @Test fun boostHinglish() = assertEquals(Command.Boost, p("phone tez karo"))
    @Test fun boostDeva() = assertEquals(Command.Boost, p("फोन तेज़ करो"))
    @Test fun boostSlow() = assertEquals(Command.Boost, p("phone slow hai"))

    // Settings
    @Test fun wifi() = assertEquals(Command.Settings(SettingsPage.WIFI), p("open wifi settings"))
    @Test fun wifiDash() = assertEquals(Command.Settings(SettingsPage.WIFI), p("Wi-Fi on karo"))
    @Test fun bluetoothDeva() = assertEquals(Command.Settings(SettingsPage.BLUETOOTH), p("ब्लूटूथ खोलो"))
    @Test fun mobileData() = assertEquals(Command.Settings(SettingsPage.DATA), p("mobile data band karo"))
    @Test fun brightness() = assertEquals(Command.Settings(SettingsPage.DISPLAY), p("brightness kam karo"))
    @Test fun volume() = assertEquals(Command.Settings(SettingsPage.SOUND), p("volume badhao"))
    @Test fun volumeDeva() = assertEquals(Command.Settings(SettingsPage.SOUND), p("आवाज़ बढ़ाओ"))
    @Test fun location() = assertEquals(Command.Settings(SettingsPage.LOCATION), p("location on karo"))
    @Test fun batterySaver() = assertEquals(Command.Settings(SettingsPage.BATTERY), p("battery saver kholo"))
    @Test fun storage() = assertEquals(Command.Settings(SettingsPage.STORAGE), p("storage kitna bacha hai"))
    @Test fun settingsMain() = assertEquals(Command.Settings(SettingsPage.MAIN), p("सेटिंग खोलो"))

    // Camera, time, date, help, unknown
    @Test fun camera() = assertEquals(Command.Camera, p("open camera"))
    @Test fun cameraDeva() = assertEquals(Command.Camera, p("कैमरा खोलो"))
    @Test fun photoKheencho() = assertEquals(Command.Camera, p("photo kheencho"))
    @Test fun timeEnglish() = assertEquals(Command.Time, p("what time is it"))
    @Test fun timeHinglish() = assertEquals(Command.Time, p("time kya hua hai"))
    @Test fun timeKitneBaje() = assertEquals(Command.Time, p("kitne baje hain"))
    @Test fun timeDeva() = assertEquals(Command.Time, p("अभी समय क्या हुआ है"))
    @Test fun dateEnglish() = assertEquals(Command.Date, p("today's date"))
    @Test fun dateHinglish() = assertEquals(Command.Date, p("aaj ki tarikh kya hai"))
    @Test fun dateDeva() = assertEquals(Command.Date, p("आज की तारीख बताओ"))
    @Test fun helpEnglish() = assertEquals(Command.Help, p("help"))
    @Test fun helpHinglish() = assertEquals(Command.Help, p("madad karo"))
    @Test fun helpDeva() = assertEquals(Command.Help, p("मदद"))
    @Test fun unknown() = assertTrue(p("kuch bhi random baat") is Command.Unknown)
    @Test fun unknownEmpty() = assertTrue(p("   ") is Command.Unknown)
    @Test fun unknownKeepsText() = assertEquals(Command.Unknown("blah blah"), p("blah blah"))

    // Normalization
    @Test fun normalizeDigits() = assertEquals("alarm 6:30", CommandParser.normalize("Alarm ६:३०"))
    @Test fun normalizePunctuation() = assertEquals("hello world", CommandParser.normalize("  Hello,   WORLD!! "))
    @Test fun normalizeNukta() = assertEquals(CommandParser.normalize("फोनपे"), CommandParser.normalize("फ़ोनपे"))
    @Test fun normalizeWifi() = assertEquals("wifi on", CommandParser.normalize("Wi-Fi ON"))
}
