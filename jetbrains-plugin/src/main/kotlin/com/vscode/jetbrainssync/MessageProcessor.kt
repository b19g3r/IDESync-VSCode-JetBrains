package com.vscode.jetbrainssync

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 消息处理器
 * 负责WebSocket消息的序列化、反序列化和路由处理
 */
class MessageProcessor(
    private val fileOperationHandler: FileOperationHandler,
    private val localIdentifierManager: LocalIdentifierManager,
    private val project: com.intellij.openapi.project.Project
) {
    private val log: Logger = Logger.getInstance(MessageProcessor::class.java)
    private val gson = Gson()

    private val messageTimeOutMs = 5000

    // 组播消息去重相关
    private val receivedMessages = mutableMapOf<String, Long>()
    private val maxReceivedMessagesSize = 1000
    private val messageCleanupIntervalMs = 300000 // 5分钟

    // 定时清理相关
    private val isShutdown = AtomicBoolean(false)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        startMessageCleanupTask()
    }

    /**
     * 启动消息清理定时任务
     */
    private fun startMessageCleanupTask() {
        executorService.submit {
            while (!isShutdown.get()) {
                try {
                    Thread.sleep(60000) // 每分钟清理一次
                    cleanupOldMessages()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    log.warn("清理消息时发生错误: ${e.message}", e)
                }
            }
        }
    }

    /**
     * 处理组播消息
     * 包含消息解析、去重检查、自己消息过滤等逻辑
     */
    fun handleMessage(message: String): Boolean {
        try {
            val messageData = parseMessageData(message)
            if (messageData == null) return false

            // 获取本地标识符
            val localIdentifier = localIdentifierManager.identifier

            // 检查是否是自己发送的消息
            if (messageData.isOwnMessage(localIdentifier)) {
                log.debug("忽略自己发送的消息")
                return false
            }
            log.info("收到组播消息: $message")

            // 检查消息去重
            if (isDuplicateMessage(messageData)) {
                log.debug("忽略重复消息: ${messageData.messageId}")
                return false
            }

            // 记录消息并处理
            recordMessage(messageData)
            // 处理消息内容
            handleIncomingState(messageData.payload)
            return true
        } catch (e: Exception) {
            log.warn("处理组播消息时发生错误: ${e.message}", e)
            return false
        }
    }

    /**
     * 解析消息数据
     */
    private fun parseMessageData(message: String): MessageWrapper? {
        return MessageWrapper.fromJsonString(message)
    }

    /**
     * 检查是否是重复消息
     */
    private fun isDuplicateMessage(messageData: MessageWrapper): Boolean {
        return receivedMessages.containsKey(messageData.messageId)
    }

    /**
     * 记录消息ID
     */
    private fun recordMessage(messageData: MessageWrapper) {
        val currentTime = System.currentTimeMillis()
        receivedMessages[messageData.messageId] = currentTime

        if (receivedMessages.size > maxReceivedMessagesSize) {
            cleanupOldMessages()
        }
    }

    /**
     * 清理过期的消息记录
     */
    private fun cleanupOldMessages() {
        val currentTime = System.currentTimeMillis()
        val expireTime = currentTime - messageCleanupIntervalMs

        val iterator = receivedMessages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < expireTime) {
                iterator.remove()
            }
        }

        log.debug("清理过期消息记录，剩余: ${receivedMessages.size}")
    }

    /**
     * 处理接收到的消息（兼容旧接口）
     */
    fun handleIncomingMessage(message: String) {
        try {
            log.info("收到消息: $message")
            val state = gson.fromJson(message, EditorState::class.java)
            log.info("\uD83C\uDF55解析消息: ${state.action} ${state.filePath}，${state.getCursorLog()}，${state.getSelectionLog()}")

            // 验证消息有效性
            if (!isValidMessage(state)) {
                return
            }

            // 路由到文件操作处理器
            fileOperationHandler.handleIncomingState(state)

        } catch (e: Exception) {
            log.warn("解析消息失败: ${e.message}", e)
        }
    }

    /**
     * 处理接收到的状态（新接口）
     */
    private fun handleIncomingState(state: EditorState) {
        try {
            log.info("\uD83C\uDF55解析消息: ${state.action} ${state.filePath}，${state.getCursorLog()}，${state.getSelectionLog()}")

            // 验证消息有效性
            if (!isValidMessage(state)) {
                return
            }

            // 路由到文件操作处理器
            fileOperationHandler.handleIncomingState(state)

        } catch (e: Exception) {
            log.warn("处理状态失败: ${e.message}", e)
        }
    }

    /**
     * 验证消息有效性
     */
    private fun isValidMessage(state: EditorState): Boolean {
//        // 忽略来自自己的消息
//        if (state.source == SourceType.JETBRAINS) {
//            return false
//        }

        // 只处理来自活跃IDE的消息
        if (!state.isActive) {
            log.info("忽略来自非活跃VSCode的消息")
            return false
        }

        // 检查消息时效性
        val messageTime = parseTimestamp(state.timestamp)
        val currentTime = System.currentTimeMillis()
        if (currentTime - messageTime > messageTimeOutMs) { // 5秒过期
            log.info("忽略过期消息，时间差: ${currentTime - messageTime}ms")
            return false
        }

        // 检查文件是否在当前项目内
        if (!isFileInCurrentProject(state)) {
            return false
        }

        return true
    }

    /**
     * 检查消息中的文件是否在当前项目内
     * @param state 编辑器状态
     * @return 如果文件在当前项目内返回 true，否则返回 false
     */
    private fun isFileInCurrentProject(state: EditorState): Boolean {
        val filePath = state.filePath

        // 空文件路径（如 WORKSPACE_SYNC 无活跃编辑器）不做过滤
        if (filePath.isEmpty()) {
            return true
        }

        // 获取兼容的文件路径
        val compatiblePath = state.getCompatiblePath()

        // 检查文件是否在当前项目内
        if (!WorkspaceUtils.isFileInProject(project, compatiblePath)) {
            log.debug("忽略非当前项目的文件: $compatiblePath")
            return false
        }

        return true
    }
}
