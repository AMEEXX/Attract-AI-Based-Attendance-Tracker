package com.attract.attendance.ui.screens.classworkspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractIconButton
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassScreen(
    onBack: () -> Unit,
    onCreate: (CreateClassCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var requiredPercentage by remember { mutableStateOf("75") }
    var totalClasses by remember { mutableStateOf("30") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create class", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    AttractIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            AttractCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Class name") },
                        placeholder = { Text("CSE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        placeholder = { Text("Operating System") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        placeholder = { Text("A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = batch,
                        onValueChange = { batch = it },
                        label = { Text("Semester / Batch") },
                        placeholder = { Text("6") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = requiredPercentage,
                        onValueChange = { requiredPercentage = it.filter(Char::isDigit) },
                        label = { Text("Required attendance (%)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = totalClasses,
                        onValueChange = { totalClasses = it.filter(Char::isDigit) },
                        label = { Text("Total planned classes") },
                        placeholder = { Text("30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.MediumGap))

            AttractPrimaryButton(
                onClick = {
                    if (name.isNotBlank() && subject.isNotBlank()) {
                        onCreate(
                            CreateClassCommand(
                                name = name.trim(),
                                subject = subject.trim(),
                                section = section.trim().ifBlank { "A" },
                                semesterBatch = batch.trim().ifBlank { "6" },
                                requiredAttendancePercent = requiredPercentage.toIntOrNull() ?: 75,
                                totalPlannedSessions = totalClasses.toIntOrNull()?.coerceAtLeast(1) ?: 30
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && subject.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create class")
            }
        }
    }
}
