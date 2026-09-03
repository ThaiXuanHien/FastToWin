@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberResultImageSharer(): ResultImageSharer = remember {
    ResultImageSharer { content ->
        runCatching {
            shareResultImage(
                result = content.result,
                playerName = content.playerName,
                playerScore = content.playerScore,
                opponentName = content.opponentName,
                opponentScore = content.opponentScore,
                gameMode = content.gameMode,
                matchType = content.matchType,
                duration = content.duration,
                accuracy = content.accuracy,
                elo = content.elo ?: "—",
                caption = content.caption,
            )
        }
    }
}

@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    TextSharer { text, title -> runCatching { shareOrCopyText(text, title) } }
}

private fun shareOrCopyText(text: String, title: String): Unit = js(
    """{
        if (navigator.share) {
            navigator.share({ title: title, text: text });
        } else if (navigator.clipboard) {
            navigator.clipboard.writeText(text);
        }
    }"""
)

private fun shareResultImage(
    result: String,
    playerName: String,
    playerScore: Int,
    opponentName: String,
    opponentScore: Int,
    gameMode: String,
    matchType: String,
    duration: String,
    accuracy: String,
    elo: String,
    caption: String,
): Unit = js(
    """{
        const canvas = document.createElement('canvas');
        canvas.width = 1080;
        canvas.height = 1350;
        const ctx = canvas.getContext('2d');
        if (!ctx) throw new Error('Canvas 2D is not supported');

        const roundedRect = (x, y, width, height, radius) => {
            const r = Math.min(radius, width / 2, height / 2);
            ctx.beginPath();
            ctx.moveTo(x + r, y);
            ctx.arcTo(x + width, y, x + width, y + height, r);
            ctx.arcTo(x + width, y + height, x, y + height, r);
            ctx.arcTo(x, y + height, x, y, r);
            ctx.arcTo(x, y, x + width, y, r);
            ctx.closePath();
        };
        const fillRoundedRect = (x, y, width, height, radius, fill) => {
            roundedRect(x, y, width, height, radius);
            ctx.fillStyle = fill;
            ctx.fill();
        };
        const fitText = (text, maxWidth, preferredSize, weight = 700) => {
            let size = preferredSize;
            do {
                ctx.font = String(weight) + ' ' + String(size) + 'px Arial, sans-serif';
                if (ctx.measureText(text).width <= maxWidth) return size;
                size -= 2;
            } while (size > 24);
            return size;
        };
        const drawCentered = (text, x, y, maxWidth, preferredSize, color, weight = 700) => {
            const size = fitText(text, maxWidth, preferredSize, weight);
            ctx.font = String(weight) + ' ' + String(size) + 'px Arial, sans-serif';
            ctx.fillStyle = color;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(text, x, y);
        };

        const background = ctx.createLinearGradient(0, 0, 1080, 1350);
        background.addColorStop(0, '#091226');
        background.addColorStop(0.52, '#16314C');
        background.addColorStop(1, '#0B1C30');
        ctx.fillStyle = background;
        ctx.fillRect(0, 0, 1080, 1350);

        const glow = ctx.createRadialGradient(540, 220, 20, 540, 220, 590);
        glow.addColorStop(0, 'rgba(53, 240, 199, 0.19)');
        glow.addColorStop(1, 'rgba(53, 240, 199, 0)');
        ctx.fillStyle = glow;
        ctx.fillRect(0, 0, 1080, 820);

        fillRoundedRect(80, 72, 920, 112, 56, '#35F0C7');
        drawCentered('FAST TO WIN', 540, 128, 800, 52, '#071A2D', 900);

        drawCentered(result, 540, 292, 900, 76, '#FFFFFF', 900);
        drawCentered(String(gameMode) + '  •  ' + String(matchType), 540, 374, 880, 34, '#AFC4D5', 600);

        fillRoundedRect(70, 430, 940, 390, 42, 'rgba(17, 37, 59, 0.88)');
        ctx.strokeStyle = 'rgba(53, 240, 199, 0.32)';
        ctx.lineWidth = 3;
        roundedRect(70, 430, 940, 390, 42);
        ctx.stroke();

        drawCentered(playerName, 280, 522, 350, 36, '#D8E7F2', 700);
        drawCentered(opponentName, 800, 522, 350, 36, '#D8E7F2', 700);
        drawCentered(String(playerScore), 280, 674, 330, 128, '#35F0C7', 900);
        drawCentered('—', 540, 674, 120, 62, '#70869A', 600);
        drawCentered(String(opponentScore), 800, 674, 330, 128, '#FFFFFF', 900);

        fillRoundedRect(70, 866, 940, 250, 42, 'rgba(9, 24, 42, 0.78)');
        const metrics = [
            { x: 230, label: 'THỜI GIAN', value: duration },
            { x: 540, label: 'CHÍNH XÁC', value: accuracy },
            { x: 850, label: 'ELO', value: elo }
        ];
        metrics.forEach(metric => {
            drawCentered(metric.label, metric.x, 940, 250, 25, '#7890A3', 700);
            drawCentered(metric.value, metric.x, 1022, 260, 47, '#FFFFFF', 800);
        });

        drawCentered(caption, 540, 1204, 900, 30, '#C9D9E5', 600);
        drawCentered('Tìm nhanh hơn. Thắng đậm hơn.', 540, 1270, 900, 32, '#35F0C7', 700);

        const dataUrl = canvas.toDataURL('image/png');
        const encoded = dataUrl.substring(dataUrl.indexOf(',') + 1);
        const binary = atob(encoded);
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) {
            bytes[index] = binary.charCodeAt(index);
        }
        const blob = new Blob([bytes], { type: 'image/png' });
        const fileName = 'fast-to-win-result-' + Date.now() + '.png';
        const download = () => {
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = fileName;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        };

        if (typeof File !== 'undefined' && navigator.share && navigator.canShare) {
            const file = new File([blob], fileName, { type: 'image/png' });
            const shareData = {
                title: 'Kết quả Fast To Win',
                text: caption,
                files: [file]
            };
            if (navigator.canShare(shareData)) {
                navigator.share(shareData).catch(error => {
                    if (!error || error.name !== 'AbortError') download();
                });
                return;
            }
        }
        download();
    }"""
)
