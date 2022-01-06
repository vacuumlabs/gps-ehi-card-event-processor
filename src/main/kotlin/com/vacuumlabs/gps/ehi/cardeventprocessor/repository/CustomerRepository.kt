package com.vacuumlabs.gps.ehi.cardeventprocessor.repository

import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Customer
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CustomerRepository : CrudRepository<Customer, Int> {

    fun findDistinctByAccountToken(accountToken: String): Optional<Customer>
}
