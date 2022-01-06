package com.vacuumlabs.gps.ehi.cardeventprocessor.config

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import javax.money.CurrencyUnit
import javax.money.Monetary

@Configuration
class CurrencyConverterConfig {
    @Bean
    @ConfigurationPropertiesBinding
    fun currencyConverter(): CurrencyConverter = CurrencyConverter()

    class CurrencyConverter : Converter<String, CurrencyUnit> {
        override fun convert(source: String): CurrencyUnit = Monetary.getCurrency(source)
    }
}