package com.vacuumlabs.gps.ehi.cardeventprocessor.repository

import com.vacuumlabs.gps.ehi.cardeventprocessor.models.Account
import com.vacuumlabs.gps.ehi.cardeventprocessor.models.AccountType
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AccountRepository : CrudRepository<Account, Int> {

    fun findDistinctByType(type: AccountType): Optional<Account>
}
