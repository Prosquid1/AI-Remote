package com.example.airemote.ai

import AICommandSender

object ServiceLocator {
    lateinit var router: HybridRouter
    lateinit var scriptSender: AICommandSender
}