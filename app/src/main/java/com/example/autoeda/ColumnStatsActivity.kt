package com.example.autoeda

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class ColumnStatsActivity : AppCompatActivity() {

    data class ColumnStats(
        val name: String,
        val isNumeric: Boolean,
        val mean: Double?,
        val std: Double?,
        val min: Double?,
        val max: Double?,
        val missingRatio: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_column_stats)

        // 🔵 네비게이션
        setupNavigation()

        // 1) iris.csv 로드
        val inputStream = resources.openRawResource(R.raw.iris)
        val csvLines = inputStream.bufferedReader().use { it.readLines() }
        if (csvLines.isEmpty()) return

        val header = csvLines.first().split(",")
        val rows = csvLines
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }

        // 2) 통계 계산
        val statsList = computeStats(header, rows)

        // 3) 테이블 채우기
        val table = findViewById<TableLayout>(R.id.tableGeneralStats)
        while (table.childCount > 1) table.removeViewAt(1)

        statsList.filter { it.isNumeric }.forEach { stat ->
            table.addView(makeRow(stat))
        }

        // 4) Type Summary
        val tvTypeSummary = findViewById<TextView>(R.id.tvTypeSummaryValues)
        val numericCount = statsList.count { it.isNumeric }
        val categoricalCount = statsList.count { !it.isNumeric }
        tvTypeSummary.text =
            "Numeric: $numericCount    Categorical: $categoricalCount    Text: 0"

        // 5) Target Column 설정 + SharedPreferences 저장
        setupTargetSetButton(statsList)
    }

    // 🔵 네비게이션 기능
    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNavColumnStats).setOnClickListener {
            // 현재 페이지
        }
        findViewById<Button>(R.id.btnNavDataQuality).setOnClickListener {
            startActivity(Intent(this, DataQualityActivity::class.java))
        }
        findViewById<Button>(R.id.btnNavHistogram).setOnClickListener {
            startActivity(Intent(this, HistogramActivity::class.java))
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            startActivity(Intent(this, TargetAnalysisActivity::class.java))
        }
    }

    // 🔵 Target 컬럼 선택 & 타입 저장
    private fun setupTargetSetButton(statsList: List<ColumnStats>) {
        val actvTarget = findViewById<AutoCompleteTextView>(R.id.actvTargetColumn)
        val btnSetTarget = findViewById<Button>(R.id.btnSetTarget)

        // 👉 numeric만이 아니라 모든 컬럼 이름 후보
        val targetCandidates = statsList.map { it.name }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            targetCandidates
        )
        actvTarget.setAdapter(adapter)
        actvTarget.threshold = 0

        actvTarget.setOnClickListener { actvTarget.showDropDown() }
        actvTarget.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) actvTarget.showDropDown()
        }

        btnSetTarget.setOnClickListener {
            val selected = actvTarget.text?.toString()?.trim()
            if (selected.isNullOrEmpty()) {
                Toast.makeText(this, "타겟 컬럼을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 선택한 컬럼의 타입 찾기
            val stat = statsList.firstOrNull { it.name == selected }
            val targetType = if (stat?.isNumeric == true) "numeric" else "categorical"

            // SharedPreferences에 저장
            val prefs = getSharedPreferences("autoeda_prefs", MODE_PRIVATE)
            prefs.edit()
                .putString("target_column", selected)
                .putString("target_type", targetType)
                .apply()

            val typeLabel = if (targetType == "numeric") "Numeric" else "Categorical"
            Toast.makeText(
                this,
                "Target column: $selected ($typeLabel)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun computeStats(
        header: List<String>,
        rows: List<List<String>>
    ): List<ColumnStats> {
        val nRows = rows.size.toDouble()

        return header.mapIndexed { colIdx, name ->
            val values = rows.map { row -> row.getOrNull(colIdx)?.trim().orEmpty() }
            val numericValues = values.mapNotNull { it.toDoubleOrNull() }

            val missingCount = values.count { it.isEmpty() }
            val missingRatio = missingCount / nRows

            val isNumeric = numericValues.isNotEmpty() && numericValues.size >= nRows * 0.5

            if (!isNumeric) {
                ColumnStats(name, false, null, null, null, null, missingRatio)
            } else {
                val mean = numericValues.average()
                val min = numericValues.minOrNull()
                val max = numericValues.maxOrNull()
                val std = if (numericValues.size > 1) {
                    kotlin.math.sqrt(
                        numericValues.sumOf { (it - mean) * (it - mean) } /
                                (numericValues.size - 1)
                    )
                } else null

                ColumnStats(name, true, mean, std, min, max, missingRatio)
            }
        }
    }

    private fun makeRow(stat: ColumnStats): TableRow {
        fun makeCell(text: String): TextView =
            TextView(this).apply {
                this.text = text
                textSize = 12f
                setPadding(8)
            }

        return TableRow(this).apply {
            addView(makeCell(stat.name))
            addView(makeCell(stat.mean?.format3() ?: "-"))
            addView(makeCell(stat.std?.format3() ?: "-"))
            addView(makeCell(stat.min?.format3() ?: "-"))
            addView(makeCell(stat.max?.format3() ?: "-"))
            addView(makeCell("${(stat.missingRatio * 100).format1()}%"))
        }
    }

    private fun Double.format3(): String = String.format("%.3f", this)
    private fun Double.format1(): String = String.format("%.1f", this)
}
