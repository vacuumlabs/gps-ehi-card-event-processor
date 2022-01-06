package com.vacuumlabs.gps.ehi.cardeventprocessor.ledger


import com.vacuumlabs.gps.ehi.cardeventprocessor.service.CardTransactionManager
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Account
import com.vacuumlabs.gps.ehi.cardeventprocessor.repository.AccountRepository
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.CardTransaction
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Customer
import com.vacuumlabs.gps.ehi.cardeventprocessor.repository.CustomerRepository
import com.vacuumlabs.gps.ehi.cardeventprocessor.service.LedgerService
import org.javamoney.moneta.Money
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.Optional

@RestController
class LedgerApi(
    private val ledgerService: LedgerService,
    private val customerRepository: CustomerRepository,
    private val cardTransactionManager: CardTransactionManager,
    private val accountRepository: AccountRepository,
) {
    @GetMapping("/customers")
    fun getCustomers(): Iterable<Customer> {
        try {
            return customerRepository.findAll()
        } catch(e: Throwable) {
            throw e
        }
    }

    @GetMapping("/customers/{customerId}")
    fun getCustomer(@PathVariable customerId: Int): Optional<Customer> {
        return customerRepository.findById(customerId)
    }

    @GetMapping("/customers/{customerId}/availableAccount")
    fun getCustomerAvailableAccount(@PathVariable customerId: Int): Optional<Account> {
        return customerRepository.findById(customerId).map { it.availableAccount }
    }

    @GetMapping("/customers/{customerId}/availableAccount/balance")
    fun getCustomerAvailableAccountBalance(@PathVariable customerId: Int): Optional<Money> {
        return customerRepository.findById(customerId).map { it.availableAccount.balance }
    }

    @PostMapping("/customers/{customerId}/availableAccount/balance")
    fun depositMoney(@PathVariable customerId: Int, @RequestBody payload: DepositPayload) {
        customerRepository.findById(customerId).ifPresent {
            ledgerService.receiveAmount(it.availableAccount, payload.amount, payload.date, "Deposit")
        }
    }

    @GetMapping("/customers/{customerId}/cardTransactions")
    fun getCustomerCardTransactions(@PathVariable customerId: Int): Optional<List<CardTransaction>> {
        return customerRepository.findById(customerId).map { it.cardTransactions }
    }

    @GetMapping("/cardTransactions")
    fun getTransactions(): Iterable<CardTransaction> {
        return cardTransactionManager.getAllTransactions()
    }

    @GetMapping("/accounts")
    fun getAccounts(): Iterable<Account> {
        return accountRepository.findAll()
    }

    @GetMapping("/accounts/{accountId}")
    fun getAccount(@PathVariable accountId: Int): Optional<Account> {
        return accountRepository.findById(accountId)
    }
}

data class DepositPayload(val amount: Money, val date: LocalDateTime)
