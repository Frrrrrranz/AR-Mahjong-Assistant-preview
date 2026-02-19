package com.example.ai_assist

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ai_assist.databinding.ActivityMainBinding
import com.example.ai_assist.repository.ChatRepository
import com.example.ai_assist.service.GameApiService
import com.example.ai_assist.viewmodel.ChatViewModel
import com.example.ai_assist.viewmodel.ChatViewModelFactory
import com.example.ai_assist.utils.RayNeoAudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var cameraExecutor: ExecutorService

    // Camera2 variables
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var backHandler: Handler
    private lateinit var backHandlerThread: HandlerThread
    private var cameraJob: Job? = null
    private var previewSize: Size? = null
    private var currentPhotoFile: File? = null
    
    // Audio Recorder
    private var audioRecorder: RayNeoAudioRecorder? = null
    private var isRecordingAudio = false

    // Game State Management
    enum class GameState { IDLE, GAMING, CAMERA_PREVIEW, PHOTO_REVIEW }
    private var currentState = GameState.IDLE

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                showCustomToast("需要相机和存储权限")
            } else {
                setupCamera2()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        backHandlerThread = HandlerThread("background")
        backHandlerThread.start()
        backHandler = Handler(backHandlerThread.looper)

        setupDependencies()
        setupUI()
        observeViewModel()
        checkPermissions()
        
        // 初始状态
        updateGameState(GameState.IDLE)
    }

    override fun onResume() {
        super.onResume()
        if (currentState == GameState.CAMERA_PREVIEW) {
            startCamera()
        }
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        audioRecorder?.stop()
        cameraExecutor.shutdown()
        backHandlerThread.quitSafely()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun setupDependencies() {
        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.SERVER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val apiService = retrofit.create(GameApiService::class.java)
        val repository = ChatRepository(apiService)
        
        val factory = ChatViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        audioRecorder = RayNeoAudioRecorder(this) { file ->
            viewModel.uploadAudio(file)
        }
    }

    private fun setupUI() {
        binding.tvStatus.text = "已连接"

        // 主操作按钮 (开始/结束对局)
        binding.btnActionPrimary.setOnClickListener {
            when (currentState) {
                GameState.IDLE -> {
                    lifecycleScope.launch {
                        viewModel.startNewSession()
                        clearGameData()
                        updateGameState(GameState.GAMING)
                    }
                }
                GameState.GAMING -> {
                    viewModel.endCurrentSession()
                    updateGameState(GameState.IDLE)
                    clearGameData()
                }
                else -> {}
            }
        }
        
        // 拍照按钮
        binding.btnActionSecondary.setOnClickListener {
            if (currentState == GameState.GAMING) {
                updateGameState(GameState.CAMERA_PREVIEW)
            }
        }

        // 相机区域内的 FAB 快门按钮
        binding.btnShutter.setOnClickListener {
            if (currentState == GameState.CAMERA_PREVIEW) {
                takePhoto()
            }
        }

        // 照片确认 - 发送
        binding.btnReviewSend.setOnClickListener {
            currentPhotoFile?.let { file ->
                showCustomToast("正在分析手牌...")
                showLoading(true)
                viewModel.uploadPhoto(file)
            }
            updateGameState(GameState.GAMING)
        }

        // 照片确认 - 重拍
        binding.btnReviewCancel.setOnClickListener {
            showCustomToast("已取消")
            updateGameState(GameState.CAMERA_PREVIEW)
        }
        
        // 语音录制按钮 (按住录制)
        binding.btnActionRecord.setOnTouchListener { _, event ->
             when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (currentState == GameState.GAMING) {
                        audioRecorder?.start()
                        isRecordingAudio = true
                        showCustomToast("录音中...")
                        binding.btnActionRecord.text = "🎤 松开结束"
                        binding.btnActionRecord.alpha = 0.7f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecordingAudio) {
                        audioRecorder?.stop()
                        isRecordingAudio = false
                        showCustomToast("录音已发送")
                        binding.btnActionRecord.text = "🎤 按住说话"
                        binding.btnActionRecord.alpha = 1.0f
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateGameState(newState: GameState) {
        // 离开相机预览状态时关闭相机
        if (currentState == GameState.CAMERA_PREVIEW && newState != GameState.CAMERA_PREVIEW && newState != GameState.PHOTO_REVIEW) {
            closeCamera()
        }
        if (currentState == GameState.PHOTO_REVIEW && newState != GameState.PHOTO_REVIEW) {
            closeCamera()
        }

        currentState = newState
        
        // 重置 Overlay 可见性
        binding.layoutCameraArea.visibility = View.GONE
        binding.layoutPhotoReviewContainer.visibility = View.GONE
        
        when (newState) {
            GameState.IDLE -> {
                binding.tvStatus.text = "等待开始"
                binding.tvStatus.setTextColor(getColor(R.color.text_secondary))

                binding.btnActionPrimary.text = "开始对局"
                binding.btnActionPrimary.setBackgroundResource(R.drawable.bg_btn_primary)
                binding.btnActionPrimary.visibility = View.VISIBLE
                binding.btnActionSecondary.visibility = View.GONE
                binding.btnActionRecord.visibility = View.GONE
            }
            GameState.GAMING -> {
                binding.tvStatus.text = "对局中"
                binding.tvStatus.setTextColor(getColor(R.color.accent_green))

                binding.btnActionPrimary.text = "结束对局"
                binding.btnActionPrimary.setBackgroundResource(R.drawable.bg_btn_danger)
                binding.btnActionPrimary.visibility = View.VISIBLE
                
                binding.btnActionSecondary.text = "📷 拍照分析"
                binding.btnActionSecondary.visibility = View.VISIBLE
                
                binding.btnActionRecord.text = "🎤 按住说话"
                binding.btnActionRecord.visibility = View.VISIBLE

                // 隐藏加载指示器
                showLoading(false)
            }
            GameState.CAMERA_PREVIEW -> {
                binding.tvStatus.text = "拍照模式"
                binding.tvStatus.setTextColor(getColor(R.color.accent_blue))

                // 显示相机预览区域（半屏）
                binding.layoutCameraArea.visibility = View.VISIBLE
                
                // 隐藏底部操作栏，使用相机内的 FAB
                binding.btnActionPrimary.visibility = View.GONE
                binding.btnActionSecondary.text = "✕ 返回"
                binding.btnActionSecondary.setBackgroundResource(R.drawable.bg_btn_danger)
                binding.btnActionSecondary.visibility = View.VISIBLE
                binding.btnActionSecondary.setOnClickListener {
                    updateGameState(GameState.GAMING)
                    // NOTE: 重新绑定拍照按钮的点击事件
                    binding.btnActionSecondary.setOnClickListener {
                        if (currentState == GameState.GAMING) {
                            updateGameState(GameState.CAMERA_PREVIEW)
                        }
                    }
                }
                binding.btnActionRecord.visibility = View.GONE
                
                startCamera()
            }
            GameState.PHOTO_REVIEW -> {
                binding.tvStatus.text = "确认照片"
                binding.tvStatus.setTextColor(getColor(R.color.accent_gold))

                binding.layoutPhotoReviewContainer.visibility = View.VISIBLE
                
                // 隐藏所有底部按钮（照片确认区有自己的按钮）
                binding.btnActionPrimary.visibility = View.GONE
                binding.btnActionSecondary.visibility = View.GONE
                binding.btnActionRecord.visibility = View.GONE
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.mappedResult.collect { result ->
                result?.let {
                    binding.tvContentHand.text = formatMahjongText(it.userHand.joinToString(" "))
                    binding.tvContentSuggested.text = formatMahjongText(it.meldedTiles.joinToString(" "))
                    binding.tvContentWaiting.text = formatMahjongText(it.suggestedPlay)
                    // 收到结果后隐藏加载指示器
                    showLoading(false)
                }
            }
        }
    }

    private fun formatMahjongText(originalText: String): SpannableString {
        val spannable = SpannableString(originalText)
        val mahjongTypeface = if (AppConfig.USE_COLOR_FONT) {
            try {
                resources.getFont(R.font.mahjong_color)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load mahjong font", e)
                null
            }
        } else {
            null
        }

        val scaleFactor = if (AppConfig.USE_COLOR_FONT) AppConfig.FONT_SCALE_COLOR else AppConfig.FONT_SCALE_DEFAULT

        var index = 0
        while (index < originalText.length) {
            val codePoint = originalText.codePointAt(index)
            val charCount = Character.charCount(codePoint)

            if (codePoint in 0x1F000..0x1F02B) {
                spannable.setSpan(RelativeSizeSpan(scaleFactor), index, index + charCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (mahjongTypeface != null) {
                    spannable.setSpan(TypefaceSpan(mahjongTypeface), index, index + charCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            index += charCount
        }
        return spannable
    }

    private fun clearGameData() {
        binding.tvContentHand.text = ""
        binding.tvContentSuggested.text = ""
        binding.tvContentWaiting.text = ""
    }

    // ==================== 加载指示器 ====================

    private fun showLoading(show: Boolean) {
        binding.layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ==================== Camera2 实现 ====================

    private fun startCamera() {
        if (binding.viewCameraPreview.isAvailable) {
            openCamera(binding.viewCameraPreview.width, binding.viewCameraPreview.height)
        } else {
            binding.viewCameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera(width, height)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }
    
    private fun openCamera(width: Int, height: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
             return
        }
        lifecycleScope.launch {
            delay(100L) // 去抖
            setupCamera2()
        }
    }

    private fun setupCamera2() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.first() 
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            if (map != null) {
                 val supportedSizes = map.getOutputSizes(SurfaceTexture::class.java).toList()
                 previewSize = chooseOptimalSize(supportedSizes.toTypedArray(), 900, 1200)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, backHandler)
            }
        } catch (e: Exception) {
            Log.e("Camera2", "Failed to open camera", e)
        }
    }
    
    private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int): Size {
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        
        for (option in choices) {
            if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                bigEnough.add(option)
            } else {
                notBigEnough.add(option)
            }
        }

        if (bigEnough.size > 0) {
            return Collections.min(bigEnough) { lhs, rhs ->
                java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
            }
        } else if (notBigEnough.size > 0) {
            return Collections.max(notBigEnough) { lhs, rhs ->
                java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
            }
        } else {
            return choices[0]
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraJob = lifecycleScope.launch {
                cameraDevice = camera
                delay(100L)
                if (cameraDevice == camera) {
                    createCameraPreviewSession(camera)
                }
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
            cameraJob?.cancel()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreviewSession(camera: CameraDevice) {
        try {
            val texture = binding.viewCameraPreview.surfaceTexture ?: return
            
            previewSize?.let {
                texture.setDefaultBufferSize(it.width, it.height)
            }
            
            val surface = Surface(texture)
            
            val pWidth = previewSize?.width ?: 900
            val pHeight = previewSize?.height ?: 1200
            
            imageReader = ImageReader.newInstance(pWidth, pHeight, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener(onImageAvailableListener, backHandler)

            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))

            val outputConfigs = listOf(
                OutputConfiguration(surface), 
                OutputConfiguration(imageReader!!.surface)
            )

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                cameraExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        cameraCaptureSession = session
                        try {
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backHandler)
                        } catch (e: Exception) {
                            Log.e("Camera2", "Capture request failed", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("Camera2", "Session configuration failed")
                    }
                }
            )
            camera.createCaptureSession(sessionConfig)

        } catch (e: Exception) {
            Log.e("Camera2", "Create capture session failed", e)
        }
    }
    
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        
        lifecycleScope.launch(Dispatchers.IO) {
            var imageClosed = false
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                imageClosed = true

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // 旋转手机竖屏拍摄的照片（通常需要旋转 90°）
                val matrix = Matrix()
                matrix.postRotate(90f)
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                
                // 裁剪底部 50%（手牌区域）
                val cropY = (rotatedBitmap.height * 0.50).toInt()
                val cropHeight = (rotatedBitmap.height * 0.50).toInt()
                
                val finalY = cropY.coerceIn(0, rotatedBitmap.height)
                val finalHeight = cropHeight.coerceAtMost(rotatedBitmap.height - finalY)
                
                val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, 0, finalY, rotatedBitmap.width, finalHeight)
                
                val photoFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
                )
                
                FileOutputStream(photoFile).use { fos ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                }
                
                bitmap.recycle()
                rotatedBitmap.recycle()
                
                currentPhotoFile = photoFile
                val uri = Uri.fromFile(photoFile)
                withContext(Dispatchers.Main) {
                    // 快门闪光效果
                    playShutterEffect()
                    showPhotoReview(uri)
                }
            } catch (e: Exception) {
                Log.e("Camera2", "Save photo failed", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("保存照片失败: ${e.message}")
                }
            } finally {
                if (!imageClosed) {
                    try { image.close() } catch (e: Exception) {}
                }
            }
        }
    }

    private fun takePhoto() {
        try {
            val session = cameraCaptureSession ?: return
            val device = cameraDevice ?: return
            val reader = imageReader ?: return

            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)

            session.capture(captureBuilder.build(), null, backHandler)
            
        } catch (e: Exception) {
            Log.e("Camera2", "Take photo failed", e)
            showCustomToast("拍照失败")
        }
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            previewSize = null 
        } catch (e: Exception) {
            Log.e("Camera2", "Close camera failed", e)
        }
    }
    
    private fun showPhotoReview(uri: Uri) {
        updateGameState(GameState.PHOTO_REVIEW)
        binding.layoutPhotoReviewContainer.visibility = View.VISIBLE
        binding.imagePhotoReview.setImageURI(uri)
    }

    // ==================== UI 辅助方法 ====================

    /**
     * 快门闪光效果：拍照时短暂白闪
     */
    private fun playShutterEffect() {
        binding.viewShutterFlash.visibility = View.VISIBLE
        val fadeOut = AlphaAnimation(0.6f, 0f).apply {
            duration = 200
        }
        binding.viewShutterFlash.startAnimation(fadeOut)
        binding.viewShutterFlash.postDelayed({
            binding.viewShutterFlash.visibility = View.GONE
        }, 200)
    }

    private fun showCustomToast(message: String) {
        binding.tvCustomToast.text = message
        binding.tvCustomToast.visibility = View.VISIBLE
        // 淡入效果
        binding.tvCustomToast.alpha = 0f
        binding.tvCustomToast.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
        binding.tvCustomToast.postDelayed({
            // 淡出效果
            binding.tvCustomToast.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.tvCustomToast.visibility = View.GONE
                }
                .start()
        }, 2000)
    }
}