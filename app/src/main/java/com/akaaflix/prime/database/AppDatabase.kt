package com.akaaflix.prime.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.akaaflix.prime.database.dao.CatalogDao
import com.akaaflix.prime.database.dao.EpgDao
import com.akaaflix.prime.database.dao.HiddenContentDao
import com.akaaflix.prime.database.dao.LibraryDao
import com.akaaflix.prime.database.dao.NotificationDao
import com.akaaflix.prime.database.dao.PlaybackHistoryDao
import com.akaaflix.prime.database.dao.PlaylistDao
import com.akaaflix.prime.database.dao.SearchHistoryDao
import com.akaaflix.prime.database.dao.UserSettingsDao
import com.akaaflix.prime.database.entity.CategoryEntity
import com.akaaflix.prime.database.entity.ChannelEntity
import com.akaaflix.prime.database.entity.CustomGroupEntity
import com.akaaflix.prime.database.entity.EpgCacheEntity
import com.akaaflix.prime.database.entity.FavoriteEntity
import com.akaaflix.prime.database.entity.GroupContentEntity
import com.akaaflix.prime.database.entity.HiddenContentEntity
import com.akaaflix.prime.database.entity.MovieEntity
import com.akaaflix.prime.database.entity.NotificationEntity
import com.akaaflix.prime.database.entity.PlaybackHistoryEntity
import com.akaaflix.prime.database.entity.PlaylistEntity
import com.akaaflix.prime.database.entity.SearchHistoryEntity
import com.akaaflix.prime.database.entity.SeriesEntity
import com.akaaflix.prime.database.entity.UserSettingsEntity

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        FavoriteEntity::class,
        CustomGroupEntity::class,
        GroupContentEntity::class,
        HiddenContentEntity::class,
        PlaybackHistoryEntity::class,
        UserSettingsEntity::class,
        SearchHistoryEntity::class,
        EpgCacheEntity::class,
        NotificationEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun catalogDao(): CatalogDao
    abstract fun libraryDao(): LibraryDao
    abstract fun hiddenContentDao(): HiddenContentDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun epgDao(): EpgDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val VERSION = 1
        const val NAME = "akaaflix.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Schema migrations. Every schema change must bump [AppDatabase.VERSION], export the new
 * schema (app/schemas) and add a [Migration] here so user data (playlists, favorites,
 * groups, hidden content, history) is preserved across app updates. Catalog tables are a
 * cache and may be rebuilt by the migration if needed.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
