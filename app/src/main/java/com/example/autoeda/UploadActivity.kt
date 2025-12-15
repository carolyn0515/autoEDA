package com.example.autoeda

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

class UploadActivity : AppCompatActivity() {

    // ---------- Overview UI ----------
    private lateinit var tvDataBadge: TextView
    private lateinit var tvFileName: TextView
    private lateinit var tvRowsValue: TextView
    private lateinit var tvColumnsValue: TextView
    private lateinit var tvFileSizeValue: TextView
    private lateinit var tvNumericValue: TextView
    private lateinit var tvCategoricalValue: TextView
    private lateinit var btnStartEda: Button
    private lateinit var tvViewDetail: TextView

    // ---------- Mode ----------
    private lateinit var rgDataSource: RadioGroup
    private lateinit var rbSourceFile: RadioButton
    private lateinit var rbSourceApi: RadioButton
    private lateinit var layoutFileField: View
    private lateinit var layoutApiField: View

    // ---------- File card ----------
    private lateinit var btnClearFile: ImageButton

    // ---------- API UI ----------
    private lateinit var btnTestApi: Button
    private lateinit var btnLoadApi: Button
    private lateinit var spApiProvider: Spinner
    private lateinit var etServiceKey: EditText
    private lateinit var etLawdCd: EditText
    private lateinit var etDealYmd: EditText
    private lateinit var etNumOfRows: EditText
    private lateinit var etPageNo: EditText

    // ---------- state ----------
    private var isApiMode: Boolean = false
    private var currentCsvFile: File? = null

    // ---------- file picker (CSV only) ----------
    private val pickCsvLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch { onUserPickedCsv(uri) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        bindViews()
        setupApiProviderSpinner()
        setupModeToggle()
        setupFilePicker()
        setupClearFile()

        // 기본: File 모드 + 미선택 상태
        setSourceFileNotSelected()

        // Start EDA: 현재 선택된 CSV로 Overview 채우기
        btnStartEda.setOnClickListener {
            lifecycleScope.launch {
                val summary = withContext(Dispatchers.IO) {
                    val f = resolveCurrentCsvFile()
                    if (f != null && f.exists()) loadCsvSummaryFromFile(f) else null
                }

                if (summary == null) {
                    toast("CSV 파일을 먼저 선택하거나(API면 Load) 해줘!")
                    return@launch
                }

                applySummary(summary)
                toast("✅ Overview 업데이트 완료")
            }
        }

        // View Detailed Analysis →
        tvViewDetail.setOnClickListener {
            val f = resolveCurrentCsvFile()
            if (f == null || !f.exists()) {
                toast("먼저 CSV 파일을 선택해줘!")
                return@setOnClickListener
            }

            val intent = Intent(this, ColumnStatsActivity::class.java)
            intent.putExtra(DataSource.EXTRA_CSV_FILE_PATH, f.absolutePath)
            startActivity(intent)
        }

        // API: Test
        btnTestApi.setOnClickListener {
            lifecycleScope.launch { testApiCall() }
        }

        // API: Load & Analyze
        btnLoadApi.setOnClickListener {
            lifecycleScope.launch { loadApiAndAnalyze() }
        }
    }

    // ------------------------------------------------------------
    // bind / setup
    // ------------------------------------------------------------
    private fun bindViews() {
        // badge
        tvDataBadge = findViewById(R.id.tvDataBadge)

        // overview
        tvFileName = findViewById(R.id.etFileName)
        tvRowsValue = findViewById(R.id.tvRowsValue)
        tvColumnsValue = findViewById(R.id.tvColumnsValue)
        tvFileSizeValue = findViewById(R.id.tvFileSizeValue)
        tvNumericValue = findViewById(R.id.tvNumericValue)
        tvCategoricalValue = findViewById(R.id.tvCategoricalValue)
        btnStartEda = findViewById(R.id.btnStartEda)
        tvViewDetail = findViewById(R.id.tvViewDetail)

        // mode
        rgDataSource = findViewById(R.id.rgDataSource)
        rbSourceFile = findViewById(R.id.rbSourceFile)
        rbSourceApi = findViewById(R.id.rbSourceApi)
        layoutFileField = findViewById(R.id.layoutFileField)
        layoutApiField = findViewById(R.id.layoutApiField)

        // file clear
        btnClearFile = findViewById(R.id.btnClearFile)

        // api
        btnTestApi = findViewById(R.id.btnTestApi)
        btnLoadApi = findViewById(R.id.btnLoadApi)
        spApiProvider = findViewById(R.id.spApiProvider)
        etServiceKey = findViewById(R.id.etServiceKey)
        etLawdCd = findViewById(R.id.etLawdCd)
        etDealYmd = findViewById(R.id.etDealYmd)
        etNumOfRows = findViewById(R.id.etNumOfRows)
        etPageNo = findViewById(R.id.etPageNo)
    }

    private fun setupModeToggle() {
        // default: file
        isApiMode = false
        layoutFileField.visibility = View.VISIBLE
        layoutApiField.visibility = View.GONE
        rbSourceFile.isChecked = true

        rgDataSource.setOnCheckedChangeListener { _, checkedId ->
            isApiMode = (checkedId == R.id.rbSourceApi)
            if (isApiMode) {
                layoutFileField.visibility = View.GONE
                layoutApiField.visibility = View.VISIBLE
                setSourceApiNotLoaded()
            } else {
                layoutFileField.visibility = View.VISIBLE
                layoutApiField.visibility = View.GONE
                setSourceFileNotSelected()
            }
        }
    }

    private fun setupFilePicker() {
        // 파일 업로드 카드 클릭 → CSV 선택
        layoutFileField.setOnClickListener {
            // CSV만 목표: text/csv 우선, 일부 폰은 text/plain로 csv를 주기도 함
            pickCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }
    }

    private fun setupClearFile() {
        btnClearFile.setOnClickListener {
            // 파일 모드일 때만 의미 있음
            if (isApiMode) {
                toast("API 모드에서는 Clear가 필요 없어요.")
                return@setOnClickListener
            }

            currentCsvFile = null
            saveCsvPath("") // 비워둠
            setSourceFileNotSelected()
            toast("파일 선택 해제됨")
        }
    }

    private fun setupApiProviderSpinner() {
        val items = listOf("data.go.kr - Apt Trade (XML)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spApiProvider.adapter = adapter

        // 데모 기본값
        etLawdCd.setText("11110")
        etDealYmd.setText("202512")
        etNumOfRows.setText("200")
        etPageNo.setText("1")
    }

    // ------------------------------------------------------------
    // source badge / UI state
    // ------------------------------------------------------------
    private fun setBadge(text: String) {
        tvDataBadge.text = text
    }

    private fun setSourceFileNotSelected() {
        currentCsvFile = null
        tvFileName.text = "Choose a CSV file"
        setBadge("FILE (Not selected)")
        clearOverview()
    }

    private fun setSourceFileSelected(file: File) {
        currentCsvFile = file
        tvFileName.text = file.name
        setBadge("FILE (Selected)")
        clearOverview()
    }

    private fun setSourceApiNotLoaded() {
        currentCsvFile = null
        tvFileName.text = "API data (not loaded yet)"
        setBadge("API (Not loaded)")
        clearOverview()
    }

    private fun setSourceApiLoaded(displayName: String, file: File) {
        currentCsvFile = file
        tvFileName.text = displayName
        setBadge("API (Loaded)")
        clearOverview()
    }

    // ------------------------------------------------------------
    // CSV pick flow
    // ------------------------------------------------------------
    private suspend fun onUserPickedCsv(uri: Uri) {
        // (선택) 다음 실행에도 접근 유지 시도
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 공급자에 따라 실패 가능(그래도 복사하면 OK)
        }

        // cache로 복사
        val copied = withContext(Dispatchers.IO) { copyUriToCache(uri) }
        if (copied == null) {
            toast("파일을 불러오지 못했어.")
            return
        }

        // 확장자 체크
        val lower = copied.name.lowercase()
        if (!lower.endsWith(".csv")) {
            toast("CSV 파일만 지원해! (.csv)")
            copied.delete()
            return
        }

        // 저장 + UI 반영
        saveCsvPath(copied.absolutePath)
        setSourceFileSelected(copied)
        toast("✅ CSV 선택 완료: ${copied.name}")
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val name = queryFileName(uri) ?: "uploaded_${System.currentTimeMillis()}.csv"
            val outFile = File(cacheDir, name)

            contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            outFile
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) return it.getString(idx)
        }
        return null
    }

    // ------------------------------------------------------------
    // resolve CSV (current > prefs)
    // ------------------------------------------------------------
    private fun resolveCurrentCsvFile(): File? {
        // 1) 현재 선택된 파일
        val cur = currentCsvFile
        if (cur != null && cur.exists()) return cur

        // 2) prefs에 저장된 최근 csv
        val path = getSavedCsvPath()
        if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.exists()) return f
        }
        return null
    }

    private fun saveCsvPath(filePath: String) {
        val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(DataSource.KEY_CSV_FILE_PATH, filePath).apply()
    }

    private fun getSavedCsvPath(): String? {
        val prefs = getSharedPreferences(DataSource.PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(DataSource.KEY_CSV_FILE_PATH, null)
    }

    // ------------------------------------------------------------
    // API flow (기존 유지 + CSV 저장)
    // ------------------------------------------------------------
    private suspend fun testApiCall() {
        val ok = withContext(Dispatchers.IO) {
            try {
                val key = etServiceKey.text.toString().trim()
                val lawd = etLawdCd.text.toString().trim()
                val ymd = etDealYmd.text.toString().trim()
                val numOfRows = etNumOfRows.text.toString().trim().toIntOrNull() ?: 100
                val pageNo = etPageNo.text.toString().trim().toIntOrNull() ?: 1
                if (key.isBlank() || lawd.isBlank() || ymd.isBlank()) return@withContext false

                val xml = fetchAptTradeXml(key, lawd, ymd, numOfRows, pageNo)
                val rows = parseAptTrades(xml)
                rows.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
        toast(if (ok) "✅ API 연결 성공" else "❌ API 테스트 실패 (키/파라미터 확인)")
    }

    private suspend fun loadApiAndAnalyze() {
        val key = etServiceKey.text.toString().trim()
        val lawd = etLawdCd.text.toString().trim()
        val ymd = etDealYmd.text.toString().trim()
        val numOfRows = etNumOfRows.text.toString().trim().toIntOrNull() ?: 100
        val pageNo = etPageNo.text.toString().trim().toIntOrNull() ?: 1

        if (key.isBlank()) { toast("Service Key를 입력해줘!"); return }
        if (lawd.isBlank()) { toast("LAWD_CD를 입력해줘!"); return }
        if (ymd.isBlank()) { toast("DEAL_YMD(YYYYMM)를 입력해줘!"); return }

        btnLoadApi.isEnabled = false
        btnTestApi.isEnabled = false
        toast("API 호출 중...")

        val result = withContext(Dispatchers.IO) {
            try {
                val xml = fetchAptTradeXml(key, lawd, ymd, numOfRows, pageNo)
                val items = parseAptTrades(xml)
                if (items.isEmpty()) return@withContext null

                val csvFile = writeAptTradesToCsv(items, lawd, ymd)
                val summary = loadCsvSummaryFromFile(csvFile)
                Pair(csvFile, summary)
            } catch (_: Exception) {
                null
            }
        }

        btnLoadApi.isEnabled = true
        btnTestApi.isEnabled = true

        if (result == null) {
            toast("불러오기 실패. (키/파라미터/트래픽 제한 확인)")
            return
        }

        val (csvFile, summary) = result
        saveCsvPath(csvFile.absolutePath)
        setSourceApiLoaded("API: AptTrade_${lawd}_${ymd}.csv", csvFile)
        applySummary(summary)
        toast("✅ API 불러오기 완료")
    }

    private fun fetchAptTradeXml(
        serviceKey: String,
        lawdCd: String,
        dealYmd: String,
        numOfRows: Int,
        pageNo: Int
    ): String {
        val base = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"
        val urlStr = "$base?serviceKey=$serviceKey&LAWD_CD=$lawdCd&DEAL_YMD=$dealYmd&numOfRows=$numOfRows&pageNo=$pageNo"

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val xml = stream.bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("HTTP $code\n$xml")
        return xml
    }

    data class AptTradeRow(
        val aptNm: String?,
        val dealAmount: String?,
        val buildYear: String?,
        val dealYear: String?,
        val dealMonth: String?,
        val dealDay: String?,
        val areaForExclusiveUse: String?,
        val floor: String?,
        val jibun: String?,
        val umdNm: String?
    )

    private fun parseAptTrades(xml: String): List<AptTradeRow> {
        val out = mutableListOf<AptTradeRow>()

        var currentTag: String? = null
        var inItem = false

        var aptNm: String? = null
        var dealAmount: String? = null
        var buildYear: String? = null
        var dealYear: String? = null
        var dealMonth: String? = null
        var dealDay: String? = null
        var area: String? = null
        var floor: String? = null
        var jibun: String? = null
        var umdNm: String? = null

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        aptNm = null; dealAmount = null; buildYear = null
                        dealYear = null; dealMonth = null; dealDay = null
                        area = null; floor = null; jibun = null; umdNm = null
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim()
                        when (currentTag) {
                            "aptNm" -> aptNm = text
                            "dealAmount" -> dealAmount = text
                            "buildYear" -> buildYear = text
                            "dealYear" -> dealYear = text
                            "dealMonth" -> dealMonth = text
                            "dealDay" -> dealDay = text
                            "areaForExclusiveUse" -> area = text
                            "floor" -> floor = text
                            "jibun" -> jibun = text
                            "umdNm" -> umdNm = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        out.add(
                            AptTradeRow(
                                aptNm, dealAmount, buildYear,
                                dealYear, dealMonth, dealDay,
                                area, floor, jibun, umdNm
                            )
                        )
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return out
    }

    private fun writeAptTradesToCsv(rows: List<AptTradeRow>, lawdCd: String, dealYmd: String): File {
        val file = File(cacheDir, "AptTrade_${lawdCd}_${dealYmd}.csv")
        file.bufferedWriter().use { w ->
            w.write("aptNm,dealAmount,buildYear,dealYear,dealMonth,dealDay,areaForExclusiveUse,floor,jibun,umdNm\n")
            rows.forEach { r ->
                fun esc(s: String?): String {
                    val v = (s ?: "").replace("\"", "\"\"")
                    return "\"$v\""
                }
                w.write(
                    listOf(
                        esc(r.aptNm), esc(r.dealAmount), esc(r.buildYear),
                        esc(r.dealYear), esc(r.dealMonth), esc(r.dealDay),
                        esc(r.areaForExclusiveUse), esc(r.floor), esc(r.jibun), esc(r.umdNm)
                    ).joinToString(",")
                )
                w.write("\n")
            }
        }
        return file
    }

    // ------------------------------------------------------------
    // Summary (CSV only)
    // ------------------------------------------------------------
    data class CsvSummary(
        val nRows: Int,
        val nCols: Int,
        val nNumeric: Int,
        val nCategorical: Int,
        val fileSizeBytes: Long
    )

    private fun loadCsvSummaryFromFile(file: File): CsvSummary {
        val bytes = file.readBytes()
        val fileSize = bytes.size.toLong()
        val lines = file.bufferedReader().readLines()
        return summaryFromLines(lines, fileSize)
    }

    private fun summaryFromLines(lines: List<String>, fileSizeBytes: Long): CsvSummary {
        if (lines.isEmpty()) return CsvSummary(0, 0, 0, 0, fileSizeBytes)

        val header = splitCsvLine(lines.first())
        val dataLines = lines.drop(1).filter { it.isNotBlank() }
        val nRows = dataLines.size
        val nCols = header.size

        var numericCount = 0
        var categoricalCount = 0

        for (colIdx in 0 until nCols) {
            val colValues = dataLines.map { line ->
                splitCsvLine(line).getOrNull(colIdx)?.trim().orEmpty().trim('"')
            }
            val numericValues = colValues.mapNotNull { it.toDoubleOrNull() }
            val isNumeric = numericValues.isNotEmpty() && numericValues.size >= (nRows * 0.5)

            if (isNumeric) numericCount++ else categoricalCount++
        }

        return CsvSummary(nRows, nCols, numericCount, categoricalCount, fileSizeBytes)
    }

    // 매우 단순 CSV split (따옴표 최소 대응)
    private fun splitCsvLine(line: String): List<String> {
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

    // ------------------------------------------------------------
    // Overview UI
    // ------------------------------------------------------------
    private fun applySummary(summary: CsvSummary) {
        tvRowsValue.text = summary.nRows.toString()
        tvColumnsValue.text = summary.nCols.toString()
        tvFileSizeValue.text = formatFileSize(summary.fileSizeBytes)
        tvNumericValue.text = summary.nNumeric.toString()
        tvCategoricalValue.text = summary.nCategorical.toString()
    }

    private fun clearOverview() {
        tvRowsValue.text = "-"
        tvColumnsValue.text = "-"
        tvFileSizeValue.text = "-"
        tvNumericValue.text = "-"
        tvCategoricalValue.text = "-"
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        return String.format("%.1f KB", kb)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
