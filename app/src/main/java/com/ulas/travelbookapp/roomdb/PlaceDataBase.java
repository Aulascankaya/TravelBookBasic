package com.ulas.travelbookapp.roomdb;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.ulas.travelbookapp.model.Place;

@Database(entities = {Place.class},version = 1)
public abstract class PlaceDataBase  extends RoomDatabase {
    public abstract PlaceDao placeDao();

}
