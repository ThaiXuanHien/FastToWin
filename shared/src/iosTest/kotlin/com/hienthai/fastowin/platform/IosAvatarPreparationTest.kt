@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hienthai.fastowin.platform

import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IosAvatarPreparationTest {
    @Test
    fun largePhotoProducesPreviewSizedJpegOnRetinaScreens() {
        val source = UIGraphicsImageRenderer(
            size = CGSizeMake(1600.0, 1200.0),
            format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
        ).imageWithActions { _ ->
            UIColor.whiteColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, 1600.0, 1200.0))
            UIColor.blackColor.setFill()
            UIRectFill(CGRectMake(400.0, 300.0, 800.0, 600.0))
        }
        val bytes = assertNotNull(prepareAvatar(assertNotNull(UIImagePNGRepresentation(source))))
        val preview = bytes.toImageBitmap()

        assertEquals(512 to 384, preview.width to preview.height)
    }
}
