package com.vacuumlabs.gps.ehi.cardeventprocessor.repository

import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransaction
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransactionState
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CardTransactionRepository : CrudRepository<CardTransaction, Int> {

    fun findDistinctByTraceId(traceId: String): Optional<CardTransaction>

    fun findByTraceId(traceId: String): Iterable<CardTransaction>

    fun findDistinctByTraceIdAndState(traceId: String, state: CardTransactionState): Optional<CardTransaction>

    fun findByState(state: CardTransactionState): Iterable<CardTransaction>
}
