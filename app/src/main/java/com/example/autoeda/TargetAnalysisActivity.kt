package com.example.autoeda

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class TargetAnalysisActivity : AppCompatActivity() {

    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>

    private val BLUE = Color.parseColor("#1E63FF")
    private val BLUE_SOFT = Color.parseColor("#EAF1FF")
    private val TEXT_DARK = Color.parseColor("#222222")
    private val HEADER_BG = Color.parseColor("#F2F2F2")
    private val BORDER = Color.parseColor("#E7E7E7")

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
        setContentView(R.layout.activity_target_analysis)

        setupNavigation()
        loadCsv()
        setupToggle()
        runTargetAnalysis()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    private fun Double.format1(): String = String.format("%.1f", this)
    private fun Double.format3(): String = String.format("%.3f", this)

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
            startActivity(forwardIntent(HistogramActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            // current
        }
    }

    private fun setupToggle() {
        val toggle = findViewById<TextView>(R.id.tvToggleConfusion)
        val scroll = findViewById<HorizontalScrollView>(R.id.scrollConfusion)

        scroll.visibility = View.VISIBLE
        toggle.text = "Hide"

        toggle.setOnClickListener {
            val visible = scroll.visibility == View.VISIBLE
            scroll.visibility = if (visible) View.GONE else View.VISIBLE
            toggle.text = if (visible) "Show" else "Hide"
        }
    }

    private fun loadCsv() {
        val intentPath = intent.getStringExtra(DataSource.EXTRA_CSV_FILE_PATH)
        val lines = CsvLoader.loadLines(this, intentPath)

        header = CsvLoader.splitCsvLine(lines.first()).map { it.trim().trim('"') }
        rows = lines.drop(1)
            .filter { it.isNotBlank() }
            .map { line -> CsvLoader.splitCsvLine(line).map { it.trim().trim('"') } }
    }

    private fun runTargetAnalysis() {
        val tvOverview = findViewById<TextView>(R.id.tvTargetOverview)
        val tvInsight = findViewById<TextView>(R.id.tvInsight)

        val k1Label = findViewById<TextView>(R.id.tvKpi1Label)
        val k1Value = findViewById<TextView>(R.id.tvKpi1Value)
        val k2Label = findViewById<TextView>(R.id.tvKpi2Label)
        val k2Value = findViewById<TextView>(R.id.tvKpi2Value)
        val k3Label = findViewById<TextView>(R.id.tvKpi3Label)
        val k3Value = findViewById<TextView>(R.id.tvKpi3Value)
        val k4Label = findViewById<TextView>(R.id.tvKpi4Label)
        val k4Value = findViewById<TextView>(R.id.tvKpi4Value)

        val classDistContainer = findViewById<LinearLayout>(R.id.layoutClassDist)
        val tableConf = findViewById<TableLayout>(R.id.tableConfusion)

        val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
        val targetName = prefs.getString("target_column", null)

        if (targetName.isNullOrBlank() || !header.contains(targetName)) {
            tvOverview.text =
                "No target selected.\n\nGo to Column Statistics → select a target column → tap Set.\n\n" +
                        "• Categorical target → Classification\n• Numeric target → Regression"
            tvInsight.text = "Pick a target first. Then we’ll run a baseline model automatically."

            k1Label.text = "Accuracy"; k1Value.text = "—"
            k2Label.text = "Macro F1";  k2Value.text = "—"
            k3Label.text = "Rows used"; k3Value.text = "—"
            k4Label.text = "Features";  k4Value.text = "—"

            classDistContainer.removeAllViews()
            tableConf.removeAllViews()
            return
        }

        val targetIdx = header.indexOf(targetName)

        val rawValues = rows.mapNotNull { it.getOrNull(targetIdx)?.trim() }.filter { it.isNotEmpty() }
        val numericValues = rawValues.mapNotNull { it.toDoubleOrNull() }
        val isNumeric = numericValues.size >= rawValues.size * 0.5

        if (isNumeric) {
            val y = numericValues
            val yMean = y.average()
            val errors = y.map { it - yMean }
            val mae = errors.map { abs(it) }.average()
            val rmse = sqrt(errors.map { it * it }.average())

            val sst = y.sumOf { (it - yMean) * (it - yMean) }
            val sse = sst
            val r2 = if (sst > 0) 1.0 - (sse / sst) else 0.0

            tvOverview.text = "$targetName (numeric)\n\nBaseline: Mean Predictor (naive)\nMetrics: RMSE / MAE / R²"
            k1Label.text = "RMSE";      k1Value.text = rmse.format3()
            k2Label.text = "MAE";       k2Value.text = mae.format3()
            k3Label.text = "Rows used"; k3Value.text = y.size.toString()
            k4Label.text = "Target mean"; k4Value.text = yMean.format3()

            classDistContainer.removeAllViews()
            tableConf.removeAllViews()

            tvInsight.text =
                "This is a very simple regression baseline.\n" +
                        "Next upgrades: train/test split, Linear Regression, residual plot.\n" +
                        "Current R² = ${r2.format3()} (mean predictor baseline)."
            return
        }

        tvOverview.text =
            "$targetName (categorical)\n\nBaseline: k-NN (k=3) using numeric features\nEvaluation: Leave-one-out (LOO)"

        val numericFeatureIdx = header.indices.filter { idx ->
            if (idx == targetIdx) return@filter false
            val vals = rows.mapNotNull { it.getOrNull(idx)?.trim() }
            val nums = vals.mapNotNull { it.toDoubleOrNull() }
            nums.isNotEmpty() && nums.size >= vals.size * 0.5
        }

        if (numericFeatureIdx.isEmpty()) {
            tvInsight.text = "No numeric features available → cannot run baseline classifier."
            k1Value.text = "—"; k2Value.text = "—"; k3Value.text = "—"; k4Value.text = "—"
            classDistContainer.removeAllViews()
            tableConf.removeAllViews()
            return
        }

        val Xraw = mutableListOf<DoubleArray>()
        val y = mutableListOf<String>()

        for (row in rows) {
            val label = row.getOrNull(targetIdx)?.trim().orEmpty()
            if (label.isEmpty()) continue

            val feats = DoubleArray(numericFeatureIdx.size)
            var ok = true
            numericFeatureIdx.forEachIndexed { j, colIdx ->
                val v = row.getOrNull(colIdx)?.trim()?.toDoubleOrNull()
                if (v == null) ok = false else feats[j] = v
            }
            if (ok) {
                Xraw.add(feats)
                y.add(label)
            }
        }

        val n = Xraw.size
        if (n < 2) {
            tvInsight.text = "Not enough valid rows to evaluate."
            return
        }

        val X = zScoreScale(Xraw)

        val classes = y.distinct().sorted()
        val classToIdx = classes.withIndex().associate { it.value to it.index }
        val kClass = classes.size
        val conf = Array(kClass) { IntArray(kClass) }

        val kNeighbors = 3

        for (i in 0 until n) {
            val xi = X[i]
            val distances = mutableListOf<Pair<Int, Double>>()

            for (j in 0 until n) {
                if (j == i) continue
                val xj = X[j]
                var dist = 0.0
                for (d in xi.indices) {
                    val diff = xi[d] - xj[d]
                    dist += diff * diff
                }
                distances.add(j to dist)
            }

            val neighbors = distances.sortedBy { it.second }
                .take(kNeighbors)
                .map { (idx, _) -> y[idx] }

            val predLabel = neighbors.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key

            val trueIdx = classToIdx[y[i]]!!
            val predIdx = classToIdx[predLabel]!!
            conf[trueIdx][predIdx] += 1
        }

        var correct = 0
        for (c in 0 until kClass) correct += conf[c][c]
        val accuracy = correct.toDouble() / n.toDouble()

        var f1Sum = 0.0
        for (c in 0 until kClass) {
            val tp = conf[c][c].toDouble()
            val fp = (0 until kClass).sumOf { r -> if (r == c) 0 else conf[r][c] }.toDouble()
            val fn = (0 until kClass).sumOf { r -> if (r == c) 0 else conf[c][r] }.toDouble()

            val precision = if (tp + fp > 0) tp / (tp + fp) else 0.0
            val recall = if (tp + fn > 0) tp / (tp + fn) else 0.0
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
            f1Sum += f1
        }
        val macroF1 = f1Sum / kClass.toDouble()

        k1Label.text = "Accuracy"
        k2Label.text = "Macro F1"
        k3Label.text = "Rows used"
        k4Label.text = "Features"

        k1Value.text = "${(accuracy * 100).format1()}%"
        k2Value.text = macroF1.format3()
        k3Value.text = n.toString()
        k4Value.text = numericFeatureIdx.size.toString()

        renderClassDistribution(classDistContainer, classes, y)
        renderConfusionTable(tableConf, classes, conf)

        tvInsight.text =
            "Baseline ready (k-NN, k=3, LOO).\n" +
                    "• If Accuracy is low: try different k, add scaling, or use train/test split.\n" +
                    "• Next upgrades: per-class precision/recall, ROC/PR (binary), cross-validation."
    }

    private fun zScoreScale(X: List<DoubleArray>): List<DoubleArray> {
        if (X.isEmpty()) return X
        val d = X[0].size
        val n = X.size

        val means = DoubleArray(d)
        val stds = DoubleArray(d)

        for (j in 0 until d) means[j] = X.sumOf { it[j] } / n.toDouble()
        for (j in 0 until d) {
            val m = means[j]
            val varr = X.sumOf { (it[j] - m) * (it[j] - m) } / max(1, n - 1).toDouble()
            stds[j] = sqrt(varr).coerceAtLeast(1e-9)
        }

        return X.map { row -> DoubleArray(d) { j -> (row[j] - means[j]) / stds[j] } }
    }

    private fun renderClassDistribution(container: LinearLayout, classes: List<String>, y: List<String>) {
        container.removeAllViews()
        val total = y.size.toDouble().coerceAtLeast(1.0)
        val counts = y.groupingBy { it }.eachCount()

        classes.forEach { cls ->
            val cnt = counts[cls] ?: 0
            val ratio = cnt / total

            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvName = TextView(this).apply {
                text = cls
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_DARK)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvRight = TextView(this).apply {
                text = "$cnt · ${(ratio * 100).format1()}%"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_DARK)
            }

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
                    topMargin = dp(12)
                }
                setBackgroundColor(BORDER)
            }

            top.addView(tvName)
            top.addView(tvRight)
            block.addView(top)
            block.addView(bar)
            block.addView(divider)
            container.addView(block)
        }
    }

    private fun renderConfusionTable(table: TableLayout, classes: List<String>, conf: Array<IntArray>) {
        table.removeAllViews()

        val headerRow = TableRow(this)
        headerRow.addView(makeCell("", true))
        classes.forEach { headerRow.addView(makeCell(it, true)) }
        table.addView(headerRow)

        for (i in classes.indices) {
            val tr = TableRow(this)
            tr.addView(makeCell(classes[i], true))
            for (j in classes.indices) tr.addView(makeCell(conf[i][j].toString(), false))
            table.addView(tr)
        }
    }

    private fun makeCell(text: String, header: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(TEXT_DARK)
            if (header) setBackgroundColor(HEADER_BG)
        }
}
