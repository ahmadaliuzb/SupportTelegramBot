package uz.zerone.supporttelegrambot

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.util.*

@Configuration
class WebMvcConfigure : WebMvcConfigurer {
    @Bean
    fun errorMessageSource() = ResourceBundleMessageSource().apply {
        setDefaultEncoding(Charsets.UTF_8.name())
        setDefaultLocale(Locale("uz"))
        setBasename("errors")
    }
}



@Component
class Components() {
    @Bean
    fun messageResourceBundleMessageSource(): ResourceBundleMessageSource? {
        val messageSource = ResourceBundleMessageSource()
        messageSource.setBasename("messages")
        messageSource.setCacheSeconds(3600)
        messageSource.setDefaultLocale(Locale.US)
        messageSource.setDefaultEncoding("UTF-8")
        return messageSource
    }

    @Bean
    fun defaultBotOptions(): DefaultBotOptions {
        val options = DefaultBotOptions()
        options.maxThreads = 10
        return options
    }
//
//    @Bean
//    fun telegramBotsApi(): TelegramBotsApi {
//        return TelegramBotsApi(DefaultBotSession::class.java)
//    }
//
//    @Bean
//    fun telegramBotInitializer(
//        telegramBotsApi: TelegramBotsApi,
//        longPollingBots: List<LongPollingBot>
//    ): TelegramBotInitializer {
//        return TelegramBotInitializer(telegramBotsApi, longPollingBots, emptyList())
//    }
}

