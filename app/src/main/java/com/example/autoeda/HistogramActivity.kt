package com.example.autoeda

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class HistogramActivity : AppCompatActivity() {

    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_histogram)

        // CSV 로드
        loadCsv()

        // UI 요소
        val actvColumn = findViewById<AutoCompleteTextView>(R.id.actvHistogramColumn)
        val actvBinSize = findViewById<AutoCompleteTextView>(R.id.actvBinSize)
        val binOptionLayout = findViewById<LinearLayout>(R.id.layoutBinOption)
        val histogramLayout = findViewById<LinearLayout>(R.id.layoutHistogramBars)
        val ratioTable = findViewById<TableLayout>(R.id.tableValueRatio)

        // 컬럼 드롭다운 (클릭하면 전체 헤더 쭉 뜸)
        val colAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            header
        )
        actvColumn.setAdapter(colAdapter)
        actvColumn.threshold = 0
        actvColumn.setOnClickListener { actvColumn.showDropDown() }
        actvColumn.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) actvColumn.showDropDown()
        }

        // Bin size 드롭다운: 퍼센트 선택 (전체 범위의 몇 %를 bin 폭으로 쓸지)
        val binPercentOptions = listOf("2%", "5%", "10%", "20%", "25%")
        val binAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            binPercentOptions
        )
        actvBinSize.setAdapter(binAdapter)
        actvBinSize.threshold = 0
        actvBinSize.setOnClickListener { actvBinSize.showDropDown() }
        actvBinSize.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) actvBinSize.showDropDown()
        }

        // 컬럼 선택 시 히스토그램 갱신
        actvColumn.setOnItemClickListener { _, _, _, _ ->
            val columnName = actvColumn.text.toString()
            updateHistogram(
                columnName,
                histogramLayout,
                ratioTable,
                actvBinSize,
                binOptionLayout
            )
        }

        // Bin 퍼센트 선택 시 히스토그램 갱신 (이미 numeric 컬럼 선택된 경우)
        actvBinSize.setOnItemClickListener { _, _, _, _ ->
            val columnName = actvColumn.text.toString()
            if (columnName.isNotBlank()) {
                updateHistogram(
                    columnName,
                    histogramLayout,
                    ratioTable,
                    actvBinSize,
                    binOptionLayout
                )
            }
        }

        // 네비게이션 버튼
        setupNavigation()
    }

    // ---------------------------- CSV LOAD ----------------------------
    private fun loadCsv() {
        val inputStream = resources.openRawResource(R.raw.iris)
        val csv = inputStream.bufferedReader().use { it.readLines() }
        header = csv.first().split(",")

        rows = csv.drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
    }

    // ---------------------------- 히스토그램 업데이트 ----------------------------
    private fun updateHistogram(
        column: String,
        histogramLayout: LinearLayout,
        ratioTable: TableLayout,
        actvBinSize: AutoCompleteTextView,
        binOptionLayout: LinearLayout
    ) {
        histogramLayout.removeAllViews()
        ratioTable.removeAllViews()

        val idx = header.indexOf(column)
        if (idx == -1) return

        val values = rows.map { it[idx].trim() }.filter { it.isNotEmpty() }
        val numericValues = values.mapNotNull { it.toDoubleOrNull() }

        // numeric 비율이 절반 이상이면 숫자형으로 취급
        val isNumeric = numericValues.size >= values.size * 0.5

        if (isNumeric) {
            binOptionLayout.visibility = LinearLayout.VISIBLE

            // 퍼센트 문자열 → 0~1 사이 Double (예: "10%" → 0.10)
            val percentStr = actvBinSize.text.toString().ifBlank { "10%" }
            val percent = percentStr.replace("%", "").toDoubleOrNull()?.div(100.0) ?: 0.10

            showNumericHistogram(
                numericValues,
                histogramLayout,
                ratioTable,
                percent
            )
        } else {
            binOptionLayout.visibility = LinearLayout.GONE
            showCategoricalHistogram(values, histogramLayout, ratioTable)
        }
    }

    // ---------------------------- 카테고리형 히스토그램 ----------------------------
    private fun showCategoricalHistogram(
        values: List<String>,
        histogramLayout: LinearLayout,
        ratioTable: TableLayout
    ) {
        val total = values.size.toDouble()
        val counts = values.groupingBy { it }.eachCount()

        val maxRatio = counts.values.maxOrNull()!! / total

        // 막대 그래프
        counts.forEach { (category, count) ->
            val ratio = count / total

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            val name = TextView(this).apply {
                text = category
                width = 180
                textSize = 13f
            }

            val bar = TextView(this).apply {
                width = (ratio / maxRatio * 400).toInt()
                height = 30
                setBackgroundColor(Color.parseColor("#4A90E2"))
            }

            val percent = TextView(this).apply {
                text = "${(ratio * 100).roundToInt()}%"
                setPadding(8, 0, 0, 0)
            }

            row.addView(name)
            row.addView(bar)
            row.addView(percent)

            histogramLayout.addView(row)
        }

        // 테이블 헤더
        val headerRow = TableRow(this)
        headerRow.addView(makeCell("Value", true))
        headerRow.addView(makeCell("Count", true))
        headerRow.addView(makeCell("Ratio", true))
        ratioTable.addView(headerRow)

        // 테이블 데이터
        counts.forEach { (category, count) ->
            val ratio = count / total
            val tr = TableRow(this)
            tr.addView(makeCell(category))
            tr.addView(makeCell(count.toString()))
            tr.addView(makeCell("${(ratio * 100).roundToInt()}%"))
            ratioTable.addView(tr)
        }
    }

    // ---------------------------- 숫자형 히스토그램 (bin 폭 = 전체 범위의 퍼센트) ----------------------------
    private fun showNumericHistogram(
        values: List<Double>,
        histogramLayout: LinearLayout,
        ratioTable: TableLayout,
        binPercent: Double   // 0.1 = 전체 range의 10%
    ) {
        if (values.isEmpty()) return

        val minVal = values.minOrNull()!!
        val maxVal = values.maxOrNull()!!
        val range = maxVal - minVal

        // bin 폭 = 범위 * 퍼센트 (너무 작으면 최소값 보정)
        val binWidth = max(range * binPercent, 1e-9)
        val numBins = max(1, ceil(range / binWidth).toInt())
        val bins = IntArray(numBins)

        // 값들을 bin에 할당
        values.forEach { v ->
            var index = ((v - minVal) / binWidth).toInt()
            if (index >= numBins) index = numBins - 1  // 최대값 안전 처리
            bins[index]++
        }

        val total = values.size.toDouble()
        val maxCount = bins.maxOrNull()!!.coerceAtLeast(1)

        // 그래프 막대
        for (i in 0 until numBins) {
            val start = minVal + i * binWidth
            val end = start + binWidth

            val count = bins[i]
            val ratio = count / total

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            val name = TextView(this).apply {
                text = String.format("%.2f ~ %.2f", start, end)
                width = 180
                textSize = 13f
            }

            val bar = TextView(this).apply {
                width = (ratio * 400 / (maxCount / total)).toInt()
                height = 30
                setBackgroundColor(Color.parseColor("#4A90E2"))
            }

            val percent = TextView(this).apply {
                text = "${(ratio * 100).roundToInt()}%"
                setPadding(8, 0, 0, 0)
            }

            row.addView(name)
            row.addView(bar)
            row.addView(percent)
            histogramLayout.addView(row)
        }

        // 테이블 헤더
        val headerRow = TableRow(this)
        headerRow.addView(makeCell("Range", true))
        headerRow.addView(makeCell("Count", true))
        headerRow.addView(makeCell("Ratio", true))
        ratioTable.addView(headerRow)

        // 테이블 데이터
        for (i in 0 until numBins) {
            val start = minVal + i * binWidth
            val end = start + binWidth
            val count = bins[i]
            val ratio = count / total

            val tr = TableRow(this)
            tr.addView(makeCell(String.format("%.2f ~ %.2f", start, end)))
            tr.addView(makeCell(count.toString()))
            tr.addView(makeCell("${(ratio * 100).roundToInt()}%"))
            ratioTable.addView(tr)
        }
    }

    // ---------------------------- 공용 셀 ----------------------------
    private fun makeCell(text: String, header: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(12, 8, 12, 8)
            gravity = Gravity.CENTER
            if (header) setBackgroundColor(Color.parseColor("#EFEFEF"))
        }
    }

    // ---------------------------- 네비 버튼 ----------------------------
    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNavColumnStats).setOnClickListener {
            startActivity(Intent(this, ColumnStatsActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavDataQuality).setOnClickListener {
            startActivity(Intent(this, DataQualityActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavHistogram).setOnClickListener {
            // 현재 화면
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            startActivity(Intent(this, TargetAnalysisActivity::class.java))
            finish()
        }
    }
}
