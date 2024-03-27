package kr.hs.jung.example.utils.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import io.getstream.log.taggedLogger
import io.getstream.webrtc.android.ktx.stringify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kr.hs.jung.example.utils.webrtc.audio.AudioHandler
import kr.hs.jung.example.utils.webrtc.audio.AudioSwitchHandler
import kr.hs.jung.example.utils.webrtc.peer.StreamPeerConnection
import kr.hs.jung.example.utils.webrtc.peer.StreamPeerConnectionFactory
import kr.hs.jung.example.utils.webrtc.peer.StreamPeerType
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.util.UUID


interface WebRTCManagerListener {
    fun onStreamAdded(mediaStream: MediaStream)
    fun onNegotiationNeeded(peerConnection: StreamPeerConnection)
    fun onIceCandidateRequest(iceCandidate: IceCandidate)
}

class WebRTCManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val peerConnectionFactory: StreamPeerConnectionFactory,
    private val listener: WebRTCManagerListener? = null
) {
    private val logger by taggedLogger("HelloWorldTutorial:WebRTCClient")

    // used to send local video track to the fragment
    val localVideoSinkFlow = MutableSharedFlow<VideoTrack>()

    // used to send remote video track to the sender
    val remoteVideoSinkFlow = MutableSharedFlow<VideoTrack>()

    // declaring video constraints and setting OfferToReceiveVideo to true
    // this step is mandatory to create valid offer and answer
    private val mediaConstraints = MediaConstraints().apply {
        mandatory.addAll(
            listOf(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"),
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"),
            ),
        )
    }

    // getting front camera
    private val videoCapturer: VideoCapturer by lazy { buildCameraCapturer() }
    private val cameraManager by lazy { context.getSystemService<CameraManager>() }
    private val cameraEnumerator: Camera2Enumerator by lazy {
        Camera2Enumerator(context)
    }

    private val resolution: CameraEnumerationAndroid.CaptureFormat
        get() {
            val frontCamera = cameraEnumerator.deviceNames.first { cameraName ->
                cameraEnumerator.isFrontFacing(cameraName)
            }
            val supportedFormats = cameraEnumerator.getSupportedFormats(frontCamera) ?: emptyList()
            return supportedFormats.firstOrNull {
                (it.width == 720 || it.width == 480 || it.width == 360)
            } ?: error("There is no matched resolution!")
        }

    // we need it to initialize video capturer
    private val surfaceTextureHelper = SurfaceTextureHelper.create(
        "SurfaceTextureHelperThread",
        peerConnectionFactory.eglBaseContext,
    )

    private val videoSource by lazy {
        peerConnectionFactory.makeVideoSource(videoCapturer.isScreencast).apply {
            videoCapturer.initialize(surfaceTextureHelper, context, this.capturerObserver)
            videoCapturer.startCapture(resolution.width, resolution.height, 30)
        }
    }

    private val localVideoTrack: VideoTrack by lazy {
        peerConnectionFactory.makeVideoTrack(
            source = videoSource,
            trackId = "Video${UUID.randomUUID()}",
        )
    }

    /** Audio properties */

    private val audioHandler: AudioHandler by lazy {
        AudioSwitchHandler(context)
    }

    private val audioManager by lazy {
        context.getSystemService<AudioManager>()
    }

    private val audioConstraints: MediaConstraints by lazy {
        buildAudioConstraints()
    }

    private val audioSource by lazy {
        peerConnectionFactory.makeAudioSource(audioConstraints)
    }

    private val localAudioTrack: AudioTrack by lazy {
        peerConnectionFactory.makeAudioTrack(
            source = audioSource,
            trackId = "Audio${UUID.randomUUID()}",
        )
    }

    private val peerConnection: StreamPeerConnection =
        peerConnectionFactory.makePeerConnection(
            coroutineScope = coroutineScope,
            configuration = peerConnectionFactory.rtcConfig,
            type = StreamPeerType.SUBSCRIBER,
            mediaConstraints = mediaConstraints,
            onStreamAdded = { mediaStream ->
                coroutineScope.launch { listener?.onStreamAdded(mediaStream) }
            },
            onNegotiationNeeded = { streamPeerConnection, _ ->
                coroutineScope.launch { listener?.onNegotiationNeeded(streamPeerConnection) }
            },
            onIceCandidateRequest = { iceCandidate, _ ->
                coroutineScope.launch { listener?.onIceCandidateRequest(iceCandidate) }
            },
            onVideoTrack = { rtpTransceiver ->
                val track = rtpTransceiver?.receiver?.track() ?: return@makePeerConnection
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    val videoTrack = track as VideoTrack
                    coroutineScope.launch {
                        remoteVideoSinkFlow.emit(videoTrack)
                    }
                }
            },
        )

    fun offer(completion: (sdp: String) -> Unit) {
        coroutineScope.launch {
            val offer = peerConnection.createOffer().getOrThrow()
            val result = peerConnection.setLocalDescription(offer)
            result.onSuccess {
                logger.d { "[offer] onSuccess" }
                completion(offer.description)
            }
        }
    }

    fun answer(completion: (sdp: String) -> Unit) {
        coroutineScope.launch {
            val answer = peerConnection.createAnswer().getOrThrow()
            val result = peerConnection.setLocalDescription(answer)
            result.onSuccess {
                completion(answer.description)
            }
            logger.d { "[SDP] send answer: ${answer.stringify()}" }
        }
    }

    fun setAnswer(sdp: String) {
        coroutineScope.launch {
            peerConnection.setRemoteDescription(
                SessionDescription(SessionDescription.Type.ANSWER, sdp),
            )
        }

    }

    fun setIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        coroutineScope.launch {
            peerConnection.addIceCandidate(
                IceCandidate(sdpMid, sdpMLineIndex, sdp),
            )
        }
    }

    fun startCaptureLocalVideo() {
        peerConnection.connection.addTrack(localVideoTrack)
        peerConnection.connection.addTrack(localAudioTrack)
        coroutineScope.launch {
            // sending local video track to show local video from start
            localVideoSinkFlow.emit(localVideoTrack)
        }
    }

    fun dispose() {
        // dispose audio & video tracks.
        remoteVideoSinkFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localVideoSinkFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localAudioTrack.dispose()
        localVideoTrack.dispose()

        // dispose audio handler and video capturer.
        audioHandler.stop()
        videoCapturer.stopCapture()
        videoCapturer.dispose()

        // dispose peerConnection.
        peerConnection.connection.dispose()
    }

    private fun buildCameraCapturer(): VideoCapturer {
        logger.d { "[buildCameraCapturer]" }
        val manager = cameraManager ?: throw RuntimeException("CameraManager was not initialized!")

        val ids = manager.cameraIdList
        var foundCamera = false
        var cameraId = ""

        for (id in ids) {
            val characteristics = manager.getCameraCharacteristics(id)
            val cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (cameraLensFacing == CameraMetadata.LENS_FACING_BACK) {
                foundCamera = true
                cameraId = id
            }
        }

        if (!foundCamera && ids.isNotEmpty()) {
            cameraId = ids.first()
        }

        val camera2Capturer = Camera2Capturer(context, cameraId, null)
        return camera2Capturer
    }

    private fun buildAudioConstraints(): MediaConstraints {
        logger.d { "[buildAudioConstraints]" }
        val mediaConstraints = MediaConstraints()
        val items = listOf(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                true.toString(),
            ),
            MediaConstraints.KeyValuePair(
                "googAutoGainControl",
                true.toString(),
            ),
            MediaConstraints.KeyValuePair(
                "googHighpassFilter",
                true.toString(),
            ),
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                true.toString(),
            ),
            MediaConstraints.KeyValuePair(
                "googTypingNoiseDetection",
                true.toString(),
            ),
        )

        return mediaConstraints.apply {
            with(optional) {
                add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
                addAll(items)
            }
        }
    }

    private fun setupAudio() {
        logger.d { "[setupAudio] #sfu; no args" }
        audioHandler.start()
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager?.availableCommunicationDevices ?: return
            val deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

            val device = devices.firstOrNull { it.type == deviceType } ?: return

            val isCommunicationDeviceSet = audioManager?.setCommunicationDevice(device)
            logger.d { "[setupAudio] #sfu; isCommunicationDeviceSet: $isCommunicationDeviceSet" }
        }
    }
}