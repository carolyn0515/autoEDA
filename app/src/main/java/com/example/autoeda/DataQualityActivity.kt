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
import androidx.core.view.setPadding
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DataQualityActivity : AppCompatActivity() {

    data class ColQuality(
        val name: String,
        val isNumeric: Boolean,
        val missingRatio: Double
    )

    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>
    private lateinit var qualities: List<ColQuality>

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
        setContentView(R.layout.activity_data_quality)

        loadCsv()

        qualities = computeColumnQuality(header, rows)

        setupMissingValues(qualities)
        setupCorrelationHeatmap(header, rows, qualities)
        setupNavigation()
    }

    // --------------------------- Utils ---------------------------

    private fun dp(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).roundToInt()

    private fun sp(sp: Float): Float = sp

    private fun pickTextColorForBackground(bgColor: Int): Int {
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        return if (luminance < 140) Color.WHITE else Color.BLACK
    }

    // --------------------------- CSV LOAD ---------------------------

    private fun loadCsv() {
        val intentPath = intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
        val csvLines = CsvLoader.loadLines(this, intentPath)
        if (csvLines.isEmpty()) {
            header = emptyList()
            rows = emptyList()
            return
        }

        header = CsvLoader.splitCsvLine(csvLines.first()).map { it.trim().trim('"') }
        rows = csvLines.drop(1)
            .filter { it.isNotBlank() }
            .map { line -> CsvLoader.splitCsvLine(line).map { it.trim().trim('"') } }
    }

    // --------------------------- Data 계산 ---------------------------

    private fun computeColumnQuality(header: List<String>, rows: List<List<String>>): List<ColQuality> {
        val nRows = rows.size.toDouble().coerceAtLeast(1.0)

        return header.mapIndexed { colIdx, name ->
            val values = rows.map { row -> row.getOrNull(colIdx)?.trim().orEmpty() }
            val numericValues = values.mapNotNull { it.toDoubleOrNull() }
            val missingCount = values.count { it.isEmpty() }
            val missingRatio = missingCount / nRows
            val isNumeric = numericValues.isNotEmpty() && numericValues.size >= nRows * 0.5

            ColQuality(name, isNumeric, missingRatio)
        }
    }

    // --------------------------- Missing Values UI ---------------------------

    private fun setupMissingValues(qualities: List<ColQuality>) {
        val container = findViewById<LinearLayout>(R.id.layoutMissingValues)
        container.removeAllViews()

        val sorted = qualities.sortedByDescending { it.missingRatio }
        val nameWidth = dp(120)

        sorted.forEach { q ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            val tvName = TextView(this).apply {
                text = q.name
                textSize = sp(15f)
                typeface = Typeface.DEFAULT_BOLD
                width = nameWidth
                setTextColor(Color.parseColor("#222222"))
            }

            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (q.missingRatio * 100).roundToInt().coerceIn(0, 100)
                layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                }
                progressTintList = ColorStateList.valueOf(Color.parseColor("#1E63FF"))
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8E8E8"))
            }

            val tvPercent = TextView(this).apply {
                text = String.format("%.1f%%", q.missingRatio * 100)
                textSize = sp(15f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#222222"))
                minWidth = dp(64)
                gravity = Gravity.END
            }

            row.addView(tvName)
            row.addView(progress)
            row.addView(tvPercent)

            container.addView(row)
        }
    }

    // --------------------------- Correlation Heatmap UI ---------------------------

    private fun setupCorrelationHeatmap(
        header: List<String>,
        rows: List<List<String>>,
        qualities: List<ColQuality>
    ) {
        val numericCols = qualities.filter { it.isNumeric }.map { it.name }
        if (numericCols.isEmpty()) return

        val (names, matrix) = computeCorrelationMatrix(header, rows, numericCols)

        val table = findViewById<TableLayout>(R.id.tableCorrHeatmap)
        table.removeAllViews()

        val headerRow = TableRow(this).apply { setPadding(0, dp(6), 0, dp(6)) }
        headerRow.addView(makeCorrHeaderCell(""))
        names.forEach { colName -> headerRow.addView(makeCorrHeaderCell(colName)) }
        table.addView(headerRow)

        for (i in names.indices) {
            val tr = TableRow(this).apply { setPadding(0, dp(4), 0, dp(4)) }
            tr.addView(makeCorrHeaderCell(names[i]))
            for (j in names.indices) tr.addView(makeCorrValueCell(matrix[i][j]))
            table.addView(tr)
        }
    }

    private fun computeCorrelationMatrix(
        header: List<String>,
        rows: List<List<String>>,
        numericCols: List<String>
    ): Pair<List<String>, Array<DoubleArray>> {
        val colIndices = numericCols.map { header.indexOf(it) }

        // numeric 값 (결측/비숫자 제거)
        val dataCols: List<List<Double>> = colIndices.map { idx ->
            rows.mapNotNull { row -> row.getOrNull(idx)?.trim()?.toDoubleOrNull() }
        }

        val n = dataCols.firstOrNull()?.size ?: 0
        val k = dataCols.size
        val matrix = Array(k) { DoubleArray(k) }

        val means = DoubleArray(k) { i -> dataCols[i].average() }
        val stds = DoubleArray(k) { i ->
            val m = means[i]
            val vals = dataCols[i]
            val variance = if (n > 1) vals.sumOf { (it - m) * (it - m) } / (n - 1) else 0.0
            sqrt(variance)
        }

        for (i in 0 until k) {
            for (j in 0 until k) {
                if (i == j) {
                    matrix[i][j] = 1.0
                } else {
                    var sum = 0.0
                    val xi = dataCols[i]
                    val xj = dataCols[j]
                    val nn = minOf(xi.size, xj.size)
                    for (t in 0 until nn) sum += (xi[t] - means[i]) * (xj[t] - means[j])
                    val cov = if (nn > 1) sum / (nn - 1) else 0.0
                    matrix[i][j] =
                        if (stds[i] == 0.0 || stds[j] == 0.0) 0.0
                        else cov / (stds[i] * stds[j])
                }
            }
        }

        return numericCols to matrix
    }

    private fun makeCorrHeaderCell(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sp(14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#222222"))
            setBackgroundColor(Color.parseColor("#F2F2F2"))
        }

    private fun makeCorrValueCell(corr: Double): TextView {
        val bg = heatColor(corr)
        return TextView(this).apply {
            text = String.format("%.2f", corr)
            textSize = sp(14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(bg)
            setTextColor(pickTextColorForBackground(bg))
        }
    }

    private fun heatColor(corr: Double): Int {
        val a = abs(corr).coerceIn(0.0, 1.0)
        val intensity = (a * 150).roundToInt().coerceIn(0, 150)
        return if (corr >= 0) Color.rgb(245 - intensity, 245 - intensity, 255)
        else Color.rgb(255, 245 - intensity, 245 - intensity)
    }

    // --------------------------- Navigation ---------------------------

    private fun setupNavigation() {
        val btnColumn = findViewById<Button>(R.id.btnNavColumnStats)
        val btnDataQuality = findViewById<Button>(R.id.btnNavDataQuality)
        val btnHistogram = findViewById<Button>(R.id.btnNavHistogram)
        val btnTarget = findViewById<Button>(R.id.btnNavTargetAnalysis)

        btnColumn.setOnClickListener {
            startActivity(forwardIntent(ColumnStatsActivity::class.java))
            finish()
        }
        btnDataQuality.setOnClickListener {
            // current
        }
        btnHistogram.setOnClickListener {
            startActivity(forwardIntent(HistogramActivity::class.java))
            finish()
        }
        btnTarget.setOnClickListener {
            startActivity(forwardIntent(TargetAnalysisActivity::class.java))
            finish()
        }
    }
}
