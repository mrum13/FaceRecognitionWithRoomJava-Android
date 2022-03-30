package com.atharvakale.facerecognition.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.atharvakale.facerecognition.SimilarityClassifier;

import java.util.HashMap;

@Entity
public class User {
    @PrimaryKey
    public int userId;

    public String fullName;
//    public String bitmap;
    public String muka;
}


