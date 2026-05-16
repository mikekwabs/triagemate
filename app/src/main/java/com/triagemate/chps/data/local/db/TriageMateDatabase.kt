package com.triagemate.chps.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.triagemate.chps.data.local.model.AssessmentEntity

@Database(entities = [AssessmentEntity::class], version = 7, exportSchema = true)
abstract class TriageMateDatabase : RoomDatabase() {
    abstract fun assessmentDao(): AssessmentDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assessments ADD COLUMN safety_override_applied INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE assessments ADD COLUMN safety_override_reason TEXT")
                db.execSQL("ALTER TABLE assessments ADD COLUMN original_gemma_urgency TEXT")
                db.execSQL("ALTER TABLE assessments ADD COLUMN confidence_level TEXT NOT NULL DEFAULT 'HIGH'")
            }
        }
    }
}
