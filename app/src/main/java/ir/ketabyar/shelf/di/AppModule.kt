package ir.ketabyar.shelf.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.ketabyar.shelf.data.AppDatabase
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    private val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE books ADD COLUMN shelved INTEGER NOT NULL DEFAULT 0")}}
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ketabyar.db").addMigrations(MIGRATION_1_2).build()
}
