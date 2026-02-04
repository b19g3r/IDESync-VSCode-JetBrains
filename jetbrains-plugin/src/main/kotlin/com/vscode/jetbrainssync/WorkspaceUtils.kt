package com.vscode.jetbrainssync

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

/**
 * 工作区工具类
 * 提供工作区路径相关的工具方法
 */
object WorkspaceUtils {

    /**
     * 获取项目的所有工作区路径
     * 包括项目根路径和所有模块内容根
     * @param project 当前项目
     * @return 工作区路径列表
     */
    fun getWorkspacePaths(project: Project): List<String> {
        val paths = mutableSetOf<String>()

        // 添加项目根路径
        project.basePath?.let { paths.add(it) }

        // 添加所有模块内容根
        val projectRootManager = ProjectRootManager.getInstance(project)
        projectRootManager.contentRoots.forEach { root ->
            paths.add(root.path)
        }

        return paths.toList()
    }

    /**
     * 检查文件是否在当前项目内
     * @param project 当前项目
     * @param filePath 文件路径
     * @return 如果文件在项目内返回 true，否则返回 false
     */
    fun isFileInProject(project: Project, filePath: String): Boolean {
        val workspacePaths = getWorkspacePaths(project)
        return isFileInPaths(filePath, workspacePaths)
    }

    /**
     * 检查文件是否在指定的路径列表中
     * @param filePath 文件路径
     * @param paths 路径列表
     * @return 如果文件在路径列表中的任一路径下返回 true，否则返回 false
     */
    fun isFileInPaths(filePath: String, paths: List<String>): Boolean {
        if (filePath.isEmpty() || paths.isEmpty()) {
            return false
        }

        // 规范化文件路径
        val normalizedFilePath = normalizePath(filePath)

        for (workspacePath in paths) {
            val normalizedWorkspacePath = normalizePath(workspacePath)

            // 检查文件路径是否以工作区路径开头
            if (normalizedFilePath.startsWith(normalizedWorkspacePath + File.separator) ||
                normalizedFilePath == normalizedWorkspacePath) {
                return true
            }
        }

        return false
    }

    /**
     * 规范化路径，处理跨平台差异
     * @param inputPath 输入路径
     * @return 规范化后的路径
     */
    private fun normalizePath(inputPath: String): String {
        // 统一使用正斜杠
        var normalized = inputPath.replace('\\', '/')

        // 移除尾部斜杠
        if (normalized.endsWith('/') && normalized.length > 1) {
            normalized = normalized.dropLast(1)
        }

        // 在 Windows 上，将盘符转为小写以便比较
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (isWindows && normalized.matches(Regex("^[A-Z]:.*"))) {
            normalized = normalized[0].lowercaseChar() + normalized.substring(1)
        }

        return normalized
    }
}
