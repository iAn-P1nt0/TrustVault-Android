package com.trustvault.android.data.local.dao

import androidx.room.*
import com.trustvault.android.data.local.entity.CredentialEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for credentials.
 */
@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun getAllCredentials(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE category = :category ORDER BY updatedAt DESC")
    fun getCredentialsByCategory(category: String): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getCredentialById(id: Long): CredentialEntity?

    @Query("SELECT * FROM credentials WHERE title LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchCredentials(query: String): Flow<List<CredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: CredentialEntity): Long

    @Update
    suspend fun updateCredential(credential: CredentialEntity)

    @Delete
    suspend fun deleteCredential(credential: CredentialEntity)

    @Query("DELETE FROM credentials")
    suspend fun deleteAllCredentials()
}
