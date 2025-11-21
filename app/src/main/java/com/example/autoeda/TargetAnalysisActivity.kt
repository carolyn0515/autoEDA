package com.example.autoeda

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout

class TargetAnalysisActivity : AppCompatActivity() {

    private lateinit var header: List<String>
    private lateinit var rows: List<List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target_analysis)

        setupNavigation()
        loadCsv()
        runTargetAnalysis()
    }

    // -------------------- 1) 네비게이션 --------------------
    private fun setupNavigation() {
        findViewById<Button>(R.id.btnNavColumnStats).setOnClickListener {
            startActivity(Intent(this, ColumnStatsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNavDataQuality).setOnClickListener {
            startActivity(Intent(this, DataQualityActivity::class.java))
        }
        findViewById<Button>(R.id.btnNavHistogram).setOnClickListener {
            startActivity(Intent(this, HistogramActivity::class.java))
        }
        findViewById<Button>(R.id.btnNavTargetAnalysis).setOnClickListener {
            // 현재 페이지
        }
    }

    // -------------------- 2) CSV 로드 --------------------
    private fun loadCsv() {
        val inputStream = resources.openRawResource(R.raw.iris)
        val lines = inputStream.bufferedReader().use { it.readLines() }

        header = lines.first().split(",")
        rows = lines.drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
    }

    // -------------------- 3) 타깃 기반 분석 실행 --------------------
    private fun runTargetAnalysis() {
        val tvOverview = findViewById<TextView>(R.id.tvTargetOverview)
        val tvInsight = findViewById<TextView>(R.id.tvInsight)

        // 1) 타깃 이름 로드
        val prefs = getSharedPreferences("autoeda_prefs", MODE_PRIVATE)
        val targetName = prefs.getString("target_column", null)

        if (targetName.isNullOrBlank() || !header.contains(targetName)) {
            tvOverview.text =
                "Target analysis overview\n\n" +
                        "아직 타깃 컬럼이 설정되지 않았거나,\n" +
                        "현재 데이터셋에서 해당 컬럼을 찾을 수 없습니다.\n\n" +
                        "Column Statistics 화면에서 타깃 컬럼을 선택 후 Set 버튼을 눌러주세요."
            tvInsight.text = ""
            return
        }

        val targetIdx = header.indexOf(targetName)

        // 2) 타깃 타입 판별 (numeric / categorical)
        val rawValues = rows.mapNotNull { it.getOrNull(targetIdx)?.trim() }
            .filter { it.isNotEmpty() }

        val numericValues = rawValues.mapNotNull { it.toDoubleOrNull() }
        val isNumeric = numericValues.size >= rawValues.size * 0.5

        if (isNumeric) {
            runNumericTargetOverview(targetName, tvOverview, tvInsight)
        } else {
            runCategoricalTargetAnalysis(targetName, targetIdx, tvOverview, tvInsight)
        }
    }

    // -------------------- 4) Numeric 타깃 개요 (stub) --------------------
    private fun runNumericTargetOverview(
        targetName: String,
        tvOverview: TextView,
        tvInsight: TextView
    ) {
        tvOverview.text =
            "Target analysis overview\n\n" +
                    "현재 선택된 타깃 컬럼은 '$targetName' (numeric, 연속형) 입니다.\n\n" +
                    "이 경우에는 회귀(regression) 문제로 볼 수 있으며,\n" +
                    "기본적으로는 RMSE, MAE, R² 등의 지표를 이용해\n" +
                    "예측 성능을 평가할 수 있습니다.\n\n" +
                    "지금 버전에서는 numeric 타깃에 대한\n" +
                    "기본 회귀 모델/시각화는 추후 추가될 예정입니다."

        tvInsight.text = "Numeric 타깃에 대한 자세한 회귀 분석 기능은 추후 업데이트 예정입니다."
    }

    // -------------------- 5) Categorical 타깃 + k-NN confusion matrix --------------------
    private fun runCategoricalTargetAnalysis(
        targetName: String,
        targetIdx: Int,
        tvOverview: TextView,
        tvInsight: TextView
    ) {
        tvOverview.text =
            "Target analysis overview\n\n" +
                    "현재 선택된 타깃 컬럼은 '$targetName' (categorical, 범주형) 입니다.\n\n" +
                    "이 타깃은 분류(classification) 문제로 볼 수 있으며,\n" +
                    "여기서는 모든 numeric 피처를 이용한 간단한 k-NN (k=3) 모델로\n" +
                    "기본 예측 성능을 평가합니다.\n\n" +
                    "아래 인사이트 카드에는 클래스 분포와 함께\n" +
                    "k-NN 기반 confusion matrix, 정확도(accuracy),\n" +
                    "macro F1 점수가 표시됩니다."

        // 1) 사용할 numeric 피처 인덱스 선택 (타깃 제외)
        val numericFeatureIdx = header.indices.filter { idx ->
            if (idx == targetIdx) return@filter false
            val vals = rows.mapNotNull { it.getOrNull(idx)?.trim() }
            val nums = vals.mapNotNull { it.toDoubleOrNull() }
            nums.size >= vals.size * 0.5 && nums.isNotEmpty()
        }

        if (numericFeatureIdx.isEmpty()) {
            tvInsight.text =
                "사용 가능한 numeric 피처가 없어 간단한 분류 모델을 실행할 수 없습니다.\n" +
                        "다른 데이터셋이나 타깃 컬럼을 선택해 보세요."
            return
        }

        // 2) 피처 행렬 X, 레이블 y 구성
        val X = mutableListOf<DoubleArray>()
        val y = mutableListOf<String>()

        for (row in rows) {
            if (row.size <= targetIdx) continue
            val label = row[targetIdx].trim()
            if (label.isEmpty()) continue

            val feats = DoubleArray(numericFeatureIdx.size)
            var ok = true
            numericFeatureIdx.forEachIndexed { j, colIdx ->
                val v = row.getOrNull(colIdx)?.trim()?.toDoubleOrNull()
                if (v == null) {
                    ok = false
                    return@forEachIndexed
                } else {
                    feats[j] = v
                }
            }
            if (ok) {
                X.add(feats)
                y.add(label)
            }
        }

        val n = X.size
        if (n < 2) {
            tvInsight.text = "유효한 행(row)이 너무 적어 분류 모델을 학습할 수 없습니다."
            return
        }

        // 3) 클래스 목록 및 confusion matrix 준비
        val classes = y.distinct().sorted()
        val classToIdx = classes.withIndex().associate { it.value to it.index }
        val k = classes.size
        val conf = Array(k) { IntArray(k) }

        // 4) 간단한 leave-one-out k-NN (k=3)
        val kNeighbors = 3

        for (i in 0 until n) {
            val xi = X[i]

            // 다른 샘플들과 거리 계산
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

            // 거리 순 정렬 후 k개 이웃
            val neighbors = distances.sortedBy { it.second }
                .take(kNeighbors)
                .map { (idx, _) -> y[idx] }

            // 다수결로 예측
            val predLabel = neighbors
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }!!
                .key

            val trueIdx = classToIdx[y[i]]!!
            val predIdx = classToIdx[predLabel]!!
            conf[trueIdx][predIdx] += 1
        }

        // 5) 성능 지표 계산 (accuracy, macro F1)
        var correct = 0
        for (c in 0 until k) correct += conf[c][c]
        val accuracy = correct.toDouble() / n.toDouble()

        var f1Sum = 0.0
        for (c in 0 until k) {
            val tp = conf[c][c].toDouble()
            val fp = (0 until k).sumOf { r ->
                if (r == c) 0 else conf[r][c]
            }.toDouble()
            val fn = (0 until k).sumOf { r ->
                if (r == c) 0 else conf[c][r]
            }.toDouble()

            val precision = if (tp + fp > 0) tp / (tp + fp) else 0.0
            val recall = if (tp + fn > 0) tp / (tp + fn) else 0.0
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
            f1Sum += f1
        }
        val macroF1 = f1Sum / k.toDouble()

        // 6) 클래스 분포 계산
        val classCounts = y.groupingBy { it }.eachCount()
        val distText = classes.joinToString("\n") { cls ->
            val cnt = classCounts[cls] ?: 0
            val ratio = cnt.toDouble() / n.toDouble() * 100.0
            "• $cls: $cnt (${ratio.format1()}%)"
        }

        // 7) confusion matrix 텍스트로 정리
        val sb = StringBuilder()
        sb.append("🔍 Categorical 타깃 기본 분류 분석\n\n")
        sb.append("• 타깃: $targetName\n")
        sb.append("• 클래스 개수: ${classes.size}\n")
        sb.append("• 사용 피처 개수: ${numericFeatureIdx.size}\n\n")

        sb.append("클래스 분포:\n$distText\n\n")

        sb.append("Confusion matrix (행 = 실제, 열 = 예측):\n")

        // 헤더
        sb.append(String.format("%10s", ""))
        for (cls in classes) {
            sb.append(String.format("%10s", cls))
        }
        sb.append("\n")

        for ((iCls, cls) in classes.withIndex()) {
            sb.append(String.format("%10s", cls))
            for (j in 0 until k) {
                sb.append(String.format("%10d", conf[iCls][j]))
            }
            sb.append("\n")
        }

        sb.append("\n")
        sb.append("Accuracy: ${(accuracy * 100).format1()}%\n")
        sb.append("Macro F1: ${macroF1.format3()}\n\n")
        sb.append("※ 이 결과는 매우 단순한 k-NN(leave-one-out) 기반 기준선(baseline)입니다.\n")
        sb.append("   실제 분석에서는 더 복잡한 모델과 교차검증을 함께 사용하는 것이 좋습니다.")

        tvInsight.text = sb.toString()
    }

    // -------------------- 6) Double 포맷 helper --------------------
    private fun Double.format1(): String = String.format("%.1f", this)
    private fun Double.format3(): String = String.format("%.3f", this)
}
