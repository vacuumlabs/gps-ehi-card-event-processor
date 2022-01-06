package com.vacuumlabs.gps.ehi.cardeventprocessor.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.vacuumlabs.gps.ehi.base.CardProcessingException
import org.hibernate.annotations.Columns
import org.hibernate.annotations.TypeDef
import org.javamoney.moneta.Money
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.money.CurrencyUnit
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity
data class Account(
    @Id
    @GeneratedValue
    val id: Int? = null,

    @Enumerated(EnumType.STRING)
    val type: AccountType,

    @Convert(converter = CurrencyUnitConverter::class)
    val currency: CurrencyUnit,

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    @JsonProperty("entries")
    private val mutableEntries: MutableList<AccountEntry> = mutableListOf(),
) {

    @get:JsonIgnore
    val entries: List<AccountEntry> get() = mutableEntries
    val balance: Money get() = Money.of(mutableEntries.sum { it.amount.numberStripped }, currency)

    fun addEntry(amount: Money, date: LocalDateTime, description: String) {
        if (amount.currency != currency) {
            throw CardProcessingException("Amount currency ${amount.currency} different from account currency $currency")
        }
        mutableEntries.add(AccountEntry(id = null, amount = amount, date = date, description = description))
    }
}

@Entity
@TypeDef(name = "Money", typeClass = MoneyUserType::class, defaultForType = Money::class)
data class AccountEntry(
    @Id
    @GeneratedValue
    val id: Int?,

    @Columns(columns = [ Column(name = "amount_currency", length = 3), Column(name = "amount_number")])
    val amount: Money,

    val date: LocalDateTime,

    private val description: String,
)

fun <T> Iterable<T>.sum(amountProvider: (T) -> BigDecimal): BigDecimal {
    return fold(BigDecimal.ZERO) { sum, item -> sum.plus(amountProvider(item)) }
}

enum class AccountType {
    BANK_ASSETS,
    BANK_REVENUES,
    CUSTOMER_AVAILABLE,
    CUSTOMER_CARD_TRANSACTION
}
