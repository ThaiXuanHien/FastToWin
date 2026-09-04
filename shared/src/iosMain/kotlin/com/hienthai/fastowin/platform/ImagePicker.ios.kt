@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val AVATAR_MAX_EDGE = 512.0
private const val AVATAR_TARGET_BYTES = 42 * 1024
private const val AVATAR_MAX_BYTES = 45 * 1024

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    val currentCallback = rememberUpdatedState(onImageSelected)
    val coordinator = remember {
        IosImagePickerCoordinator { bytes ->
            currentCallback.value(bytes)
        }
    }

    DisposableEffect(coordinator) {
        onDispose(coordinator::dispose)
    }

    content(coordinator::present)
}

private class IosImagePickerCoordinator(
    private val onImageSelected: (ByteArray?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var activePicker: PHPickerViewController? = null
    private var disposed = false

    fun present() {
        if (disposed || activePicker != null) return

        val presenter = topViewController(
            UIApplication.sharedApplication.keyWindow?.rootViewController
        ) ?: run {
            onImageSelected(null)
            return
        }

        val picker = PHPickerViewController(
            configuration = PHPickerConfiguration().apply {
                selectionLimit = 1
                filter = PHPickerFilter.imagesFilter
            }
        ).apply {
            delegate = this@IosImagePickerCoordinator
        }

        activePicker = picker
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        activePicker = null
        picker.delegate = null
        picker.dismissViewControllerAnimated(true, completion = null)

        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            deliver(null)
            return
        }

        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val avatarBytes = data?.let(::prepareAvatar)
            deliver(avatarBytes)
        }
    }

    fun dispose() {
        disposed = true
        activePicker?.apply {
            delegate = null
            dismissViewControllerAnimated(false, completion = null)
        }
        activePicker = null
    }

    private fun deliver(bytes: ByteArray?) {
        dispatch_async(dispatch_get_main_queue()) {
            if (!disposed) onImageSelected(bytes)
        }
    }
}

private fun prepareAvatar(data: NSData): ByteArray? {
    val source = UIImage(data = data) ?: return null
    var maxEdge = AVATAR_MAX_EDGE
    var quality = 0.82
    var candidate: ByteArray? = null

    repeat(9) {
        val resized = resizeImage(source, maxEdge)
        candidate = UIImageJPEGRepresentation(resized, quality)?.toByteArray()
        val size = candidate?.size ?: return null
        if (size <= AVATAR_TARGET_BYTES) return candidate

        if (quality > 0.48) {
            quality -= 0.1
        } else {
            maxEdge *= 0.82
        }
    }

    return candidate?.takeIf { it.size <= AVATAR_MAX_BYTES }
}

private fun resizeImage(image: UIImage, maxEdge: Double): UIImage {
    val (width, height) = image.size.useContents { width to height }
    val longestEdge = maxOf(width, height)
    if (longestEdge <= maxEdge) return image

    val ratio = maxEdge / longestEdge
    val targetWidth = maxOf(1.0, width * ratio)
    val targetHeight = maxOf(1.0, height * ratio)
    return UIGraphicsImageRenderer(
        size = CGSizeMake(targetWidth, targetHeight)
    ).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }
}

private fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return ByteArray(0)
    return bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: ByteArray(0)
}

private tailrec fun topViewController(controller: UIViewController?): UIViewController? {
    val presented = controller?.presentedViewController ?: return controller
    return topViewController(presented)
}
