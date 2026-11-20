package dev.pallav.phonesaar.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    private fun c(pkg: String, cat: Int = -1, label: String = "x") = Categorizer.categorize(pkg, cat, label)

    @Test fun packageMapCoversManyApps() = assertTrue(Categorizer.mappedPackageCount >= 150)
    @Test fun whatsapp() = assertEquals(Category.CHAT, c("com.whatsapp"))
    @Test fun upi() = assertEquals(Category.MONEY, c("com.phonepe.app"))
    @Test fun gpay() = assertEquals(Category.MONEY, c("com.google.android.apps.nbu.paisa.user"))
    @Test fun maps() = assertEquals(Category.TRAVEL, c("com.google.android.apps.maps"))
    @Test fun rapidoCaptain() = assertEquals(Category.WORK, c("com.rapido.rider"))
    @Test fun swiggyDelivery() = assertEquals(Category.WORK, c("in.swiggy.deliveryapp"))
    @Test fun uberDriver() = assertEquals(Category.WORK, c("com.ubercab.driver"))
    @Test fun uberRiderIsTravel() = assertEquals(Category.TRAVEL, c("com.ubercab"))
    @Test fun digilocker() = assertEquals(Category.GOV_HEALTH, c("com.digilocker.android"))
    @Test fun youtube() = assertEquals(Category.MEDIA, c("com.google.android.youtube"))
    @Test fun amazon() = assertEquals(Category.SHOPPING, c("in.amazon.mShop.android.shopping"))
    @Test fun calculator() = assertEquals(Category.TOOLS, c("com.google.android.calculator"))
    @Test fun game() = assertEquals(Category.GAMES, c("com.dts.freefireth"))
    @Test fun packageBeatsAndroidCategory() = assertEquals(Category.CHAT, c("com.whatsapp", 2))
    @Test fun androidGame() = assertEquals(Category.GAMES, c("com.unknown.a", 0))
    @Test fun androidVideo() = assertEquals(Category.MEDIA, c("com.unknown.a", 2))
    @Test fun androidAudio() = assertEquals(Category.MEDIA, c("com.unknown.a", 1))
    @Test fun androidImage() = assertEquals(Category.PHOTOS, c("com.unknown.a", 3))
    @Test fun androidSocial() = assertEquals(Category.CHAT, c("com.unknown.a", 4))
    @Test fun androidMaps() = assertEquals(Category.TRAVEL, c("com.unknown.a", 6))
    @Test fun androidProductivity() = assertEquals(Category.TOOLS, c("com.unknown.a", 7))
    @Test fun androidCategoryBeatsKeyword() = assertEquals(Category.GAMES, c("com.unknown.a", 0, "Bank Quest"))
    @Test fun keywordBank() = assertEquals(Category.MONEY, c("com.unknown.a", -1, "City Bank Mobile"))
    @Test fun keywordDeva() = assertEquals(Category.PHOTOS, c("com.unknown.a", -1, "मेरा कैमरा"))
    @Test fun keywordCaptain() = assertEquals(Category.WORK, c("com.unknown.a", -1, "Fleet Captain"))
    @Test fun keywordHealth() = assertEquals(Category.GOV_HEALTH, c("com.unknown.a", -1, "Daily Health Tips"))
    @Test fun unknownIsOther() = assertEquals(Category.OTHER, c("com.unknown.a", -1, "Zork"))
    @Test fun bankPrefix() = assertEquals(Category.MONEY, c("com.sbi.new.thing"))
    @Test fun govPrefix() = assertEquals(Category.GOV_HEALTH, c("in.gov.new.thing"))
}
