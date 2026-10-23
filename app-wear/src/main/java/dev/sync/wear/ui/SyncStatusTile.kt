package dev.sync.wear.ui

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.tiles.SuspendingTileService
import dev.sync.wear.alarm.WearAlarmReader

/**
 * Quick Glance Tile for the Galaxy Watch 7 watch face — shows the next synced alarm time.
 *
 * Uses [SuspendingTileService] from Horologist to bridge Tile callbacks to coroutines.
 * Tiles are refreshed by the system every 30 minutes or when [requestUpdate] is called.
 *
 * Design: Dark background (#0A0E1A) with cyan accent (#26D0CE) alarm time.
 */
@OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)
class SyncStatusTile : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        val alarms    = WearAlarmReader(this).readNextAlarms()
        val nextAlarm = alarms.firstOrNull()
        val timeStr   = if (nextAlarm != null)
            "${nextAlarm.hour.toString().padStart(2,'0')}:${nextAlarm.minute.toString().padStart(2,'0')}"
        else
            "--:--"
        val labelStr  = nextAlarm?.label?.takeIf { it.isNotBlank() } ?: "Next Alarm"

        val layout = buildLayout(timeStr, labelStr)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build()
                    )
                    .build()
            )
            .setFreshnessIntervalMillis(30 * 60 * 1_000L)  // refresh every 30 min
            .build()
    }

    private fun buildLayout(timeStr: String, label: String): androidx.wear.protolayout.LayoutElementBuilders.Layout {
        val expandedDimension = androidx.wear.protolayout.DimensionBuilders.ExpandedDimensionProp.Builder().build()
        val wrappedDimension = androidx.wear.protolayout.DimensionBuilders.WrappedDimensionProp.Builder().build()

        return Layout.Builder()
            .setRoot(
                Box.Builder()
                    .setWidth(expandedDimension)
                    .setHeight(expandedDimension)
                    .setModifiers(
                        androidx.wear.protolayout.ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                androidx.wear.protolayout.ModifiersBuilders.Background.Builder()
                                    .setColor(argb(0xFF000000.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Column.Builder()
                            .setWidth(wrappedDimension)
                            .setHeight(wrappedDimension)
                            .addContent(
                                Text.Builder()
                                    .setText("⏰ $timeStr")
                                    .setFontStyle(
                                        androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(18f))
                                            .setColor(argb(0xFF26D0CE.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(label)
                                    .setFontStyle(
                                        androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(13f))
                                            .setColor(argb(0xFF8A95B0.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText("Alarm Sync")
                                    .setFontStyle(
                                        androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(11f))
                                            .setColor(argb(0xFF4F5565.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"

        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(SyncStatusTile::class.java)
        }
    }
}
