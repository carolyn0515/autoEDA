package com.example.autoeda

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ColumnStatsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CSV_FILE_PATH = "extra_csv_file_path"
    }

    enum class ColKind { NUMERIC, CATEGORICAL, TEXT }

    data class ColumnStats(
        val name: String,
        val kind: ColKind,
        val mean: Double?,
        val std: Double?,
        val min: Double?,
        val max: Double?,
        val missingRatio: Double
    ) {
        val isNumeric: Boolean get() = kind == ColKind.NUMERIC
    }

    private fun forwardIntent(clazz: Class<*>): Intent {
        val intent = Intent(this, clazz)
        val path = getCurrentCsvPath()
        if (!path.isNullOrBlank()) intent.putExtra(DataSource.EXTRA_CSV_FILE_PATH, path)
        return intent
    }

    private fun getCurrentCsvPath(): String? {
        val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
        // intent가 있으면 그걸 우선
        return intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
            ?: prefs.getString(DataSource.KEY_CSV_FILE_PATH, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_column_stats)

        setupNavigation()

        // CSV 로드 (Intent or Prefs or Raw)
        val intentPath = intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
        val lines = CsvLoader.loadLines(this, intentPath)
        if (lines.isEmpty()) return

        val header = CsvLoader.splitCsvLine(lines.first()).map { it.trim().trim('"') }
        val rows = lines.drop(1)
            .filter { it.isNotBlank() }
            .map { line -> CsvLoader.splitCsvLine(line).map { it.trim().trim('"') } }

        val statsList = computeStats(header, rows)

        // 테이블 채우기
        val table = findViewById<TableLayout>(R.id.tableGeneralStats)
        while (table.childCount > 1) table.removeViewAt(1)

        statsList.filter { it.kind == ColKind.NUMERIC }.forEach { stat ->
            table.addView(makeRow(stat))
        }

        // Type Summary (3카드)
        val numericCount = statsList.count { it.kind == ColKind.NUMERIC }
        val categoricalCount = statsList.count { it.kind == ColKind.CATEGORICAL }
        val textCount = statsList.count { it.kind == ColKind.TEXT }

        findViewById<TextView>(R.id.tvNumericCount).text = numericCount.toString()
        findViewById<TextView>(R.id.tvCategoricalCount).text = categoricalCount.toString()
        findViewById<TextView>(R.id.tvTextCount).text = textCount.toString()

        setupTargetSetButton(statsList)
    }

    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNavColumnStats).setOnClickListener {
            // current
        }
        findViewById<Button>(R.id.btnNavDataQuality).setOnClickListener {
            startActivity(forwardIntent(DataQualityActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavHistogram).setOnClickListener {
            startActivity(forwardIntent(HistogramActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            startActivity(forwardIntent(TargetAnalysisActivity::class.java))
            finish()
        }
    }

    private fun setupTargetSetButton(statsList: List<ColumnStats>) {
        val actvTarget = findViewById<AutoCompleteTextView>(R.id.actvTargetColumn)
        val btnSetTarget = findViewById<Button>(R.id.btnSetTarget)

        val targetCandidates = statsList.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, targetCandidates)
        actvTarget.setAdapter(adapter)
        actvTarget.threshold = 0

        actvTarget.setOnClickListener { actvTarget.showDropDown() }
        actvTarget.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvTarget.showDropDown() }

        btnSetTarget.setOnClickListener {
            val selected = actvTarget.text?.toString()?.trim()
            if (selected.isNullOrEmpty()) {
                Toast.makeText(this, "타겟 컬럼을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stat = statsList.firstOrNull { it.name == selected }
            val targetType = if (stat?.kind == ColKind.NUMERIC) "numeric" else "categorical"

            val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putString("target_column", selected)
                .putString("target_type", targetType)
                .apply()

            val typeLabel = if (targetType == "numeric") "Numeric" else "Categorical"
            Toast.makeText(this, "Target column: $selected ($typeLabel)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun computeStats(header: List<String>, rows: List<List<String>>): List<ColumnStats> {
        val nRows = rows.size.toDouble().coerceAtLeast(1.0)

        return header.mapIndexed { colIdx, name ->
            val values = rows.map { row -> row.getOrNull(colIdx)?.trim().orEmpty() }

            val missingCount = values.count { it.isEmpty() }
            val missingRatio = missingCount / nRows

            val nonMissing = values.filter { it.isNotEmpty() }

            val numericValues = nonMissing.mapNotNull { it.toDoubleOrNull() }
            val isNumeric = numericValues.isNotEmpty() && numericValues.size >= nRows * 0.5

            if (isNumeric) {
                val mean = numericValues.average()
                val min = numericValues.minOrNull()
                val max = numericValues.maxOrNull()
                val std = if (numericValues.size > 1) {
                    kotlin.math.sqrt(
                        numericValues.sumOf { (it - mean) * (it - mean) } /
                                (numericValues.size - 1)
                    )
                } else null

                ColumnStats(name, ColKind.NUMERIC, mean, std, min, max, missingRatio)
            } else {
                val unique = nonMissing.toSet().size
                val catThreshold = maxOf(20, (nRows * 0.05).toInt())
                val kind = if (unique <= catThreshold) ColKind.CATEGORICAL else ColKind.TEXT
                ColumnStats(name, kind, null, null, null, null, missingRatio)
            }
        }
    }

    private fun makeRow(stat: ColumnStats): TableRow {
        fun makeCell(text: String, bold: Boolean = false, alignStart: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                textSize = 15f
                setPadding(10, 10, 10, 10)
                gravity = if (alignStart) Gravity.START else Gravity.CENTER
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

        return TableRow(this).apply {
            addView(makeCell(stat.name, bold = true, alignStart = true))
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
