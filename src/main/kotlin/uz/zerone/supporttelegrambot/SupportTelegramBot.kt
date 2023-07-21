package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Location
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class SupportTelegramBot(
    private val messageHandler: MessageHandler,
    private val callbackQueryHandler: CallbackQueryHandler,
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "https://t.me/firstkotlinbot"

    override fun getBotToken() = "6300162247:AAEV4HccaFlyrsE-OmOaIuxijkV98saBnko"

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasCallbackQuery() -> callbackQueryHandler.handle(update.callbackQuery, this)
            update.hasMessage() -> messageHandler.handle(update.message, this)
        }

        if (update.hasMessage() && update.message.hasLocation()) {
            val location: Location = update.message.location
            val latitude: Double = location.latitude
            val longitude: Double = location.longitude

            // Save the latitude and longitude to your database here
            saveLocationToDatabase(update.message.chatId, latitude, longitude)
        }


    }
    private fun saveLocationToDatabase(chatId: Long, latitude: Double, longitude: Double) {
        // Implement your code to save the location to your database here
        // You can use your preferred database library or ORM framework for this task
    }
}


