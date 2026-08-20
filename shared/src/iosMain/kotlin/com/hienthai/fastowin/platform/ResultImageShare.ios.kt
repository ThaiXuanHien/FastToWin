package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberResultImageSharer(): ResultImageSharer = remember { IosResultImageSharer() }

@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    TextSharer { text, _ ->
        runCatching {
            presentShareSheet(listOf(text))
        }
    }
}

private class IosResultImageSharer : ResultImageSharer {
    override fun share(content: ResultShareContent): Result<Unit> = runCatching {
        val image = renderResultImage(content)
        presentShareSheet(listOf(image, content.caption))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun presentShareSheet(items: List<Any>) {
    val shareSheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    val presenter = topViewController(
        UIApplication.sharedApplication.keyWindow?.rootViewController
    ) ?: return
    shareSheet.popoverPresentationController?.apply {
        sourceView = presenter.view
        sourceRect = presenter.view.bounds
    }
    presenter.presentViewController(shareSheet, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun renderResultImage(content: ResultShareContent) =
    UIGraphicsImageRenderer(
        size = CGSizeMake(1080.0, 1350.0),
        format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            scale = 1.0
            opaque = true
        }
    ).imageWithActions { _ ->
        val card = resultCard(content)
        card.drawViewHierarchyInRect(card.bounds, afterScreenUpdates = true)
    }

@OptIn(ExperimentalForeignApi::class)
private fun resultCard(content: ResultShareContent): UIView {
    val card = UIView(frame = CGRectMake(0.0, 0.0, 1080.0, 1350.0)).apply {
        backgroundColor = color(9, 24, 43)
    }
    card.addSubview(label("FAST TO WIN", 80.0, 72.0, 920.0, 112.0, 46.0, color(7, 31, 36), true).apply {
        backgroundColor = color(41, 211, 158)
        layer.cornerRadius = 56.0
        clipsToBounds = true
    })
    card.addSubview(label(content.result, 70.0, 220.0, 940.0, 100.0, 70.0, UIColor.whiteColor, true))
    card.addSubview(label("${content.gameMode} • ${content.matchType}", 70.0, 330.0, 940.0, 54.0, 34.0, color(182, 208, 224)))

    card.addSubview(UIView(frame = CGRectMake(70.0, 430.0, 940.0, 390.0)).apply {
        backgroundColor = color(18, 38, 61)
        layer.cornerRadius = 42.0
    })
    card.addSubview(label(content.playerName, 110.0, 470.0, 340.0, 70.0, 42.0, UIColor.whiteColor, true, fit = true))
    card.addSubview(label(content.opponentName, 630.0, 470.0, 340.0, 70.0, 42.0, UIColor.whiteColor, true, fit = true))
    card.addSubview(label(content.playerScore.toString(), 110.0, 570.0, 340.0, 150.0, 128.0, color(41, 211, 158), true))
    card.addSubview(label(content.opponentScore.toString(), 630.0, 570.0, 340.0, 150.0, 128.0, UIColor.whiteColor, true))
    card.addSubview(label("–", 480.0, 590.0, 120.0, 100.0, 76.0, color(132, 155, 174), true))

    card.addSubview(UIView(frame = CGRectMake(70.0, 865.0, 940.0, 250.0)).apply {
        backgroundColor = color(28, 54, 80)
        layer.cornerRadius = 42.0
    })
    addMetric(card, "THỜI GIAN", content.duration, 75.0)
    addMetric(card, "CHÍNH XÁC", content.accuracy, 385.0)
    addMetric(card, "ELO", content.elo ?: "—", 695.0)

    card.addSubview(label(content.caption, 90.0, 1160.0, 900.0, 72.0, 27.0, color(182, 208, 224), fit = true))
    card.addSubview(label("Nhanh mắt • Nhanh tay • Chiến thắng", 90.0, 1240.0, 900.0, 48.0, 28.0, color(41, 211, 158), true))
    return card
}

@OptIn(ExperimentalForeignApi::class)
private fun addMetric(card: UIView, title: String, value: String, x: Double) {
    card.addSubview(label(title, x, 910.0, 310.0, 45.0, 26.0, color(148, 174, 193), true))
    card.addSubview(label(value, x, 975.0, 310.0, 75.0, 48.0, UIColor.whiteColor, true, fit = true))
}

@OptIn(ExperimentalForeignApi::class)
private fun label(
    text: String,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    size: Double,
    color: UIColor,
    bold: Boolean = false,
    fit: Boolean = false
) = UILabel(frame = CGRectMake(x, y, width, height)).apply {
    this.text = text
    textColor = color
    textAlignment = NSTextAlignmentCenter
    font = if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
    adjustsFontSizeToFitWidth = fit
    minimumScaleFactor = 0.55
}

private fun color(red: Int, green: Int, blue: Int) = UIColor.colorWithRed(
    red = red / 255.0,
    green = green / 255.0,
    blue = blue / 255.0,
    alpha = 1.0
)

private tailrec fun topViewController(controller: UIViewController?): UIViewController? {
    val presented = controller?.presentedViewController ?: return controller
    return topViewController(presented)
}
