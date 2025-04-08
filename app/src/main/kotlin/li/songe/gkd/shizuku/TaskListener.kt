package li.songe.gkd.shizuku

import android.app.ITaskStackListener
import android.os.Parcel


class TaskListener(private val onStackChanged: () -> Unit) : ITaskStackListener.Stub() {

    public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
        return try {
            super.onTransact(code, data, reply, flags)
        } catch (_: Throwable) {
            true
        }
    }

    override fun onTaskStackChanged() = onStackChanged()
}