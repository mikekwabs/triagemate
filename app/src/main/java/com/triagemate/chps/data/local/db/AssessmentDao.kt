package com.triagemate.chps.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.triagemate.chps.data.local.model.AssessmentEntity
import kotlinx.coroutines.flow.Flow

data class UrgencyCount(
    val urgency: String,
    val count: Int
)

@Dao
interface AssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: AssessmentEntity): Long

    @Query("SELECT * FROM assessments ORDER BY timestamp DESC")
    fun getAllAssessments(): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE id = :id")
    suspend fun getAssessmentById(id: Long): AssessmentEntity?

    @Query("SELECT COUNT(*) FROM assessments WHERE sync_status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM assessments WHERE sync_status = 'PENDING'")
    suspend fun getPendingAssessments(): List<AssessmentEntity>

    @Query("UPDATE assessments SET sync_status = 'SYNCED', synced_at = :timestamp WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>, timestamp: Long)

    @Query("SELECT COUNT(*) FROM assessments WHERE timestamp > :startOfMonth")
    fun getCountThisMonth(startOfMonth: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM assessments WHERE timestamp >= :start AND timestamp < :end")
    fun getCountBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT urgency, COUNT(*) as count FROM assessments WHERE timestamp > :startOfMonth GROUP BY urgency")
    fun getUrgencyDistributionThisMonth(startOfMonth: Long): Flow<List<UrgencyCount>>

    @Query("SELECT urgency, COUNT(*) as count FROM assessments WHERE timestamp >= :start AND timestamp < :end GROUP BY urgency")
    fun getUrgencyDistributionBetween(start: Long, end: Long): Flow<List<UrgencyCount>>

    @Query("SELECT * FROM assessments WHERE timestamp > :startOfMonth ORDER BY timestamp DESC")
    fun getAssessmentsThisMonth(startOfMonth: Long): Flow<List<AssessmentEntity>>

    @Query("SELECT symptoms_json FROM assessments WHERE timestamp > :startOfMonth")
    suspend fun getAllSymptomsJsonThisMonth(startOfMonth: Long): List<String>

    @Query("SELECT AVG(duration_millis) FROM assessments WHERE timestamp > :startOfMonth AND duration_millis > 0")
    fun getAverageDurationThisMonth(startOfMonth: Long): Flow<Double?>

    @Query("DELETE FROM assessments")
    suspend fun deleteAllAssessments()
}
