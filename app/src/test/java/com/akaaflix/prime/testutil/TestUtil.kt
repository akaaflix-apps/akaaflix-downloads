package com.akaaflix.prime.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.network.XtreamApiFactory
import com.akaaflix.prime.network.XtreamDataSource
import com.akaaflix.prime.security.CredentialCipher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Reversible stand-in for the Android Keystore cipher (Keystore is unavailable on the JVM). */
class FakeCipher : CredentialCipher {
    override fun encrypt(plain: String): String = "ENC[" + plain.reversed().map { (it.code + 3).toChar() }.joinToString("") + "]"
    override fun decrypt(encoded: String): String {
        require(encoded.startsWith("ENC[") && encoded.endsWith("]"))
        return encoded.removePrefix("ENC[").removeSuffix("]").map { (it.code - 3).toChar() }.joinToString("").reversed()
    }
}

fun inMemoryDb(): AppDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
    .allowMainThreadQueries()
    .build()

fun testClient(readTimeoutMs: Long = 5_000): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
    .build()

fun xtream(client: OkHttpClient = testClient(), online: Boolean = true) = XtreamDataSource(XtreamApiFactory(client), isOnline = { online })
