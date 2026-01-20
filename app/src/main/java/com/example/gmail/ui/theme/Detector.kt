package com.example.gmail

import android.content.Context
import java.io.File

object Detector {

    fun checkFiles(context: Context): Boolean {
        val file = File(context.filesDir, "worm_signature.txt")
        if (!file.exists()) return false

        val content = file.readText()
        val signatures = listOf("AUTO_REPLICATE", "C2_BEACON", "PORT_SCAN")

        return signatures.any { content.contains(it) }
    }
}
