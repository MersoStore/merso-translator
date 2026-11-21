package com.merso.gametranslate

// Holds the running stream so it can move between the Remote Play screen
// and the mini player on the main screen without disconnecting.
object StreamHolder {
    var controller: StreamController? = null
    var handedOff: Boolean = false
}
