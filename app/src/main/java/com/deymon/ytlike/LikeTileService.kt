package com.deymon.ytlike

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile: one tap likes the current track, works without unlocking. */
class LikeTileService : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        Feedback.show(this, Like.like(this))
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val controller = Like.target(this)
        tile.state = if (controller != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = controller?.let { Like.title(it) }?.ifEmpty { null } ?: "ничего не играет"
        tile.updateTile()
    }
}
