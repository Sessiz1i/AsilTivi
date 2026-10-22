package com.omer.belestiviclone

import com.omer.belestiviclone.shared.preview.R38CAdmission
import com.omer.belestiviclone.shared.preview.R38CProfileTransition

object R38CEntry {
    const val action = "com.omer.belestiviclone.R38C_CAS"
    const val activity = "com.omer.belestiviclone.R38CSyntheticActivity"
    fun allowed(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?,
        database: String?, key: Int, className: String, restored: Boolean): Boolean =
        !restored && candidate == "R20-C013" && action == this.action && command == "suite" && className == activity &&
            session == "toplu-r38c-20261016-c013-main" && database != null &&
            R38CProfileTransition.allowedSynthetic(R38CAdmission(debug, key, database, session, "apply", R38CProfileTransition.fixtureManifest, candidate=candidate))
}
