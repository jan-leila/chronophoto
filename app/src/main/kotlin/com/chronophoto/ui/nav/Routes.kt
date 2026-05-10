package com.chronophoto.ui.nav

object Routes {
    const val PROJECTS = "projects"
    const val NEW_PROJECT = "projects/new"

    private const val PROJECT_DETAIL_BASE = "projects"
    const val PROJECT_DETAIL_ARG = "projectId"
    const val PROJECT_DETAIL_ROUTE = "$PROJECT_DETAIL_BASE/{$PROJECT_DETAIL_ARG}"

    fun projectDetail(id: Long) = "$PROJECT_DETAIL_BASE/$id"

    const val CAPTURE_ARG = "projectId"
    const val CAPTURE_ROUTE = "$PROJECT_DETAIL_BASE/{$CAPTURE_ARG}/capture"
    fun capture(projectId: Long) = "$PROJECT_DETAIL_BASE/$projectId/capture"

    const val PHOTO_DETAIL_ARG = "photoId"
    const val PHOTO_DETAIL_ROUTE = "photos/{$PHOTO_DETAIL_ARG}"
    fun photoDetail(id: Long) = "photos/$id"

    const val PROJECT_EDIT_ARG = "projectId"
    const val PROJECT_EDIT_ROUTE = "$PROJECT_DETAIL_BASE/{$PROJECT_EDIT_ARG}/edit"
    fun projectEdit(id: Long) = "$PROJECT_DETAIL_BASE/$id/edit"
}
