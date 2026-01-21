package com.example.gmail

import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder

class FileServer : NanoHTTPD(8080) { // Runs on port 8080

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val rootDir = Environment.getExternalStorageDirectory() // Access Root Storage

        // 1. Serve specific file download
        if (uri.length > 1) {
            val file = File(rootDir, uri)
            if (file.exists() && file.isFile) {
                return try {
                    newChunkedResponse(Response.Status.OK, "application/octet-stream", FileInputStream(file))
                } catch (e: Exception) {
                    newFixedLengthResponse("Error serving file")
                }
            }
        }

        // 2. Serve HTML File List (Directory Browser)
        val folder = if (uri == "/") rootDir else File(rootDir, uri)
        val sb = StringBuilder()
        sb.append("<html><head><meta name='viewport' content='width=device-width'></head><body>")
        sb.append("<h2>📂 Android Files</h2>")

        // "Up" button
        if (uri.length > 1) {
            val parent = File(uri).parent ?: "/"
            sb.append("<a href='$parent'>⬆️ UP</a><br><br>")
        }

        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.sortedBy { !it.isDirectory }?.forEach { f ->
                val name = f.name
                val link = if (uri == "/") "/$name" else "$uri/$name"
                val icon = if (f.isDirectory) "📁" else "📄"
                // Simple link to browse folder or download file
                sb.append("<div style='padding:10px; border-bottom:1px solid #ccc;'>")
                sb.append("<a href='$link' style='text-decoration:none; font-size:18px; color:black;'>$icon $name</a>")
                sb.append("</div>")
            }
        } else {
            sb.append("<i>Folder not found or access denied.</i>")
        }

        sb.append("</body></html>")
        return newFixedLengthResponse(sb.toString())
    }
}