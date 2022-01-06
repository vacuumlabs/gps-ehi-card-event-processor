package com.vacuumlabs.gps.ehi.cardeventprocessor.service

import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Account
import org.javamoney.moneta.Money
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class LedgerService(
    private val accountManager: AccountManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)!!

    @Transactional
    fun blockAmount(from: Account, txAccount: Account, amount: Money, date: LocalDateTime, description: String) {
        logger.debug("Blocking amount=$amount, debiting accountId=${from.id} and crediting accountId=${txAccount.id}")
        bookDoubleEntry(
            debit = from,
            credit = txAccount,
            amount = amount,
            date = date,
            description = "Block amount for $description",
        )
    }

    @Transactional
    fun unblockAmount(to: Account, txAccount: Account, amount: Money, date: LocalDateTime, description: String) {
        logger.debug("Unlocking amount=$amount, crediting accountId=${to.id} and debiting accountId=${txAccount.id}")
        bookDoubleEntry(
            debit = txAccount,
            credit = to,
            amount = amount,
            date = date,
            description = "Unblock amount for $description",
        )
    }

    @Transactional
    fun sendAmount(from: Account, amount: Money, date: LocalDateTime, description: String) {
        logger.debug("Sending amount=$amount from account=${from.id}")
        bookDoubleEntry(
            debit = from,
            credit = accountManager.getBankAssetsAccount(),
            amount = amount,
            date = date,
            description = "Send amount for $description",
        )
    }

    @Transactional
    fun receiveAmount(to: Account, amount: Money, date: LocalDateTime, description: String) {
        logger.debug("Receiving amount=$amount on accountId=${to.id}")
        bookDoubleEntry(
            debit = accountManager.getBankAssetsAccount(),
            credit = to,
            amount = amount,
            date = date,
            description = "Receive amount for $description",
        )
    }

    @Transactional
    fun settleInterchangeFees(amount: Money?, date: LocalDateTime, description: String) {
        when {
            amount == null -> {} // Nothing
            amount.isPositive -> {
                bookDoubleEntry(
                    debit = accountManager.getBankAssetsAccount(),
                    credit = accountManager.getBankRevenuesAccount(),
                    amount = amount,
                    date = date,
                    description = "Settle interchange fees for $description",
                )
            }
            amount.isNegative -> {
                bookDoubleEntry(
                    debit = accountManager.getBankRevenuesAccount(),
                    credit = accountManager.getBankAssetsAccount(),
                    amount = amount.negate(),
                    date = date,
                    description = "Settle interchange fees for $description",
                )
            }
        }
    }

    private fun bookDoubleEntry(debit: Account, credit: Account, amount: Money, date: LocalDateTime, description: String) {
        logger.trace("Booking double entry amount=$amount on accounts debit=${debit.id}, credit=${credit.id} ")
        debit.addEntry(amount.negate(), date, description)
        credit.addEntry(amount, date, description)
    }
}
