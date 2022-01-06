package com.vacuumlabs.gps.ehi.cardeventprocessor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.vacuumlabs.gps.ehi.base.CardProcessingException
import com.vacuumlabs.gps.ehi.base.UnexpectedCardEventException
import com.vacuumlabs.gps.ehi.base.events.AsyncCardEvent
import com.vacuumlabs.gps.ehi.base.events.AuthorizationAdvice
import com.vacuumlabs.gps.ehi.base.events.AuthorizationRequest
import com.vacuumlabs.gps.ehi.base.events.AuthorizationReversal
import com.vacuumlabs.gps.ehi.base.events.CardEvent
import com.vacuumlabs.gps.ehi.base.events.Fees
import com.vacuumlabs.gps.ehi.base.events.PresentmentNotification
import com.vacuumlabs.gps.ehi.base.events.PresentmentReversal
import com.vacuumlabs.gps.ehi.cardeventprocessor.config.CardEventProcessorConfig
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransactionState
import com.vacuumlabs.gps.ehi.base.utils.plus
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Consumer

@Service
class CardEventProcessor(
    private val cardTransactionManager: CardTransactionManager,
    private val streamBridge: StreamBridge,
    private val ledgerService: LedgerService,
    private val accountManager: AccountManager,
    private val objectMapper: ObjectMapper,
    private val configuration: CardEventProcessorConfig
) : Consumer<AsyncCardEvent> {

    private val logger = LoggerFactory.getLogger(javaClass)!!

    @Transactional
    fun processAsynchronously(cardEvent: AsyncCardEvent) {
        if (configuration.queueEnabled) {
            logger.debug("Queueing card event ${cardEvent.javaClass.simpleName}: ${cardEvent.toJson()}")
            streamBridge.send(configuration.queueName, cardEvent)
        } else {
            logger.debug("Skipping queue for card event ${cardEvent.javaClass.simpleName}: ${cardEvent.toJson()}")
            accept(cardEvent)
        }
    }

    @Transactional
    override fun accept(cardEvent: AsyncCardEvent) {
        logger.debug("Processing card event ${cardEvent.javaClass.simpleName}: ${cardEvent.toJson()}")
        //.sendMessage("Processing card event ${cardEvent.javaClass.simpleName} with traceId: ${cardEvent.traceId}")
        when (cardEvent) {
            is AuthorizationReversal -> process(cardEvent)
            is PresentmentNotification -> process(cardEvent)
            is PresentmentReversal -> process(cardEvent)
            is AuthorizationAdvice -> process(cardEvent)
        }
    }

    @Transactional
    fun process(event: AuthorizationRequest): Result {
        logger.debug("Processing card event ${event.javaClass.simpleName}: ${event.toJson()}")
        //slackNotificationService.sendMessage("Processing card event ${event.javaClass.simpleName} with traceId: ${event.traceId}")

        // ordering: we must not have an inactive transaction with the event's traceId
        val customer = accountManager.getCustomer(event.token)
        val cardTransaction = cardTransactionManager.getOrCreateActiveTransaction(customer, event.traceId)

        // for refund authorizations a credit check is not required, and the customer account should only be credited when the tx is settled
        if (event.amount.isNegative) {
            val debitAmount = event.amount.negate().applyTransactionFees(event.fees)

            val availableBalance = customer.availableAccount.balance
            if (debitAmount > availableBalance) {
                cardTransactionManager.abortTransaction(cardTransaction)
                throw NotEnoughAvailableBalance(amount = debitAmount, balance = availableBalance)
            }

            ledgerService.blockAmount(
                from = customer.availableAccount,
                txAccount = cardTransaction.account,
                amount = debitAmount,
                date = event.date,
                description = "Authorization Request: ${event.traceId}",
            )
        }
        return Result(customer.availableAccount.balance)
    }

    fun process(event: AuthorizationAdvice) {
        val customer = accountManager.getCustomer(event.token)
        val debitAmount = event.amount.negate().applyTransactionFees(event.fees)
        val description = "Authorization Request: ${event.traceId}"

        when (event.status) {
            AuthorizationAdvice.Status.APPROVED -> {
                val cardTransaction = cardTransactionManager.getOrCreateActiveTransaction(customer, event.traceId)
                ledgerService.blockAmount(
                    from = customer.availableAccount,
                    txAccount = cardTransaction.account,
                    amount = debitAmount,
                    date = event.date,
                    description = description,
                )
            }
            AuthorizationAdvice.Status.DECLINED_BY_ADVICE -> {
                val cardTransaction = cardTransactionManager.getOrCreateActiveTransaction(customer, event.traceId)
                if (cardTransaction.account.balance.isPositive) {
                    // this is *only* the case, if the transaction was created in this step!
                    ledgerService.unblockAmount(
                        to = customer.availableAccount,
                        txAccount = cardTransaction.account,
                        amount = debitAmount,
                        date = event.date,
                        description = description,
                    )
                }
                cardTransactionManager.abortTransaction(cardTransaction)
            }
            AuthorizationAdvice.Status.DECLINED_BY_REQUEST -> {
                val cardTransaction = cardTransactionManager.getExistingTransaction(event.traceId)
                when (cardTransaction.state) {
                    CardTransactionState.ACTIVE -> {
                        ledgerService.unblockAmount(
                            to = customer.availableAccount,
                            txAccount = cardTransaction.account,
                            amount = debitAmount,
                            date = event.date,
                            description = description,
                        )
                        cardTransactionManager.abortTransaction(cardTransaction)
                    }
                    CardTransactionState.ABORTED -> {
                        logger.debug(
                            "Discarding auth advice by retried auth request, as transaction with traceId=${cardTransaction.traceId} is already aborted."
                        )
                    }
                    CardTransactionState.FINISHED -> {
                        throw UnexpectedCardEventException("Auth request abortion received for an already finished transaction=$cardTransaction")
                    }
                }
            }
        }
    }

    fun process(event: AuthorizationReversal) {
        val customer = accountManager.getCustomer(event.token)
        val debitAmount = event.amount.applyTransactionFees(event.fees)
        val cardTransaction = cardTransactionManager.getExistingTransaction(event.traceId)

        when (cardTransaction.state) {
            CardTransactionState.ACTIVE -> {
                ledgerService.unblockAmount(
                    to = customer.availableAccount,
                    txAccount = cardTransaction.account,
                    amount = debitAmount,
                    date = event.date,
                    description = "Authorization Reversal: ${event.traceId}",
                )
                if (cardTransaction.account.balance.isZero) {
                    cardTransactionManager.abortTransaction(cardTransaction)
                }
            }
            CardTransactionState.ABORTED -> {
                // a previous auth advice already informed us about the transaction's abortion
                logger.debug("Discarding auth reversal, as transaction with traceId=${cardTransaction.traceId} is already aborted.")
            }
            CardTransactionState.FINISHED -> {
                throw UnexpectedCardEventException("Auth reversal received for an already finished transaction=$cardTransaction")
            }
        }
    }

    fun process(event: PresentmentNotification) {
        val customer = accountManager.getCustomer(event.token)
        val debitAmount = event.amount.negate().applyTransactionFees(event.fees)
        val cardTransaction = cardTransactionManager.getOrCreateActiveTransaction(customer, event.traceId)
        val description = "Presentment Notification: ${event.traceId}"

        if (cardTransaction.account.balance >= debitAmount) {
            logger.trace("Card accounting: there was sufficient authorization")
            ledgerService.sendAmount(
                from = cardTransaction.account,
                amount = debitAmount,
                date = event.date,
                description = description,
            )
        } else if (cardTransaction.account.balance.isPositive) {
            logger.trace("Card accounting: there was insufficient authorization")
            ledgerService.sendAmount(
                from = customer.availableAccount,
                amount = debitAmount.subtract(cardTransaction.account.balance),
                date = event.date,
                description = description,
            )
            ledgerService.sendAmount(
                from = cardTransaction.account,
                amount = cardTransaction.account.balance,
                date = event.date,
                description = description,
            )
        } else {
            logger.trace("Card accounting: there was no previous authorization")
            ledgerService.sendAmount(
                from = customer.availableAccount,
                amount = debitAmount,
                date = event.date,
                description = description,
            )
        }

        ledgerService.settleInterchangeFees(event.fees.interchange, event.date, description = description)

        if (event.isFinal) {
            if (cardTransaction.account.balance.isPositive) {
                logger.trace("Card accounting: unblocking remaining balance")
                ledgerService.unblockAmount(
                    to = customer.availableAccount,
                    txAccount = cardTransaction.account,
                    amount = cardTransaction.account.balance,
                    date = event.date,
                    description = description,
                )
            }
            cardTransactionManager.finishTransaction(cardTransaction)
        }
    }

    fun process(event: PresentmentReversal) {
        val customer = accountManager.getCustomer(event.token)
        val creditAmount = event.amount.applyTransactionFees(event.fees)
        cardTransactionManager.getFinishedTransaction(event.traceId) // operation required to assure ordering
        val description = "Presentment Reversal: ${event.traceId}"

        ledgerService.receiveAmount(
            to = customer.availableAccount,
            amount = creditAmount,
            date = event.date,
            description = description,
        )
        ledgerService.settleInterchangeFees(
            event.fees.interchange,
            date = event.date,
            description = description,
        )
    }

    private fun Money.applyTransactionFees(fees: Fees): Money {
        return this + fees.fixed + fees.fxPad + fees.rate + fees.mccPad
    }

    private fun CardEvent.toJson(): String = objectMapper.writeValueAsString(this)
}

data class Result(
    val balance: Money
)

class NotEnoughAvailableBalance(
    val amount: Money,
    val balance: Money,
    message: String = "Not enough available balance: amount (incl. fees) = $amount, balance = $balance"
) : CardProcessingException(message)
