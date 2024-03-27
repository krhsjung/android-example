package kr.hs.jung.example.utils.webrtc

import kr.hs.jung.example.utils.webrtc.peer.StreamPeerType

fun StreamPeerType.stringify() = when (this) {
    StreamPeerType.PUBLISHER -> "publisher"
    StreamPeerType.SUBSCRIBER -> "subscriber"
}