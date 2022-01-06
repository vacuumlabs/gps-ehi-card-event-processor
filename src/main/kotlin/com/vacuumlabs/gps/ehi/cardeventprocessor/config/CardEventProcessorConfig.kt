package com.vacuumlabs.gps.ehi.cardeventprocessor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "cardeventprocessor", ignoreUnknownFields = true)
class CardEventProcessorConfig(
    val queueEnabled: Boolean,
    val queueName: String
)