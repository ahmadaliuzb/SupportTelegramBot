package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class SupportTelegramBot(
    private val messageHandler: MessageHandler,
    private val callbackQueryHandler: CallbackQueryHandler,
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "https://t.me/testsuppoertbot"

    override fun getBotToken() = "6170321057:AAGRy6I61dmUBIQMi8JjOvP48eAnTNFnx1g"

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasCallbackQuery() -> callbackQueryHandler.handle(update.callbackQuery, this)
            update.hasMessage() -> messageHandler.handle(update.message, this)
        }

    }

    fun notificationOperator(dto: UserUpdateDto) {
        val languages = mutableListOf<Language>()
        for (languageDto in dto.languageList) {
            when (languageDto) {
                "UZ" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                "RU" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.RU))
                "ENG" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.ENG))
            }
        }


    }
}


