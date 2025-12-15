package com.example.autoeda

import android.content.Context
import java.io.File

object CsvLoader {

    fun loadLines(context: Context, intentPath: String?): List<String> {
        val prefs = context.getSharedPreferences(DataSource.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(DataSource.KEY_CSV_FILE_PATH, null)

        val pathToUse = when {
            !intentPath.isNullOrBlank() -> intentPath
            !savedPath.isNullOrBlank() -> savedPath
            else -> null
        }

        return try {
            if (!pathToUse.isNullOrBlank() && File(pathToUse).exists()) {
                File(pathToUse).bufferedReader().use { it.readLines() }
            } else {
                context.resources.openRawResource(R.raw.iris).bufferedReader().use { it.readLines() }
            }
        } catch (e: Exception) {
            // 마지막 보험: raw
            context.resources.openRawResource(R.raw.iris).bufferedReader().use { it.readLines() }
        }
    }

    // 따옴표 CSV split
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> { inQuotes = !inQuotes; sb.append(ch) }
                ',' -> {
                    if (inQuotes) sb.append(ch)
                    else { out.add(sb.toString()); sb.setLength(0) }
                }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }
}
