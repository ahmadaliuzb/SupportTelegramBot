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
        val language1 = Language(LanguageEnum.RU)
        val language2 = Language(LanguageEnum.UZ)
        val language3 = Language(LanguageEnum.ENG)
        languageRepository.save(language1)
        languageRepository.save(language2)
        languageRepository.save(language3)
    }
}