package kr.hs.jung.example.utils.webrtc.audio

import kr.hs.jung.example.utils.webrtc.audio.AudioDevice

typealias AudioDeviceChangeListener = (
    audioDevices: List<AudioDevice>,
    selectedAudioDevice: AudioDevice?,
) -> Unit
