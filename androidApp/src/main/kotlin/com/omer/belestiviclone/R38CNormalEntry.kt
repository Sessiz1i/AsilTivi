package com.omer.belestiviclone

import com.omer.belestiviclone.shared.preview.R38CAdmission
import com.omer.belestiviclone.shared.preview.R38CProfileTransition

object R38CNormalEntry {
    const val action = "com.omer.belestiviclone.R38C_NORMAL"
    const val activity = "com.omer.belestiviclone.R38CNormalActivity"
    fun allowed(admission: R38CAdmission, action: String?, className: String, restored: Boolean, exists: Boolean) =
        !restored && exists && action == this.action && className == activity && R38CProfileTransition.allowed(admission)
}
