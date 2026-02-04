import * as vscode from 'vscode';
import * as path from 'path';

/**
 * 工作区工具类
 * 提供工作区路径相关的工具方法
 */
export class WorkspaceUtils {

    /**
     * 获取当前所有工作区文件夹路径
     * @returns 工作区路径数组
     */
    static getWorkspacePaths(): string[] {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders || workspaceFolders.length === 0) {
            return [];
        }
        return workspaceFolders.map(folder => folder.uri.fsPath);
    }

    /**
     * 检查文件是否在当前工作区内
     * @param filePath 文件路径
     * @returns 如果文件在工作区内返回 true，否则返回 false
     */
    static isFileInWorkspace(filePath: string): boolean {
        const workspacePaths = this.getWorkspacePaths();
        return this.isFileInPaths(filePath, workspacePaths);
    }

    /**
     * 检查文件是否在指定的路径列表中
     * @param filePath 文件路径
     * @param paths 路径列表
     * @returns 如果文件在路径列表中的任一路径下返回 true，否则返回 false
     */
    static isFileInPaths(filePath: string, paths: string[]): boolean {
        if (!filePath || !paths || paths.length === 0) {
            return false;
        }

        // 规范化文件路径
        const normalizedFilePath = this.normalizePath(filePath);

        for (const workspacePath of paths) {
            const normalizedWorkspacePath = this.normalizePath(workspacePath);

            // 检查文件路径是否以工作区路径开头
            if (normalizedFilePath.startsWith(normalizedWorkspacePath + path.sep) ||
                normalizedFilePath === normalizedWorkspacePath) {
                return true;
            }
        }

        return false;
    }

    /**
     * 规范化路径，处理跨平台差异
     * @param inputPath 输入路径
     * @returns 规范化后的路径
     */
    private static normalizePath(inputPath: string): string {
        // 统一使用正斜杠
        let normalized = inputPath.replace(/\\/g, '/');

        // 移除尾部斜杠
        if (normalized.endsWith('/') && normalized.length > 1) {
            normalized = normalized.slice(0, -1);
        }

        // 在 Windows 上，将盘符转为小写以便比较
        if (process.platform === 'win32' && /^[A-Z]:/.test(normalized)) {
            normalized = normalized[0].toLowerCase() + normalized.substring(1);
        }

        return normalized;
    }
}
