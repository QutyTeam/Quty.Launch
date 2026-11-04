// *** core/utilities/DialogBlurHelper.kt *** //
package by.quty.launch.core.utilities

import android.app.Dialog
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.Window

/**
 * Glassmorphism-размытие фона под диалогом.
 *
 * Android 12+ (API 31): размывает root-вью активности через RenderEffect.
 * Android 10–11: размытие недоступно — стеклянный эффект даёт сам фон
 * диалога (bg_dialog_solid / bg_glass_dialog), поэтому здесь ничего не делаем.
 *
 * Единая точка, чтобы логика не дублировалась в активностях и фрагментах.
 */
object DialogBlurHelper {

    /** Радиус размытия фона (px). */
    private const val BLUR_RADIUS = 20f

    /**
     * Размывает фон активности на время показа [dialog] и снимает размытие
     * при его закрытии. [extraOnDismiss] вызывается после снятия размытия —
     * сюда передают собственную dismiss-логику, чтобы не перезатирать listener.
     *
     * Безопасно для < API 31: размытие пропускается, но [extraOnDismiss]
     * всё равно подключается.
     */
    @JvmStatic
    @JvmOverloads
    fun apply(
        dialog: Dialog,
        window: Window?,
        extraOnDismiss: (() -> Unit)? = null
    ) {
        val root = window?.decorView?.rootView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && root != null) {
            val effect = RenderEffect.createBlurEffect(
                BLUR_RADIUS,
                BLUR_RADIUS,
                Shader.TileMode.CLAMP
            )
            root.setRenderEffect(effect)
        }

        dialog.setOnDismissListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                root?.setRenderEffect(null)
            }
            extraOnDismiss?.invoke()
        }
    }

    /**
     * То же, но принимает [androidx.appcompat.app.AlertDialog] — удобнее из
     * фрагментов, где диалог собирается через AlertDialog.Builder.
     */
    @JvmOverloads
    fun apply(
        dialog: androidx.appcompat.app.AlertDialog,
        window: Window?,
        extraOnDismiss: (() -> Unit)? = null
    ) {
        apply(dialog as Dialog, window, extraOnDismiss)
    }
}
