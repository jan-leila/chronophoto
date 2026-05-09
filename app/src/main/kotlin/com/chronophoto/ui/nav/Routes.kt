package com.chronophoto.ui.nav

object Routes {
    const val PROJECTS = "projects"
    const val NEW_PROJECT = "projects/new"

    private const val PROJECT_DETAIL_BASE = "projects"
    const val PROJECT_DETAIL_ARG = "projectId"
    const val PROJECT_DETAIL_ROUTE = "$PROJECT_DETAIL_BASE/{$PROJECT_DETAIL_ARG}"

    fun projectDetail(id: Long) = "$PROJECT_DETAIL_BASE/$id"
}
