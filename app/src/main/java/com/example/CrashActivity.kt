package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = intent.getStringExtra("stack_trace") ?: "Unknown error"
        
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = "FATAL CRASH:\n\n$stackTrace"
            textSize = 12f
            setPadding(32, 32, 32, 32)
            setTextColor(android.graphics.Color.RED)
        }
        scrollView.addView(textView)
        setContentView(scrollView)
    }
}
