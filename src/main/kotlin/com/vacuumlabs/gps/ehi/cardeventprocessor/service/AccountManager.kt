package com.vacuumlabs.gps.ehi.cardeventprocessor.service

import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Account
import com.vacuumlabs.gps.ehi.cardeventprocessor.repository.AccountRepository
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.AccountType
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Customer
import com.vacuumlabs.gps.ehi.cardeventprocessor.repository.CustomerRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.money.CurrencyUnit

@Service
class AccountManager(
    private val accountRepository: AccountRepository,
    private val customerRepository: CustomerRepository,
    @Value("\${defaultCurrency:GBP}") val defaultCurrency: CurrencyUnit
) {
    @get:Transactional
    val allAccounts = accountRepository.findAll()

    @Transactional
    fun getBankAssetsAccount(): Account {
        return accountRepository
            .findDistinctByType(AccountType.BANK_ASSETS)
            .orElseThrow { NoSuchElementException("Bank asset account not found") }
    }

    @Transactional
    fun getBankRevenuesAccount(): Account {
        return accountRepository
            .findDistinctByType(AccountType.BANK_REVENUES)
            .orElseThrow { NoSuchElementException("Bank revenues account not found") }
    }

    @Transactional
    fun getCustomer(token: String): Customer {
        return customerRepository
            .findDistinctByAccountToken(token)
            .orElseThrow { NoSuchElementException("No customer assigned to account token $token") }
    }

    @Transactional
    fun createCustomer(name: String, accountToken: String) = customerRepository.save(
        Customer(
            name = name,
            accountToken = accountToken,
            availableAccount = Account(type = AccountType.CUSTOMER_AVAILABLE, currency = defaultCurrency)
        )
    )

    @Transactional
    fun createAccount(type: AccountType): Account {
        return accountRepository.save(Account(type = type, currency = defaultCurrency))
    }
}
