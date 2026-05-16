package com.triagemate.chps.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.triagemate.chps.data.local.model.AssessmentEntity

@Database(entities = [AssessmentEntity::class], version = 6, exportSchema = true)
abstract class TriageMateDatabase : RoomDatabase() {
    abstract fun assessmentDao(): AssessmentDao
}
