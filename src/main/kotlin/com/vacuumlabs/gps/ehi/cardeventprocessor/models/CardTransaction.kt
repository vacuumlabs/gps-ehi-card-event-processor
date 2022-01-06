package com.vacuumlabs.gps.ehi.cardeventprocessor.models

import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToOne

@Entity
data class CardTransaction(
    @Id
    @GeneratedValue
    val id: Int?,
    @Column(unique = true)
    val traceId: String,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val account: Account,
    var state: CardTransactionState = CardTransactionState.ACTIVE,
)

enum class CardTransactionState {
    ACTIVE,
    FINISHED,
    ABORTED,
}
