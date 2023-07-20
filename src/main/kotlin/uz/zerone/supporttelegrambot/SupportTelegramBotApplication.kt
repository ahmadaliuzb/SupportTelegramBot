package uz.zerone.supporttelegrambot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories(repositoryBaseClass = BaseRepositoryImpl::class)

class SupportTelegramBotApplication

fun main(args: Array<String>) {
    runApplication<SupportTelegramBotApplication>(*args)
//    val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
//    telegramBotsApi.registerBot(SupportTelegramBot(messageService = MessageService()))
}
