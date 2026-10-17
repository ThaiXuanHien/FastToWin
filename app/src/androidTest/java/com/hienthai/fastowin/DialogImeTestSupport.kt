package com.hienthai.fastowin

import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.Closeable

/** Observe the native dialog window without replacing Compose's own insets listener. */
internal class DialogImeSynchronizer(field: SemanticsNodeInteraction) : Closeable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val composeView = (field.fetchSemanticsNode().root as ViewRootForTest).view
    private val decorView = composeView.rootView
    private val animations = mutableSetOf<WindowInsetsAnimationCompat>()

    init {
        check(decorView !== composeView)
        instrumentation.runOnMainSync {
            ViewCompat.setWindowInsetsAnimationCallback(decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animations += animation
                    }

                    override fun onProgress(insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                        animations.clear()
                        animations += runningAnimations.filter { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        animations -= animation
                    }
                })
        }
    }

    @Suppress("DEPRECATION") // ViewCompat also supports the app's API 29 minimum.
    fun show() {
        instrumentation.runOnMainSync { ViewCompat.getWindowInsetsController(composeView)?.show(WindowInsetsCompat.Type.ime()) }
    }

    @Suppress("DEPRECATION")
    fun hide() {
        instrumentation.runOnMainSync { ViewCompat.getWindowInsetsController(composeView)?.hide(WindowInsetsCompat.Type.ime()) }
    }

    // Compose's idle clock does not include the separate native IME animation.
    fun isSettled(visible: Boolean): Boolean {
        var settled = false
        instrumentation.runOnMainSync {
            val insets = ViewCompat.getRootWindowInsets(composeView)
            settled = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()) == visible && animations.isEmpty()
        }
        return settled
    }

    override fun close() {
        instrumentation.runOnMainSync { ViewCompat.setWindowInsetsAnimationCallback(decorView, null) }
    }
}

internal fun withDialogIme(label: String, field: SemanticsNodeInteraction,
    vararg actions: SemanticsNodeInteraction, block: (DialogImeSynchronizer) -> Unit) {
    DialogImeSynchronizer(field).use { ime ->
        try {
            block(ime)
        } catch (failure: Throwable) {
            runCatching { captureDialogFailure(label, field, *actions) }.onFailure(failure::addSuppressed)
            throw failure
        }
    }
}

internal fun SemanticsNodeInteraction.assertFullyVisibleAboveIme(): SemanticsNodeInteraction {
    assertIsDisplayed()
    val node = fetchSemanticsNode()
    var clipped = node.boundsInRoot
    var parent = node.parent
    while (parent != null) {
        clipped = clipped.intersect(parent.boundsInRoot)
        parent = parent.parent
    }
    assertTrue("Control must be fully inside the scroll viewport: clipped=$clipped size=${node.size}",
        clipped.height >= node.size.height - 1f && clipped.width >= node.size.width - 1f)
    var imeTop = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val view = (node.root as ViewRootForTest).view
        imeTop = view.rootView.height - (ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
    }
    assertTrue("Control must be above the keyboard: bounds=${node.boundsInWindow} imeTop=$imeTop",
        node.boundsInWindow.bottom <= imeTop + 1f)
    return this
}
