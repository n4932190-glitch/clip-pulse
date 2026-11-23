package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ViralClipAiRepository
import com.example.data.local.SavedClipDatabase
import com.example.data.local.SavedClipEntity
import com.example.data.local.SavedClipRepository
import com.example.data.remote.VideoSourceExtractor
import com.example.domain.model.ClipDurationOption
import com.example.domain.model.FramingCropMode
import com.example.domain.model.ResolvedVideoSource
import com.example.domain.model.SubtitleCue
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.SubtitleSize
import com.example.domain.model.SubtitleStyle
import com.example.domain.model.ViralClip
import com.example.media.ExportOutcome
import com.example.media.VideoClipExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ClipPulseViewModel"

sealed interface StudioPipelineState {
    data object Idle : StudioPipelineState
    data class Processing(val stepLabel: String) : StudioPipelineState
    data object Ready : StudioPipelineState
    data class Error(val message: String) : StudioPipelineState
}

sealed interface ExportPipelineState {
    data object Idle : ExportPipelineState
    data class Exporting(val progressPercent: Int) : ExportPipelineState
    data class Saved(val outcome: ExportOutcome) : ExportPipelineState
    data class Error(val message: String) : ExportPipelineState
}

data class ClipPulseUiState(
    val urlInput: String = "",
    val selectedDuration: ClipDurationOption = ClipDurationOption.SIXTY,
    val framingCropMode: FramingCropMode = FramingCropMode.CENTER,
    val selectedSubtitleStyle: SubtitleStyle = SubtitleStyle.HORMOZI_POP,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val pipelineState: StudioPipelineState = StudioPipelineState.Idle,
    val resolvedSource: ResolvedVideoSource? = null,
    val viralClips: List<ViralClip> = emptyList(),
    val selectedClipIndex: Int = 0,
    val exportState: ExportPipelineState = ExportPipelineState.Idle,
    val navigateToEditorTrigger: Int = 0
) {
    val activeClip: ViralClip?
        get() = viralClips.getOrNull(selectedClipIndex)
}

class ClipPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = VideoSourceExtractor(application)
    private val aiRepository = ViralClipAiRepository()
    private val exporter = VideoClipExporter(application)
    private val savedRepository = SavedClipRepository(
        SavedClipDatabase.getInstance(application).savedClipDao()
    )

    private val _uiState = MutableStateFlow(ClipPulseUiState())
    val uiState: StateFlow<ClipPulseUiState> = _uiState.asStateFlow()

    val savedClips: StateFlow<List<SavedClipEntity>> = savedRepository.savedClips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private var activeAnalysisJob: Job? = null
    private var activeExportJob: Job? = null

    fun updateUrlInput(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    fun selectDuration(duration: ClipDurationOption) {
        val previous = _uiState.value.selectedDuration
        _uiState.update { it.copy(selectedDuration = duration) }
        val currentSource = _uiState.value.resolvedSource
        if (previous != duration && currentSource != null && _uiState.value.pipelineState is StudioPipelineState.Ready) {
            analyzeResolvedSource(currentSource, duration)
        }
    }

    fun selectFramingMode(mode: FramingCropMode) {
        _uiState.update { it.copy(framingCropMode = mode) }
    }

    fun selectSubtitleStyle(style: SubtitleStyle) {
        _uiState.update { it.copy(selectedSubtitleStyle = style) }
    }

    fun selectSubtitlePosition(position: SubtitlePosition) {
        _uiState.update { it.copy(subtitlePosition = position) }
    }

    fun selectSubtitleSize(size: SubtitleSize) {
        _uiState.update { it.copy(subtitleSize = size) }
    }

    fun selectClipIndex(index: Int) {
        if (index in _uiState.value.viralClips.indices) {
            _uiState.update { it.copy(selectedClipIndex = index, exportState = ExportPipelineState.Idle) }
        }
    }

    fun updateSubtitleCueText(cueId: String, newText: String, newHighlight: String) {
        val state = _uiState.value
        val activeClip = state.activeClip ?: return
        val updatedCues = activeClip.subtitles.map { cue ->
            if (cue.id == cueId) {
                cue.copy(text = newText, highlightWord = newHighlight)
            } else cue
        }
        val updatedClip = activeClip.copy(subtitles = updatedCues)
        val updatedList = state.viralClips.toMutableList().also {
            it[state.selectedClipIndex] = updatedClip
        }
        _uiState.update { it.copy(viralClips = updatedList) }
    }

    fun addSubtitleCue(startOffsetMs: Long, endOffsetMs: Long, text: String, highlightWord: String) {
        val state = _uiState.value
        val activeClip = state.activeClip ?: return
        val newCue = SubtitleCue(
            id = java.util.UUID.randomUUID().toString(),
            startOffsetMs = startOffsetMs,
            endOffsetMs = endOffsetMs,
            text = text,
            highlightWord = highlightWord
        )
        val updatedClip = activeClip.copy(
            subtitles = (activeClip.subtitles + newCue).sortedBy { it.startOffsetMs }
        )
        val updatedList = state.viralClips.toMutableList().also {
            it[state.selectedClipIndex] = updatedClip
        }
        _uiState.update { it.copy(viralClips = updatedList) }
    }

    fun extractFromLink(rawUrl: String = _uiState.value.urlInput) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(pipelineState = StudioPipelineState.Error("Paste a YouTube or Instagram video link first."))
            }
            return
        }

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    urlInput = trimmed,
                    pipelineState = StudioPipelineState.Processing("Fetching video details & captions…"),
                    exportState = ExportPipelineState.Idle
                )
            }
            try {
                val source = extractor.resolveUrl(trimmed)
                _uiState.update {
                    it.copy(
                        resolvedSource = source,
                        pipelineState = StudioPipelineState.Processing(
                            "AI scanning for viral ${_uiState.value.selectedDuration.label} hooks…"
                        )
                    )
                }
                val keyframes = if (source.isStreamPlayable && !source.playableUri.isNullOrBlank()) {
                    extractor.extractKeyframesForGemini(source.playableUri, source.durationMs)
                } else emptyList()

                val clips = aiRepository.findViralClips(
                    source = source,
                    targetDuration = _uiState.value.selectedDuration,
                    keyframes = keyframes
                )
                keyframes.forEach { it.second.recycle() }

                _uiState.update {
                    it.copy(
                        viralClips = clips,
                        selectedClipIndex = 0,
                        pipelineState = StudioPipelineState.Ready,
                        navigateToEditorTrigger = it.navigateToEditorTrigger + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Link extraction failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = StudioPipelineState.Error(
                            e.localizedMessage ?: "Unable to extract video clips from this link."
                        )
                    )
                }
            }
        }
    }

    fun loadDeviceVideo(uri: Uri, attachToCurrentProjectOnly: Boolean = false) {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            if (attachToCurrentProjectOnly && _uiState.value.resolvedSource != null) {
                // Upgrade the current link-based project with full playable local video footage
                val deviceMeta = extractor.resolveDeviceVideo(uri)
                _uiState.update { state ->
                    state.copy(
                        resolvedSource = state.resolvedSource?.copy(
                            playableUri = uri.toString(),
                            isStreamPlayable = true,
                            durationMs = deviceMeta.durationMs
                        ),
                        exportState = ExportPipelineState.Idle
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    pipelineState = StudioPipelineState.Processing("Inspecting device video frames…"),
                    exportState = ExportPipelineState.Idle
                )
            }
            try {
                val source = extractor.resolveDeviceVideo(uri)
                _uiState.update {
                    it.copy(
                        resolvedSource = source,
                        pipelineState = StudioPipelineState.Processing(
                            "AI finding viral ${_uiState.value.selectedDuration.label} moments & subtitles…"
                        )
                    )
                }
                val keyframes = extractor.extractKeyframesForGemini(uri.toString(), source.durationMs)
                val clips = aiRepository.findViralClips(
                    source = source,
                    targetDuration = _uiState.value.selectedDuration,
                    keyframes = keyframes
                )
                keyframes.forEach { it.second.recycle() }

                _uiState.update {
                    it.copy(
                        viralClips = clips,
                        selectedClipIndex = 0,
                        pipelineState = StudioPipelineState.Ready,
                        navigateToEditorTrigger = it.navigateToEditorTrigger + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Device video analysis failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = StudioPipelineState.Error(
                            e.localizedMessage ?: "Failed to analyze device video."
                        )
                    )
                }
            }
        }
    }

    private fun analyzeResolvedSource(source: ResolvedVideoSource, duration: ClipDurationOption) {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pipelineState = StudioPipelineState.Processing(
                        "AI re-cutting ${duration.label} viral clips…"
                    )
                )
            }
            try {
                val keyframes = if (source.isStreamPlayable && !source.playableUri.isNullOrBlank()) {
                    extractor.extractKeyframesForGemini(source.playableUri, source.durationMs)
                } else emptyList()

                val clips = aiRepository.findViralClips(source, duration, keyframes)
                keyframes.forEach { it.second.recycle() }

                _uiState.update {
                    it.copy(
                        viralClips = clips,
                        selectedClipIndex = 0,
                        pipelineState = StudioPipelineState.Ready
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Re-analysis failed", e)
                _uiState.update {
                    it.copy(
                        pipelineState = StudioPipelineState.Error(
                            e.localizedMessage ?: "Failed to re-cut clips."
                        )
                    )
                }
            }
        }
    }

    fun saveClipToDevice() {
        val state = _uiState.value
        val source = state.resolvedSource ?: return
        val clip = state.activeClip ?: return

        activeExportJob?.cancel()
        activeExportJob = viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportPipelineState.Exporting(2)) }
            try {
                val outcome = exporter.exportAndSaveClip(
                    source = source,
                    clip = clip,
                    subtitleStyle = state.selectedSubtitleStyle,
                    subtitlePosition = state.subtitlePosition,
                    subtitleSize = state.subtitleSize,
                    cropMode = state.framingCropMode,
                    onProgress = { pct ->
                        _uiState.update { current ->
                            if (current.exportState is ExportPipelineState.Exporting) {
                                current.copy(exportState = ExportPipelineState.Exporting(pct))
                            } else current
                        }
                    }
                )

                savedRepository.recordSavedClip(
                    SavedClipEntity(
                        title = clip.title,
                        sourcePlatform = source.platform.label,
                        sourceUrl = source.inputUrl,
                        mediaStoreUri = outcome.mediaStoreUri.toString(),
                        thumbnailUrl = source.thumbnailUrl,
                        durationSeconds = clip.durationSeconds,
                        viralityScore = clip.viralityScore,
                        subtitleStyleName = state.selectedSubtitleStyle.displayName
                    )
                )

                _uiState.update { it.copy(exportState = ExportPipelineState.Saved(outcome)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Save to device failed", e)
                _uiState.update {
                    it.copy(
                        exportState = ExportPipelineState.Error(
                            e.localizedMessage ?: "Could not save video to device."
                        )
                    )
                }
            }
        }
    }

    fun dismissExportSheet() {
        activeExportJob?.cancel()
        _uiState.update { it.copy(exportState = ExportPipelineState.Idle) }
    }

    fun clearPipelineError() {
        _uiState.update {
            it.copy(
                pipelineState = if (it.resolvedSource != null && it.viralClips.isNotEmpty()) {
                    StudioPipelineState.Ready
                } else StudioPipelineState.Idle
            )
        }
    }

    fun deleteSavedClip(id: Long) {
        viewModelScope.launch {
            savedRepository.deleteClip(id)
        }
    }
}
