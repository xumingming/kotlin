/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class LibraryModificationTracker(project: Project) : SimpleModificationTracker() {
    companion object {
        @JvmStatic
        fun getInstance(project: Project) = ServiceManager.getService(project, LibraryModificationTracker::class.java)!!
    }

    init {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
            override fun after(events: List<VFileEvent>) {
                events.filterIsInstance<VFileCreateEvent>().let { createEvents ->
                    if (createEvents.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            processBulk(createEvents)
                        }
                    }
                }
            }

            override fun before(events: List<VFileEvent>) {
                processBulk(events)
            }

            private fun processBulk(events: List<VFileEvent>) {
                events.forEach { e ->
                    if (processEvent(e)) {
                        return
                    }
                }
            }
        })
    }

    private val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)

    fun processEvent(event: VFileEvent): Boolean {
        val file = event.file
        if (file != null && (projectFileIndex.isInLibraryClasses(file) || isLibraryJarRoot(file))) {
            incModificationCount()
            return true
        }
        return false
    }

    private fun isLibraryJarRoot(virtualFile: VirtualFile): Boolean {
        if (virtualFile.extension != "jar") return false

        val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile) ?: return false
        return projectFileIndex.isInLibraryClasses(jarRoot)
    }
}

