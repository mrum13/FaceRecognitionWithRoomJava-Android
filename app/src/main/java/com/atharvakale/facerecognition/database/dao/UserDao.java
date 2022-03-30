package com.atharvakale.facerecognition.database.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.atharvakale.facerecognition.database.entity.User;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user")
    User getAll();

    @Query("INSERT INTO user (fullName, muka) VALUES(:name, :muka)")
    void insertAll(String name, String muka);

//    @Query("INSERT INTO user (bitmap) VALUES(:bitmap)")
//    void insertBitmap(String bitmap);

    @Query("INSERT INTO user (fullName) VALUES(:name)")
    void insertName(String name);

    @Query("INSERT INTO user (muka) VALUES(:muka)")
    void insertEmbed(String muka);

//    @Query("UPDATE user SET fullName=:name, email=:email WHERE userId=:uid")
//    void update(int uid, String name, String email);

//    @Query("SELECT fullName FROM user")
//    public List<User> getUserName();

//    @Query("SELECT muka FROM user")
//    User getEmbeddings();

//    @Delete
//    void delete(User user);
}
