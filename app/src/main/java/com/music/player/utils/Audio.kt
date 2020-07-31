package com.music.player.utils

import java.io.Serializable

class Audio (
    var data: String,
    var title: String,
    var album: String,
    var artist: String,
    var albumId: Long
): Serializable