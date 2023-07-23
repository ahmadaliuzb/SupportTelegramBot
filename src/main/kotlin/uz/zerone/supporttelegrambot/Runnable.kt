package uz.zerone.supporttelegrambot

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component


/**
18/07/2023 - 11:06 AM
Created by Akhmadali
 */

@Component
class Runnable(private val languageRepository: LanguageRepository) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val language1 = Language(LanguageEnum.ENG)
        val language2 = Language(LanguageEnum.RU)
        val language3 = Language(LanguageEnum.UZ)
        languageRepository.save(language1)
        languageRepository.save(language2)
        languageRepository.save(language3)
    }
}