package com.yuukias.seminararc

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class SeminarArcTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, SeminarArcApp::class.java.name, context)
    }
}
