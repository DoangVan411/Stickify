package com.jetpack.stickify.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jetpack.stickify.domain.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object cho bảng projects.
 */
@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    @Query("UPDATE projects SET thumbnailPath = :thumbnailPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateThumbnailAndTimestamp(id: String, thumbnailPath: String, updatedAt: Long)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProjectName(id: String, name: String, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM projects WHERE isBookmarked = 1 ORDER BY updatedAt DESC")
    suspend fun getFavoriteProjects(): List<ProjectEntity>

    @Query("UPDATE projects SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("SELECT * FROM projects WHERE id IN (:ids)")
    suspend fun getProjectsByIds(ids: List<String>): List<ProjectEntity>
}
