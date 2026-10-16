package com.hienthai.fastowin

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidImagePickerTest {
    @Test
    fun transparentPngIsFlattenedOnWhiteBeforeJpegUpload() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.TRANSPARENT)
        // A large translucent patch also catches silently discarding alpha.
        for (x in 16 until 48) for (y in 16 until 48) {
            source.setPixel(x, y, Color.argb(128, 255, 0, 0))
        }
        val output = pickImage(source, Bitmap.CompressFormat.PNG)
        try {
            val corner = output.getPixel(0, 0)
            assertTrue("Transparent pixels must become white, got $corner",
                Color.red(corner) >= 245 && Color.green(corner) >= 245 && Color.blue(corner) >= 245)
            val patch = output.getPixel(32, 32)
            assertTrue("Translucent red must blend on white, got $patch",
                Color.red(patch) >= 245 && Color.green(patch) in 115..140 && Color.blue(patch) in 115..140)
        } finally {
            output.recycle()
        }
    }

    @Test
    fun opaqueJpegKeepsItsColorAndIsScaledWithinUploadLimit() {
        val source = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(30, 90, 180))
        val output = pickImage(source, Bitmap.CompressFormat.JPEG)
        try {
            assertTrue(output.width <= 512 && output.height <= 512)
            val center = output.getPixel(output.width / 2, output.height / 2)
            assertTrue("Opaque image color must be preserved",
                Color.red(center) in 25..35 && Color.green(center) in 85..95 && Color.blue(center) in 175..185)
        } finally {
            output.recycle()
        }
    }

    private fun pickImage(source: Bitmap, format: Bitmap.CompressFormat): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("avatar-picker-", ".image", instrumentation.targetContext.cacheDir)
        try {
            file.outputStream().use { assertTrue(source.compress(format, 90, it)) }
        } finally {
            source.recycle()
        }
        try {
            // Exercise the private Android URI pipeline without adding a test-only public API.
            // The real system picker is covered by physical device acceptance.
            val prepare = Class.forName("com.hienthai.fastowin.platform.ImagePicker_androidKt")
                .getDeclaredMethod("prepareAndroidAvatar", ContentResolver::class.java, Uri::class.java)
                .apply { isAccessible = true }
            val bytes = checkNotNull(prepare.invoke(null,
                instrumentation.targetContext.contentResolver, Uri.fromFile(file)) as ByteArray?)
            assertTrue("JPEG payload must fit the avatar upload limit", bytes.size <= 45 * 1024)
            assertTrue("Picker must return JPEG bytes", bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(decoded)
            return checkNotNull(decoded)
        } finally {
            file.delete()
        }
    }
}
