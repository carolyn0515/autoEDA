package com.example.autoeda

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.content.Intent

class UploadActivity : AppCompatActivity() {

    private lateinit var tvFileName: TextView
    private lateinit var tvRowsValue: TextView
    private lateinit var tvColumnsValue: TextView
    private lateinit var tvFileSizeValue: TextView
    private lateinit var tvNumericValue: TextView
    private lateinit var tvCategoricalValue: TextView
    private lateinit var btnStartEda: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        // 1) 뷰 연결
        tvFileName = findViewById(R.id.etFileName)
        tvRowsValue = findViewById(R.id.tvRowsValue)
        tvColumnsValue = findViewById(R.id.tvColumnsValue)
        tvFileSizeValue = findViewById(R.id.tvFileSizeValue)
        tvNumericValue = findViewById(R.id.tvNumericValue)
        tvCategoricalValue = findViewById(R.id.tvCategoricalValue)
        btnStartEda = findViewById(R.id.btnStartEda)

        // "View Detailed Analysis →" 텍스트뷰
        val tvViewDetail = findViewById<TextView>(R.id.tvViewDetail)

        // 2) iris.csv가 기본으로 선택되어 있는 것처럼 표시
        tvFileName.text = "iris.csv"

        // 3) Start EDA 버튼 클릭 시 iris.csv 분석해서 Overview 채우기
        btnStartEda.setOnClickListener {
            val summary = loadIrisSummary()
            tvRowsValue.text = summary.nRows.toString()
            tvColumnsValue.text = summary.nCols.toString()
            tvFileSizeValue.text = formatFileSize(summary.fileSizeBytes)
            tvNumericValue.text = summary.nNumeric.toString()
            tvCategoricalValue.text = summary.nCategorical.toString()
        }

        // 4) View Detailed Analysis → 클릭하면 ColumnStatsActivity로 이동
        tvViewDetail.setOnClickListener {
            val intent = Intent(this, ColumnStatsActivity::class.java)
            startActivity(intent)
        }
    }

    // ------------------------------------------
    // iris.csv 요약 정보 계산
    // ------------------------------------------

    data class IrisSummary(
        val nRows: Int,
        val nCols: Int,
        val nNumeric: Int,
        val nCategorical: Int,
        val fileSizeBytes: Long
    )

    private fun loadIrisSummary(): IrisSummary {
        // 파일 크기 (bytes)
        val sizeInput = resources.openRawResource(R.raw.iris)
        val bytes = sizeInput.readBytes()
        sizeInput.close()
        val fileSize = bytes.size.toLong()

        // CSV 파싱용 InputStream 다시 열기
        val input = resources.openRawResource(R.raw.iris)
        val lines = input.bufferedReader().readLines()
        input.close()

        if (lines.isEmpty()) {
            return IrisSummary(
                nRows = 0,
                nCols = 0,
                nNumeric = 0,
                nCategorical = 0,
                fileSizeBytes = fileSize
            )
        }

        val header = lines.first().split(",")
        val dataLines = lines.drop(1).filter { it.isNotBlank() }
        val nRows = dataLines.size
        val nCols = header.size

        var numericCount = 0
        var categoricalCount = 0

        // 각 컬럼이 numeric인지 categorical인지 간단하게 판별
        for (colIdx in 0 until nCols) {
            val colValues = dataLines.map { line ->
                val parts = line.split(",")
                parts.getOrNull(colIdx)?.trim().orEmpty()
            }
            val numericValues = colValues.mapNotNull { it.toDoubleOrNull() }
            val isNumeric = numericValues.isNotEmpty() &&
                    numericValues.size >= nRows * 0.5   // 절반 이상이 숫자면 numeric으로 간주

            if (isNumeric) numericCount++ else categoricalCount++
        }

        return IrisSummary(
            nRows = nRows,
            nCols = nCols,
            nNumeric = numericCount,
            nCategorical = categoricalCount,
            fileSizeBytes = fileSize
        )
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        return String.format("%.1f KB", kb)
    }
}
