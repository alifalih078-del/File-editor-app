package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "redesign_history")
data class RedesignHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalFileName: String,
    val inputType: String,
    val redesignedTitle: String,
    val userInstruction: String,
    val designThemeName: String,
    val changeSummary: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val pdfFilePath: String?,
    val docxFilePath: String?,
    val pageCount: Int,
    val documentJson: String,
    val createdAtMillis: Long
)
