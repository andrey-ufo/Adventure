package com.myapp.adventure.projects

/** C-facing seam: the manager/editor never talk to SAF directly. */
interface ProjectRepository {
    suspend fun list(): List<ProjectRef>
    suspend fun create(title: String): ProjectRef?
    suspend fun duplicate(project: ProjectRef): ProjectRef?
    suspend fun delete(project: ProjectRef): Boolean
}
