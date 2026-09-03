@file:OptIn(
    kotlin.io.encoding.ExperimentalEncodingApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class
)

package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
actual fun ImagePicker(
    onImageSelected: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    content {
        openBrowserImagePicker { dataUrl ->
            val bytes = dataUrl
                ?.substringAfter(',', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
                ?.let { encoded -> runCatching { Base64.Default.decode(encoded) }.getOrNull() }
            onImageSelected(bytes)
        }
    }
}

actual fun ByteArray.toImageBitmap(): ImageBitmap = decodeToImageBitmap()

/**
 * Opens the browser's native image chooser and normalizes the selected image before it is sent
 * through the game WebSocket. The payload stays below the server's 64 KiB frame limit even when
 * the original photo comes directly from a modern phone camera.
 */
private fun openBrowserImagePicker(onResult: (String?) -> Unit): Unit = js(
    """
    {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.style.display = 'none';
        document.body.appendChild(input);

        let completed = false;
        const finish = (value) => {
            if (completed) return;
            completed = true;
            input.remove();
            onResult(value);
        };

        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file || !file.type.startsWith('image/') || file.size > 20 * 1024 * 1024) {
                finish(null);
                return;
            }

            const objectUrl = URL.createObjectURL(file);
            const image = new Image();
            image.onload = () => {
                try {
                    let maxEdge = 512;
                    let quality = 0.82;
                    let dataUrl = null;
                    let estimatedBytes = Number.MAX_SAFE_INTEGER;

                    for (let attempt = 0; attempt < 8; attempt += 1) {
                        const scale = Math.min(1, maxEdge / Math.max(image.naturalWidth, image.naturalHeight));
                        const width = Math.max(1, Math.round(image.naturalWidth * scale));
                        const height = Math.max(1, Math.round(image.naturalHeight * scale));
                        const canvas = document.createElement('canvas');
                        canvas.width = width;
                        canvas.height = height;
                        const context = canvas.getContext('2d');
                        context.drawImage(image, 0, 0, width, height);

                        dataUrl = canvas.toDataURL('image/jpeg', quality);
                        const payload = dataUrl.substring(dataUrl.indexOf(',') + 1);
                        estimatedBytes = Math.floor(payload.length * 3 / 4);
                        if (estimatedBytes <= 42 * 1024) break;

                        if (quality > 0.5) {
                            quality -= 0.1;
                        } else {
                            maxEdge = Math.max(192, Math.floor(maxEdge * 0.78));
                        }
                    }

                    finish(estimatedBytes <= 45 * 1024 ? dataUrl : null);
                } catch (_) {
                    finish(null);
                } finally {
                    URL.revokeObjectURL(objectUrl);
                }
            };
            image.onerror = () => {
                URL.revokeObjectURL(objectUrl);
                finish(null);
            };
            image.src = objectUrl;
        };

        input.oncancel = () => {
            input.remove();
        };

        input.click();
    }
    """
)
