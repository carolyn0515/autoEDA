package com.example.autoeda

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class HistogramActivity : AppCompatActivity() {

    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>

    private fun forwardIntent(clazz: Class<*>): Intent {
        val intent = Intent(this, clazz)
        val path = getCurrentCsvPath()
        if (!path.isNullOrBlank()) intent.putExtra(DataSource.EXTRA_CSV_FILE_PATH, path)
        return intent
    }

    private fun getCurrentCsvPath(): String? {
        val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
        return intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
            ?: prefs.getString(DataSource.KEY_CSV_FILE_PATH, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_histogram)

        loadCsv()

        val actvColumn = findViewById<AutoCompleteTextView>(R.id.actvHistogramColumn)
        val actvBinSize = findViewById<AutoCompleteTextView>(R.id.actvBinSize)
        val binOptionLayout = findViewById<LinearLayout>(R.id.layoutBinOption)
        val histogramLayout = findViewById<LinearLayout>(R.id.layoutHistogramBars)
        val ratioTable = findViewById<TableLayout>(R.id.tableValueRatio)

        val colAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, header)
        actvColumn.setAdapter(colAdapter)
        actvColumn.threshold = 0
        actvColumn.setOnClickListener { actvColumn.showDropDown() }
        actvColumn.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvColumn.showDropDown() }

        val binPercentOptions = listOf("2%", "5%", "10%", "20%", "25%")
        val binAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, binPercentOptions)
        actvBinSize.setAdapter(binAdapter)
        actvBinSize.threshold = 0
        actvBinSize.setOnClickListener { actvBinSize.showDropDown() }
        actvBinSize.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvBinSize.showDropDown() }

        actvColumn.setOnItemClickListener { _, _, _, _ ->
            updateHistogram(actvColumn.text.toString(), histogramLayout, ratioTable, actvBinSize, binOptionLayout)
        }

        actvBinSize.setOnItemClickListener { _, _, _, _ ->
            val columnName = actvColumn.text.toString()
            if (columnName.isNotBlank()) {
                updateHistogram(columnName, histogramLayout, ratioTable, actvBinSize, binOptionLayout)
            }
        }

        setupNavigation()
    }

    // ---------------------------- Utils ----------------------------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    private fun sp(v: Float): Float = v

    private val BLUE = Color.parseColor("#1E63FF")
    private val BLUE_SOFT = Color.parseColor("#EAF1FF")
    private val TEXT_DARK = Color.parseColor("#222222")
    private val BORDER = Color.parseColor("#E7E7E7")
    private val HEADER_BG = Color.parseColor("#F2F2F2")

    // ---------------------------- CSV LOAD ----------------------------

    private fun loadCsv() {
        val intentPath = intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
        val csv = CsvLoader.loadLines(this, intentPath)
        header = CsvLoader.splitCsvLine(csv.first()).map { it.trim().trim('"') }
        rows = csv.drop(1)
            .filter { it.isNotBlank() }
            .map { line -> CsvLoader.splitCsvLine(line).map { it.trim().trim('"') } }
    }

    // ---------------------------- 업데이트 ----------------------------

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

        val values = rows.map { it.getOrNull(idx)?.trim().orEmpty() }.filter { it.isNotEmpty() }
        val numericValues = values.mapNotNull { it.toDoubleOrNull() }

        val isNumeric = numericValues.size >= values.size * 0.5

        if (isNumeric) {
            binOptionLayout.visibility = LinearLayout.VISIBLE
            val percentStr = actvBinSize.text.toString().ifBlank { "10%" }
            val percent = percentStr.replace("%", "").toDoubleOrNull()?.div(100.0) ?: 0.10
            showNumericHistogram(numericValues, histogramLayout, ratioTable, percent)
        } else {
            binOptionLayout.visibility = LinearLayout.GONE
            showCategoricalHistogram(values, histogramLayout, ratioTable)
        }
    }

    private fun addBarRow(parent: LinearLayout, label: String, ratio: Double, countText: String? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvLabel = TextView(this).apply {
            text = label
            textSize = sp(16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_DARK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val rightText = buildString {
            if (!countText.isNullOrBlank()) append(countText).append(" · ")
            append(String.format("%.0f%%", ratio * 100))
        }

        val tvRight = TextView(this).apply {
            text = rightText
            textSize = sp(15f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_DARK)
        }

        top.addView(tvLabel)
        top.addView(tvRight)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (ratio * 1000).roundToInt().coerceIn(0, 1000)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply {
                topMargin = dp(8)
            }
            progressTintList = ColorStateList.valueOf(BLUE)
            progressBackgroundTintList = ColorStateList.valueOf(BLUE_SOFT)
        }

        val divider = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(14)
            }
            setBackgroundColor(BORDER)
        }

        row.addView(top)
        row.addView(bar)
        row.addView(divider)
        parent.addView(row)
    }

    private fun showCategoricalHistogram(values: List<String>, histogramLayout: LinearLayout, ratioTable: TableLayout) {
        val total = values.size.toDouble().coerceAtLeast(1.0)
        val counts = values.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        counts.forEach { (category, count) ->
            addBarRow(histogramLayout, category, count / total, count.toString())
        }

        ratioTable.addView(makeTableHeaderRow(listOf("Value", "Count", "Ratio")))
        counts.forEach { (category, count) ->
            val ratio = count / total
            ratioTable.addView(makeTableRow(listOf(category, count.toString(), String.format("%.0f%%", ratio * 100))))
        }
    }

    private fun showNumericHistogram(values: List<Double>, histogramLayout: LinearLayout, ratioTable: TableLayout, binPercent: Double) {
        if (values.isEmpty()) return

        val minVal = values.minOrNull()!!
        val maxVal = values.maxOrNull()!!
        val range = (maxVal - minVal)

        val binWidth = max(range * binPercent, 1e-9)
        val numBins = max(1, ceil(range / binWidth).toInt())
        val bins = IntArray(numBins)

        values.forEach { v ->
            var index = ((v - minVal) / binWidth).toInt()
            if (index >= numBins) index = numBins - 1
            bins[index]++
        }

        val total = values.size.toDouble().coerceAtLeast(1.0)

        for (i in 0 until numBins) {
            val start = minVal + i * binWidth
            val end = start + binWidth
            val count = bins[i]
            addBarRow(histogramLayout, String.format("%.2f ~ %.2f", start, end), count / total, count.toString())
        }

        ratioTable.addView(makeTableHeaderRow(listOf("Range", "Count", "Ratio")))
        for (i in 0 until numBins) {
            val start = minVal + i * binWidth
            val end = start + binWidth
            val count = bins[i]
            val ratio = count / total
            ratioTable.addView(
                makeTableRow(
                    listOf(
                        String.format("%.2f ~ %.2f", start, end),
                        count.toString(),
                        String.format("%.0f%%", ratio * 100)
                    )
                )
            )
        }
    }

    private fun makeTableHeaderRow(cols: List<String>): TableRow =
        TableRow(this).apply { cols.forEach { addView(makeCell(it, true)) } }

    private fun makeTableRow(cols: List<String>): TableRow =
        TableRow(this).apply { cols.forEach { addView(makeCell(it, false)) } }

    private fun makeCell(text: String, isHeader: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sp(15f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            typeface = Typeface.DEFAULT_BOLD
            if (isHeader) setBackgroundColor(HEADER_BG)
        }

    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNavColumnStats).setOnClickListener {
            startActivity(forwardIntent(ColumnStatsActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavDataQuality).setOnClickListener {
            startActivity(forwardIntent(DataQualityActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavHistogram).setOnClickListener {
            // current
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            startActivity(forwardIntent(TargetAnalysisActivity::class.java))
            finish()
        }
    }
}
