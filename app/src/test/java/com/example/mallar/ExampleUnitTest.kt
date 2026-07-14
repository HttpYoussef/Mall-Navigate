package com.example.mallar

import com.example.mallar.ui.chatbot.ChatInputParser
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun chatbotCategoryDetection_isCorrect() {
        // Dining category matches
        assertEquals("Dining", ChatInputParser.detectCategory("I want food"))
        assertEquals("Dining", ChatInputParser.detectCategory("I am hungry"))
        assertEquals("Dining", ChatInputParser.detectCategory("Recommend a restaurant"))
        assertEquals("Dining", ChatInputParser.detectCategory("عايز اكل"))
        assertEquals("Dining", ChatInputParser.detectCategory("مطعم"))

        // Fashion category matches
        assertEquals("Fashion", ChatInputParser.detectCategory("I need clothes"))
        assertEquals("Fashion", ChatInputParser.detectCategory("I want a jacket"))
        assertEquals("Fashion", ChatInputParser.detectCategory("I need shoes"))
        assertEquals("Fashion", ChatInputParser.detectCategory("ملابس"))

        // Perfumes & Cosmetics matches
        assertEquals("Perfumes& Cosmetics", ChatInputParser.detectCategory("I need perfume"))
        assertEquals("Perfumes& Cosmetics", ChatInputParser.detectCategory("عطور"))
        assertEquals("Perfumes& Cosmetics", ChatInputParser.detectCategory("beauty products"))

        // Jewellery matches
        assertEquals("Jewellery", ChatInputParser.detectCategory("I need a watch"))
        assertEquals("Jewellery", ChatInputParser.detectCategory("I want jewelry"))
        assertEquals("Jewellery", ChatInputParser.detectCategory("ساعة"))

        // Pharmacy matches
        assertEquals("Pharmacy", ChatInputParser.detectCategory("I need medicine"))
        assertEquals("Pharmacy", ChatInputParser.detectCategory("صيدلية"))

        // No match
        assertNull(ChatInputParser.detectCategory("hello assistant"))
    }

    @Test
    fun chatbotSingleDestinationParsing_isCorrect() {
        assertEquals("Starbucks", ChatInputParser.parseSingleDestination("Take me to Starbucks"))
        assertEquals("Zara", ChatInputParser.parseSingleDestination("go to Zara"))
        assertEquals("Zara", ChatInputParser.parseSingleDestination("navigate to Zara"))
        assertEquals("زارا", ChatInputParser.parseSingleDestination("عايز اروح لـ زارا"))
        assertEquals("زارا", ChatInputParser.parseSingleDestination("روح زارا"))
        assertEquals("Bershka", ChatInputParser.parseSingleDestination("Bershka"))
    }
}