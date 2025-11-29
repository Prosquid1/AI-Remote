interface AICommandSender {
    suspend fun sendScript(applescript: String): Result<Unit>
}