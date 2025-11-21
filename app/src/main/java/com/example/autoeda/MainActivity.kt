package com.example.autoeda

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // CSV 등에서 불러온 실제 컬럼명 리스트 ← 나중에 실제 데이터로 교체
        val columnNames = listOf("age", "height", "weight", "city", "income")

        val actvTarget = findViewById<AutoCompleteTextView>(R.id.actvTargetColumn)
        val btnSetTarget = findViewById<Button>(R.id.btnSetTarget)

        // AutoCompleteTextView 어댑터
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            columnNames
        )

        actvTarget.setAdapter(adapter)

        // 입력 칸 누르면 바로 드롭다운 열리게
        actvTarget.setOnClickListener {
            actvTarget.showDropDown()
        }

        // Set 버튼 → 선택된 타겟 컬럼 사용
        btnSetTarget.setOnClickListener {
            val selectedColumn = actvTarget.text.toString()

            if (selectedColumn.isEmpty()) {
                actvTarget.error = "Select a column"
            } else {
                // TODO: 선택된 컬럼을 ViewModel 등에 저장
                // viewModel.setTargetColumn(selectedColumn)

                println("Target column selected: $selectedColumn")
            }
        }
    }
}
