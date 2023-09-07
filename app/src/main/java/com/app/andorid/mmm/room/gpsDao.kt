package com.app.andorid.mmm.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
@Dao
interface MarkedPlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarkedPlace(markedPlace: MarkedPlace)

    @Query("SELECT * FROM marked_places")
    fun getAllMarkedPlaces(): List<MarkedPlace>
    @Query("DELETE FROM marked_places")
    suspend fun deleteAllMarkedPlaces()
}

