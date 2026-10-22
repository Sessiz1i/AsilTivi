package com.omer.belestiviclone

import com.omer.belestiviclone.shared.preview.R38BAdmission
import com.omer.belestiviclone.shared.preview.R38BProfileTransition

object R38BEntry {
    const val action = "com.omer.belestiviclone.R38B_CAS"
    const val activity = "com.omer.belestiviclone.R38BSyntheticActivity"
    fun allowed(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?,
        database: String?, key: Int, className: String, restored: Boolean): Boolean =
        !restored && candidate == "R20-C013" && action == this.action && command == "suite" && className == activity &&
            session == "toplu-r38b-20261016-c013-main" && database != null &&
            R38BProfileTransition.allowed(R38BAdmission(debug, key, database, session, "apply"))
}
