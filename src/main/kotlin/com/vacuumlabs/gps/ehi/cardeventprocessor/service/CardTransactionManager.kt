package com.vacuumlabs.gps.ehi.cardeventprocessor.service

import com.vacuumlabs.gps.ehi.base.UnexpectedCardEventException
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.AccountType
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Customer
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransaction
import com.vacuumlabs.gps.ehi.cardeventprocessor.repository.CardTransactionRepository
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransactionState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CardTransactionManager(
    private val cardTransactionRepository: CardTransactionRepository,
    private val accountManager: AccountManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)!!

    @Transactional
    fun getAllTransactions(): Iterable<CardTransaction> = cardTransactionRepository.findAll()

    @Transactional
    fun getExistingTransaction(traceId: String): CardTransaction {
        val tx = cardTransactionRepository.findDistinctByTraceId(traceId)
        return when {
            tx.isPresent -> tx.get()
            else -> throw UnexpectedCardEventException("No existing transaction for traceId=$traceId")
        }
    }

    @Transactional
    fun getActiveTransaction(traceId: String): CardTransaction {
        val tx = cardTransactionRepository.findDistinctByTraceIdAndState(traceId, CardTransactionState.ACTIVE)
        return when {
            tx.isPresent -> tx.get()
            else -> throw UnexpectedCardEventException("No active transaction for traceId=$traceId")
        }
    }

    @Transactional
    fun getFinishedTransaction(traceId: String): CardTransaction {
        val tx = cardTransactionRepository.findDistinctByTraceIdAndState(traceId, CardTransactionState.FINISHED)
        return when {
            tx.isPresent -> tx.get()
            else -> throw UnexpectedCardEventException("No finished transaction for traceId=$traceId")
        }
    }

    @Transactional
    fun getOrCreateActiveTransaction(customer: Customer, traceId: String): CardTransaction {
        val tx = cardTransactionRepository.findDistinctByTraceId(traceId)
        return when {
            tx.isEmpty -> {
                logger.debug("Creating new transaction for traceId=$traceId")
                createTransaction(customer, traceId)
            }
            tx.isPresent && (tx.get().state == CardTransactionState.ACTIVE) -> {
                logger.debug("Found existing ACTIVE transaction=${tx.map { it.id to it.state }}")
                tx.get()
            }
            else -> {
                throw UnexpectedCardEventException(
                    "ACTIVE transaction required, but transaction with different state already exists=${tx.map { it.id to it.state }}"
                )
            }
        }
    }

    private fun createTransaction(customer: Customer, traceId: String): CardTransaction {
        val account = accountManager.createAccount(AccountType.CUSTOMER_CARD_TRANSACTION)
        return cardTransactionRepository
            .save(CardTransaction(null, traceId, account))
            .also { customer.cardTransactions.add(it) }
    }

    fun finishTransaction(cardTransaction: CardTransaction) {
        logger.debug("Marking transaction=$cardTransaction as finished")
        if (!cardTransaction.account.balance.isZero) {
            logger.warn("Finishing transaction=$cardTransaction where account balance is not Zero!")
        }
        cardTransaction.state = CardTransactionState.FINISHED
    }

    fun abortTransaction(cardTransaction: CardTransaction) {
        logger.debug("Marking transaction=$cardTransaction as aborted")
        if (!cardTransaction.account.balance.isZero) {
            logger.warn("Aborting transaction=$cardTransaction where account balance is not Zero!")
        }
        cardTransaction.state = CardTransactionState.ABORTED
    }
}
