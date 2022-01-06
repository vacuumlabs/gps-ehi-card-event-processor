package com.vacuumlabs.gps.ehi.cardeventprocessor.models

import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.BigDecimalType
import org.hibernate.type.StringType
import org.hibernate.usertype.CompositeUserType
import org.javamoney.moneta.Money
import java.io.Serializable
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Objects
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount
import javax.persistence.AttributeConverter
import javax.persistence.Converter

@Converter
class CurrencyUnitConverter : AttributeConverter<CurrencyUnit, String> {
    override fun convertToDatabaseColumn(attribute: CurrencyUnit?) = attribute?.currencyCode
    override fun convertToEntityAttribute(dbData: String?) = dbData?.let { Monetary.getCurrency(it) }
}

// see also:
// https://www.baeldung.com/hibernate-custom-types
// https://github.com/JavaMoney/jsr354-ri/issues/185
class MoneyUserType : CompositeUserType {

    // ORDER IS IMPORTANT!  it must match the order the columns are defined in the property mapping
    override fun getPropertyNames() = arrayOf("currency", "amount")
    override fun getPropertyTypes() = arrayOf(StringType.INSTANCE, BigDecimalType.INSTANCE)
    override fun returnedClass(): Class<*> = Money::class.java

    override fun getPropertyValue(component: Any?, propertyIndex: Int): Any? {
        if (component == null) {
            return null
        }
        val money = component as Money
        return when (propertyIndex) {
            0 -> money.currency.currencyCode
            1 -> money.number.numberValue(BigDecimal::class.java)
            else -> throw HibernateException("Invalid property index [$propertyIndex]")
        }
    }

    override fun setPropertyValue(component: Any?, propertyIndex: Int, value: Any?) {
        throw HibernateException("Called setPropertyValue on an immutable type {" + component?.javaClass + "}")
    }

    @Throws(SQLException::class)
    override fun nullSafeGet(resultSet: ResultSet, names: Array<String>, session: SharedSessionContractImplementor, `object`: Any?): Any? {
        assert(names.size == 2)

        // owner here is of type TestUser or the actual owning Object
        var money: Money? = null
        val currency = resultSet.getString(names[0])
        // Deferred check after first read
        if (!resultSet.wasNull()) {
            val amount = resultSet.getBigDecimal(names[1])
            money = Money.of(amount, currency)
        }
        return money
    }

    @Throws(SQLException::class)
    override fun nullSafeSet(preparedStatement: PreparedStatement, value: Any?, property: Int, session: SharedSessionContractImplementor) {
        if (null == value) {
            preparedStatement.setNull(property, StringType.INSTANCE.sqlType())
            preparedStatement.setNull(property + 1, BigDecimalType.INSTANCE.sqlType())
        } else {
            val amount = value as Money
            preparedStatement.setString(property, amount.currency.currencyCode)
            preparedStatement.setBigDecimal(property + 1, amount.number.numberValue(BigDecimal::class.java))
        }
    }

    /** Used while dirty checking - control passed on to the [MonetaryAmount] */
    override fun equals(o1: Any?, o2: Any?) = Objects.equals(o1, o2)
    override fun hashCode(value: Any) = value.hashCode()
    override fun isMutable() = false

    /** for snapshots (immutable) */
    override fun deepCopy(value: Any?) = value
    /** for merging (immutable) */
    override fun replace(original: Any?, target: Any?, paramSessionImplementor: SharedSessionContractImplementor, owner: Any?) = original

    /** for L2 cache */
    override fun disassemble(value: Any?, paramSessionImplementor: SharedSessionContractImplementor) = value as Serializable
    override fun assemble(cached: Serializable, sessionImplementor: SharedSessionContractImplementor, owner: Any?) = cached
}
