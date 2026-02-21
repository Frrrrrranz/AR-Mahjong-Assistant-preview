package com.example.ai_assist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_assist.model.AnalyzeResponse
import com.example.ai_assist.model.DetectTilesResponse
import com.example.ai_assist.model.ProcessAudioResponse
import com.example.ai_assist.repository.ChatRepository
import com.example.ai_assist.utils.MahjongMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    // Expose mapped result
    val analyzeResult: StateFlow<AnalyzeResponse?> = repository.analyzeResult
    
    val mappedResult = analyzeResult.map { response ->
        response?.let {
            AnalyzeResponse(
                userHand = MahjongMapper.mapListToUnicode(it.userHand),
                meldedTiles = MahjongMapper.mapListToUnicode(it.meldedTiles),
                suggestedPlay = MahjongMapper.mapToUnicode(it.suggestedPlay)
            )
        }
    }
    
    // NOTE: 语音处理结果，用于 UI 展示反馈
    private val _audioResult = MutableStateFlow<ProcessAudioResponse?>(null)
    val audioResult: StateFlow<ProcessAudioResponse?> = _audioResult

    // NOTE: 实时检测结果，用于叠加绘制检测框
    private val _detectionResult = MutableStateFlow<DetectTilesResponse?>(null)
    val detectionResult: StateFlow<DetectTilesResponse?> = _detectionResult
    
    private var sessionId = UUID.randomUUID().toString()

    suspend fun startNewSession(): Boolean {
        sessionId = UUID.randomUUID().toString()
        return repository.startSession(sessionId)
    }

    fun endCurrentSession() {
        val currentId = sessionId
        viewModelScope.launch {
            repository.endSession(currentId)
        }
    }
    
    fun uploadPhoto(file: File) {
        viewModelScope.launch {
            try {
                repository.analyzeImage(file, sessionId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadAudio(file: File) {
        viewModelScope.launch {
            val result = repository.uploadAudio(file, sessionId)
            _audioResult.value = result
        }
    }

    // 实时检测：仅上传图片做 YOLO 推理
    fun detectTiles(file: File) {
        viewModelScope.launch {
            val result = repository.detectTiles(file)
            _detectionResult.value = result
        }
    }

    // 供 MainActivity 在同步调用 repository 后直接更新结果
    fun updateDetectionResult(result: DetectTilesResponse) {
        _detectionResult.value = result
    }

    // 清空检测结果（退出实时检测模式时调用）
    fun clearDetectionResult() {
        _detectionResult.value = null
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

