package com.triagemate.chps.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.triagemate.chps.data.local.db.AssessmentDao
import com.triagemate.chps.data.local.db.TriageMateDatabase
import com.triagemate.chps.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE assessments ADD COLUMN photo_uri TEXT")
            db.execSQL("ALTER TABLE assessments ADD COLUMN visual_finding TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE assessments ADD COLUMN confirmed_visual_finding_json TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE assessments ADD COLUMN patient_sex TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE assessments ADD COLUMN duration_millis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE assessments ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("ALTER TABLE assessments ADD COLUMN synced_at INTEGER")
            db.execSQL("ALTER TABLE assessments ADD COLUMN compound_id TEXT NOT NULL DEFAULT ''")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TriageMateDatabase {
        return Room.databaseBuilder(
                context,
                TriageMateDatabase::class.java,
                Constants.DATABASE_NAME
            )
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(TriageMateDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideAssessmentDao(database: TriageMateDatabase): AssessmentDao {
        return database.assessmentDao()
    }
}
