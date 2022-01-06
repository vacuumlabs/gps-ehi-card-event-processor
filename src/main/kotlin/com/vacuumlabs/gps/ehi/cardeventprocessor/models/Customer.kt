package com.vacuumlabs.gps.ehi.cardeventprocessor.models

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.OneToOne

@Entity
data class Customer(
    @Id
    @GeneratedValue
    val id: Int? = null,

    val name: String,

    @Column(unique = true)
    val accountToken: String,

    @JsonIgnore
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val availableAccount: Account,

    @JsonIgnore
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    val cardTransactions: MutableList<CardTransaction> = mutableListOf()
)
