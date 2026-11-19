package dev.pallav.phonesaar

import android.app.Application
import android.content.Context
import org.conscrypt.Conscrypt
import java.security.Security

class PhoneSaarApp : Application() {
    val repo: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        // libadb needs Conscrypt's TLS 1.3 for wireless debugging pairing.
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    companion object {
        fun repo(context: Context): Repository = (context.applicationContext as PhoneSaarApp).repo
    }
}
