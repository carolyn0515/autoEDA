package com.example.autoeda

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DataQualityActivity : AppCompatActivity() {

    data class ColQuality(
        val name: String,
        val isNumeric: Boolean,
        val missingRatio: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_quality)

        // 1) iris.csv 로드
        val inputStream = resources.openRawResource(R.raw.iris)
        val csvLines = inputStream.bufferedReader().use { it.readLines() }
        if (csvLines.isEmpty()) return

        val header = csvLines.first().split(",")
        val rows = csvLines
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }

        // 2) 결측률 + 숫자 컬럼 여부
        val qualities = computeColumnQuality(header, rows)

        // 3) Missing Values UI 세팅
        setupMissingValues(qualities)

        // 4) Correlation Heatmap UI 세팅
        setupCorrelationHeatmap(header, rows, qualities)

        // 5) 상단 네비게이션 버튼
        setupNavigation()
    }

    // ---- Data 계산 ----

    private fun computeColumnQuality(
        header: List<String>,
        rows: List<List<String>>
    ): List<ColQuality> {
        val nRows = rows.size.toDouble()

        return header.mapIndexed { colIdx, name ->
            val values = rows.map { row -> row.getOrNull(colIdx)?.trim().orEmpty() }

            val numericValues = values.mapNotNull { it.toDoubleOrNull() }
            val missingCount = values.count { it.isEmpty() }
            val missingRatio = missingCount / nRows
            val isNumeric = numericValues.isNotEmpty() && numericValues.size >= nRows * 0.5

            ColQuality(
                name = name,
                isNumeric = isNumeric,
                missingRatio = missingRatio
            )
        }
    }

    // ---- Missing Values UI ----

    private fun setupMissingValues(qualities: List<ColQuality>) {
        val container = findViewById<LinearLayout>(R.id.layoutMissingValues)
        container.removeAllViews()

        val maxNameLen = qualities.maxOfOrNull { it.name.length } ?: 0
        val nameWidth = (maxNameLen * 10).coerceAtLeast(60)  // 대충 고정 폭

        qualities.forEach { q ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvName = TextView(this).apply {
                text = q.name
                textSize = 12f
                width = nameWidth
            }

            val progress = ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                progress = (q.missingRatio * 100).roundToInt()
                layoutParams = LinearLayout.LayoutParams(0, 20, 1f)
                // 기본 tint 색 사용 (파란색 막대)
            }

            val tvPercent = TextView(this).apply {
                text = String.format("%.1f%%", q.missingRatio * 100)
                textSize = 12f
                setPadding(8, 0, 0, 0)
            }

            row.addView(tvName)
            row.addView(progress)
            row.addView(tvPercent)

            container.addView(row)
        }
    }

    // ---- Correlation Heatmap UI ----

    private fun setupCorrelationHeatmap(
        header: List<String>,
        rows: List<List<String>>,
        qualities: List<ColQuality>
    ) {
        val numericCols = qualities
            .filter { it.isNumeric }
            .map { it.name }

        if (numericCols.isEmpty()) return

        val (names, matrix) = computeCorrelationMatrix(header, rows, numericCols)

        val table = findViewById<TableLayout>(R.id.tableCorrHeatmap)
        table.removeAllViews()

        // 헤더 행
        val headerRow = TableRow(this)
        headerRow.addView(makeCorrHeaderCell("")) // 좌상단 비우기
        names.forEach { colName ->
            headerRow.addView(makeCorrHeaderCell(colName))
        }
        table.addView(headerRow)

        // 각 행
        for (i in names.indices) {
            val tr = TableRow(this)

            // 행 이름
            tr.addView(makeCorrHeaderCell(names[i]))

            for (j in names.indices) {
                val corr = matrix[i][j]
                tr.addView(makeCorrValueCell(corr))
            }
            table.addView(tr)
        }
    }

    private fun computeCorrelationMatrix(
        header: List<String>,
        rows: List<List<String>>,
        numericCols: List<String>
    ): Pair<List<String>, Array<DoubleArray>> {

        val colIndices = numericCols.map { colName ->
            header.indexOf(colName)
        }

        // 각 컬럼의 numeric 값 리스트
        val dataCols: List<List<Double>> = colIndices.map { idx ->
            rows.mapNotNull { row -> row.getOrNull(idx)?.trim()?.toDoubleOrNull() }
        }

        val n = dataCols.first().size
        val k = dataCols.size
        val matrix = Array(k) { DoubleArray(k) }

        // 각 컬럼 평균, 표준편차
        val means = DoubleArray(size = k) { i -> dataCols[i].average() }
        val stds = DoubleArray(size = k) { i ->
            val m = means[i]
            val vals = dataCols[i]
            val variance = vals.sumOf { (it - m) * (it - m) } / (n - 1)
            sqrt(variance)   // ← 마지막 줄에 Double을 리턴
        }

        // 상관계수 계산 (Pearson)
        for (i in 0 until k) {
            for (j in 0 until k) {
                if (i == j) {
                    matrix[i][j] = 1.0
                } else {
                    var sum = 0.0
                    val xi = dataCols[i]
                    val xj = dataCols[j]
                    for (t in 0 until n) {
                        sum += (xi[t] - means[i]) * (xj[t] - means[j])
                    }
                    val cov = sum / (n - 1)
                    matrix[i][j] = if (stds[i] == 0.0 || stds[j] == 0.0) 0.0
                    else cov / (stds[i] * stds[j])
                }
            }
        }

        return numericCols to matrix
    }

    private fun makeCorrHeaderCell(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            setPadding(8, 4, 8, 4)
            gravity = Gravity.CENTER
        }

    private fun makeCorrValueCell(corr: Double): TextView {
        val txt = TextView(this).apply {
            text = String.format("%.2f", corr)
            textSize = 11f
            setPadding(4, 4, 4, 4)
            gravity = Gravity.CENTER
        }

        // |corr| 값으로 색 intensity 결정 (0~1 → 0~180)
        val intensity = (abs(corr) * 180).roundToInt().coerceIn(0, 180)
        // 양수: 파란색 쪽, 음수: 붉은색 쪽
        txt.setBackgroundColor(
            if (corr >= 0) {
                Color.rgb(230 - intensity, 230 - intensity, 255)
            } else {
                Color.rgb(255, 230 - intensity, 230 - intensity)
            }
        )

        return txt
    }

    // ---- 상단 네비게이션 ----

    private fun setupNavigation() {
        val btnColumn = findViewById<Button>(R.id.btnNavColumnStats)
        val btnDataQuality = findViewById<Button>(R.id.btnNavDataQuality)
        val btnHistogram = findViewById<Button>(R.id.btnNavHistogram)
        val btnTarget = findViewById<Button>(R.id.btnNavTargetAnalysis)

        btnColumn.setOnClickListener {
            startActivity(Intent(this, ColumnStatsActivity::class.java))
            finish()
        }

        // DataQualityActivity 에서 자기 자신 버튼은 아무 동작 X
        btnDataQuality.setOnClickListener {
            // no-op
        }

        btnHistogram.setOnClickListener {
            startActivity(Intent(this, HistogramActivity::class.java))
            finish()
        }

        btnTarget.setOnClickListener {
            startActivity(Intent(this, TargetAnalysisActivity::class.java))
            finish()
        }
    }
}
