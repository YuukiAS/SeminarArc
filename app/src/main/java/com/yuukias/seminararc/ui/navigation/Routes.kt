package com.yuukias.seminararc.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SeminarLibraryRoute

@Serializable
data class SeminarEditorRoute(val seminarId: Long? = null)

@Serializable
data class SeminarDetailRoute(val seminarId: Long)

@Serializable
data class ActiveSessionRoute(val seminarId: Long)

@Serializable
data class SeminarTimelineRoute(val seminarId: Long)

@Serializable
data class ReconstructionWorkspaceRoute(val seminarId: Long)
