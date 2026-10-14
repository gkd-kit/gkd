package li.gkd.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Matches gkd-android/src/main/res/drawable/ic_status.xml.
val TileStatus: ImageVector
    get() {
        if (_tileStatus != null) {
            return _tileStatus!!
        }
        _tileStatus = ImageVector.Builder(
            name = "TileStatus",
            defaultWidth = 64.dp,
            defaultHeight = 64.dp,
            viewportWidth = 64f,
            viewportHeight = 64f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M19.39,60.75C19.33375,57.65125 19.64125,54.5425 19.49125,51.45625C19.29125,47.36 18.78625,43.27375 18.29875,39.1975C18.1525,37.96875 18.26625,37.16875 19.5675,36.685C26.5125,34.1025 33.5225,33.77875 40.6475,35.87875C41.7825,36.2125 42.12875,36.70375 41.95875,37.94375C41.64625,40.22875 41.5525,42.55 41.4825,44.86C41.32625,50.05 41.24,55.24125 41.12125,60.59125C33.92875,60.75 26.74,60.75 19.39,60.75Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M44.5,60.75C44.575,52.98875 44.8,45.2275 45.04,37C54.68625,42.045 59.48125,50.315 62,60.61875C56.22375,60.75 50.4375,60.75 44.5,60.75Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M2,60.75C2.9075,55.22625 4.63,49.99375 7.96875,45.72125C9.85625,43.3075 12.16625,41.315 14.30875,39.16125C14.63375,38.835 15.08125,38.665 15.75,38.25C17.34,45.755 17.11375,53.08875 16.68875,60.59375C11.84,60.75 6.9925,60.75 2,60.75Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M20.85,19.49875l10.5825,-10.63l10.5825,10.63l-10.5825,10.63z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M15.75,13.85625l10.60625,-10.60625l3.52,3.55125l-10.60625,10.60625z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M36.77,3.25l10.60625,10.60625l-3.52,3.55125l-10.60625,-10.60625z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileStatus!!
    }

private var _tileStatus: ImageVector? = null

// Matches gkd-android/src/main/res/drawable/ic_capture.xml.
val TileCapture: ImageVector
    get() {
        if (_tileCapture != null) {
            return _tileCapture!!
        }
        _tileCapture = ImageVector.Builder(
            name = "TileCapture",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M128,384a42.7,42.7 0,0 0,42.7 -42.7L170.7,213.3a42.7,42.7 0,0 1,42.7 -42.7h128a42.7,42.7 0,0 0,0 -85.3L213.3,85.3a128,128 0,0 0,-128 128v128a42.7,42.7 0,0 0,42.7 42.7zM341.3,853.3L213.3,853.3a42.7,42.7 0,0 1,-42.7 -42.7v-128a42.7,42.7 0,0 0,-85.3 0v128a128,128 0,0 0,128 128h128a42.7,42.7 0,0 0,0 -85.3zM512,341.3a170.7,170.7 0,1 0,170.7 170.7,170.7 170.7,0 0,0 -170.7,-170.7zM512,597.3a85.3,85.3 0,1 1,85.3 -85.3,85.3 85.3,0 0,1 -85.3,85.3zM810.7,85.3h-128a42.7,42.7 0,0 0,0 85.3h128a42.7,42.7 0,0 1,42.7 42.7v128a42.7,42.7 0,0 0,85.3 0L938.7,213.3a128,128 0,0 0,-128 -128zM896,640a42.7,42.7 0,0 0,-42.7 42.7v128a42.7,42.7 0,0 1,-42.7 42.7h-128a42.7,42.7 0,0 0,0 85.3h128a128,128 0,0 0,128 -128v-128a42.7,42.7 0,0 0,-42.7 -42.7z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileCapture!!
    }

private var _tileCapture: ImageVector? = null

// Matches gkd-android/src/main/res/drawable/ic_http.xml.
val TileHttp: ImageVector
    get() {
        if (_tileHttp != null) {
            return _tileHttp!!
        }
        _tileHttp = ImageVector.Builder(
            name = "TileHttp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M40,600v-240h60v80h80v-80h60v240h-60v-100h-80v100L40,600ZM340,600v-180h-60v-60h180v60h-60v180h-60ZM560,600v-180h-60v-60h180v60h-60v180h-60ZM720,600v-240h140q24,0 42,18t18,42v40q0,24 -18,42t-42,18h-80v80h-60ZM780,460h80v-40h-80v40Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileHttp!!
    }

private var _tileHttp: ImageVector? = null

// Matches gkd-android/src/main/res/drawable/ic_radio_button.xml.
val TileButton: ImageVector
    get() {
        if (_tileButton != null) {
            return _tileButton!!
        }
        _tileButton = ImageVector.Builder(
            name = "TileButton",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M480,680q83,0 141.5,-58.5T680,480q0,-83 -58.5,-141.5T480,280q-83,0 -141.5,58.5T280,480q0,83 58.5,141.5T480,680ZM480,880q-83,0 -156,-31.5T197,763q-54,-54 -85.5,-127T80,480q0,-83 31.5,-156T197,197q54,-54 127,-85.5T480,80q83,0 156,31.5T763,197q54,54 85.5,127T880,480q0,83 -31.5,156T763,763q-54,54 -127,85.5T480,880ZM480,800q134,0 227,-93t93,-227q0,-134 -93,-227t-227,-93q-134,0 -227,93t-93,227q0,134 93,227t227,93ZM480,480Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileButton!!
    }

private var _tileButton: ImageVector? = null

// Matches gkd-android/src/main/res/drawable/ic_layers.xml.
val TileActivity: ImageVector
    get() {
        if (_tileActivity != null) {
            return _tileActivity!!
        }
        _tileActivity = ImageVector.Builder(
            name = "TileActivity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M480,842 L120,562l66,-50 294,228 294,-228 66,50 -360,280ZM480,640L120,360l360,-280 360,280 -360,280ZM480,360ZM480,538 L710,360 480,182 250,360 480,538Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileActivity!!
    }

private var _tileActivity: ImageVector? = null

// Matches gkd-android/src/main/res/drawable/ic_event_list.xml.
val TileEvent: ImageVector
    get() {
        if (_tileEvent != null) {
            return _tileEvent!!
        }
        _tileEvent = ImageVector.Builder(
            name = "TileEvent",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M640,840q-33,0 -56.5,-23.5T560,760v-160q0,-33 23.5,-56.5T640,520h160q33,0 56.5,23.5T880,600v160q0,33 -23.5,56.5T800,840L640,840ZM640,760h160v-160L640,600v160ZM80,720v-80h360v80L80,720ZM640,440q-33,0 -56.5,-23.5T560,360v-160q0,-33 23.5,-56.5T640,120h160q33,0 56.5,23.5T880,200v160q0,33 -23.5,56.5T800,440L640,440ZM640,360h160v-160L640,200v160ZM80,320v-80h360v80L80,320ZM720,680ZM720,280Z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return _tileEvent!!
    }

private var _tileEvent: ImageVector? = null
