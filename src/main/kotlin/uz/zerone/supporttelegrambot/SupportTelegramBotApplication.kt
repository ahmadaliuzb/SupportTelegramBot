package uz.zerone.supporttelegrambot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@SpringBootApplication
class SupportTelegramBotApplication

fun main(args: Array<String>) {
    runApplication<SupportTelegramBotApplication>(*args)
    //
    val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
    telegramBotsApi.registerBot(SupportTelegramBot())
}
