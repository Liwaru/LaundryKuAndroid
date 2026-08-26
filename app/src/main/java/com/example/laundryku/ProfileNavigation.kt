package com.example.laundryku

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.openProfileForLevel(level: Int) {
    startActivity(Intent(this, ProfileActivity::class.java).apply {
        putExtra(ProfileActivity.EXTRA_LEVEL, level)
    })
}

fun AppCompatActivity.openScreen(activityClass: Class<out AppCompatActivity>) {
    if (this::class.java == activityClass) return
    startActivity(Intent(this, activityClass).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    })
}
