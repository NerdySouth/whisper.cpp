package com.whispercppdemo.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.whispercppdemo.R

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        messageLog = viewModel.dataLog,
        threadCount = viewModel.threadCount,
        onBenchmarkTapped = viewModel::benchmark,
        onTranscribeSampleTapped = viewModel::transcribeSample,
        onRecordTapped = viewModel::toggleRecord,
        onThreadCountChanged = viewModel::updateThreadCount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    messageLog: String,
    threadCount: Int,
    onBenchmarkTapped: () -> Unit,
    onTranscribeSampleTapped: () -> Unit,
    onRecordTapped: () -> Unit,
    onThreadCountChanged: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped)
                    TranscribeSampleButton(enabled = canTranscribe, onClick = onTranscribeSampleTapped)
                }
                ThreadControlButtons(
                    currentThreadCount = threadCount,
                    onThreadCountChanged = onThreadCountChanged
                )
                RecordButton(
                    enabled = canTranscribe,
                    isRecording = isRecording,
                    onClick = onRecordTapped
                )
            }
            MessageLog(messageLog)
        }
    }
}

@Composable
private fun MessageLog(log: String) {
    SelectionContainer {
        Text(modifier = Modifier.verticalScroll(rememberScrollState()), text = log)
    }
}

@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text("Benchmark")
    }
}

@Composable
private fun TranscribeSampleButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text("Transcribe sample")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        }
    )
    Button(onClick = {
        if (micPermissionState.status.isGranted) {
            onClick()
        } else {
            micPermissionState.launchPermissionRequest()
        }
     }, enabled = enabled) {
        Text(
            if (isRecording) {
                "Stop recording"
            } else {
                "Start recording"
            }
        )
    }
}

@Composable
private fun ThreadControlButtons(
    currentThreadCount: Int,
    onThreadCountChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Thread Count:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(2, 4, 6, 8).forEach { threadCount ->
                Button(
                    onClick = { onThreadCountChanged(threadCount) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (threadCount == currentThreadCount) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("$threadCount")
                }
            }
        }
    }
}