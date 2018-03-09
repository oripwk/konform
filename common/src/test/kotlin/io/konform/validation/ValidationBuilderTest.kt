package io.konform.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationBuilderTest {

    // Some example constraints for Testing
    fun ValidationBuilder<String>.minLength(minValue: Int) =
        addConstraint("must have at least {1} characters", minValue.toString()) { it.length >= minValue }

    fun ValidationBuilder<String>.maxLength(minValue: Int) =
        addConstraint("must have at most {1} characters", minValue.toString()) { it.length <= minValue }

    fun ValidationBuilder<String>.matches(regex: Regex) =
        addConstraint("must have correct format") { it.contains(regex) }

    fun ValidationBuilder<String>.containsANumber() =
        matches("[0-9]".toRegex()) hint "must have at least one number"

    @Test
    fun singleValidation() {
        val oneValidation = Validation<Register> {
            Register::password {
                minLength(1)
            }
        }

        Register(password = "a").let { assertEquals(Valid(it), oneValidation(it)) }
        Register(password = "").let { assertEquals(1, countErrors(oneValidation(it), Register::password)) }
    }

    @Test
    fun disjunctValidations() {
        val twoDisjunctValidations = Validation<Register> {
            Register::password {
                minLength(1)
            }
            Register::password {
                maxLength(10)
            }
        }

        Register(password = "a").let { assertEquals(Valid(it), twoDisjunctValidations(it)) }
        Register(password = "").let { assertEquals(1, countErrors(twoDisjunctValidations(it), Register::password)) }
        Register(password = "aaaaaaaaaaa").let { assertEquals(1, countErrors(twoDisjunctValidations(it), Register::password)) }
    }

    @Test
    fun overlappingValidations() {
        val overlappingValidations = Validation<Register> {
            Register::password {
                minLength(8)
                containsANumber()
            }
        }

        Register(password = "verysecure1").let { assertEquals(Valid(it), overlappingValidations(it)) }
        Register(password = "9").let { assertEquals(1, countErrors(overlappingValidations(it), Register::password)) }
        Register(password = "insecure").let { assertEquals(1, countErrors(overlappingValidations(it), Register::password)) }
        Register(password = "pass").let { assertEquals(2, countErrors(overlappingValidations(it), Register::password)) }
    }


    @Test
    fun validatingMultipleFields() {
        val overlappingValidations = Validation<Register> {
            Register::password {
                minLength(8)
                containsANumber()
            }

            Register::email {
                matches(".+@.+".toRegex())
            }
        }

        Register(email = "tester@test.com", password = "verysecure1").let { assertEquals(Valid(it), overlappingValidations(it)) }
        Register(email = "tester@test.com").let {
            assertEquals(1, countFieldsWithErrors(overlappingValidations(it)))
            assertEquals(2, countErrors(overlappingValidations(it), Register::password))
        }
        Register(password = "verysecure1").let { assertEquals(1, countErrors(overlappingValidations(it), Register::email)) }
        Register().let { assertEquals(2, countFieldsWithErrors(overlappingValidations(it))) }
    }

    @Test
    fun validatingNullableTypes() {
        val nullableTypeValidation = Validation<Register> {
            Register::referredBy ifPresent {
                matches(".+@.+".toRegex())
            }
        }

        Register(referredBy = null).let { assertEquals(Valid(it), nullableTypeValidation(it)) }
        Register(referredBy = "poweruser@test.com").let { assertEquals(Valid(it), nullableTypeValidation(it)) }
        Register(referredBy = "poweruser@").let { assertEquals(1, countErrors(nullableTypeValidation(it), Register::referredBy)) }
    }

    @Test
    fun validatingRequiredTypes() {
        val nullableTypeValidation = Validation<Register> {
            Register::referredBy required {
                matches(".+@.+".toRegex())
            }
        }

        Register(referredBy = "poweruser@test.com").let { assertEquals(Valid(it), nullableTypeValidation(it)) }

        Register(referredBy = null).let { assertEquals(1, countErrors(nullableTypeValidation(it), Register::referredBy)) }
        Register(referredBy = "poweruser@").let { assertEquals(1, countErrors(nullableTypeValidation(it), Register::referredBy)) }
    }

    @Test
    fun validatingNestedTypesDirectly() {
        val nestedTypeValidation = Validation<Register> {
            Register::home ifPresent {
                Address::address {
                    minLength(1)
                }
            }
        }

        Register(home = Address("Home")).let { assertEquals(Valid(it), nestedTypeValidation(it)) }
        Register(home = Address("")).let { assertEquals(1, countErrors(nestedTypeValidation(it), Register::home, Address::address)) }
    }

    @Test
    fun alternativeSyntax() {
        val splitDoubleValidation = Validation<Register> {
            Register::password.has.minLength(1)
            Register::password.has.maxLength(10)
            Register::email.has.matches(".+@.+".toRegex())
        }

        Register(email = "tester@test.com", password = "a").let { assertEquals(Valid(it), splitDoubleValidation(it)) }
        Register(email = "tester@test.com", password = "").let { assertEquals(1, countErrors(splitDoubleValidation(it), Register::password)) }
        Register(email = "tester@test.com", password = "aaaaaaaaaaa").let { assertEquals(1, countErrors(splitDoubleValidation(it), Register::password)) }
        Register(email = "tester@").let { assertEquals(2, countFieldsWithErrors(splitDoubleValidation(it))) }
    }

    @Test
    fun validateLists() {

        data class Data(val registrations: List<Register> = emptyList())

        val listValidation = Validation<Data> {
            Data::registrations onEach {
                Register::email {
                    minLength(3)
                }
            }
        }

        Data().let { assertEquals(Valid(it), listValidation(it)) }
        Data(registrations = listOf(Register(email = "valid"), Register(email = "a")))
            .let {
                assertEquals(1, countErrors(listValidation(it), Data::registrations, 1, Register::email))
            }
        Data(registrations = listOf(Register(email = "a"), Register(email = "ab")))
            .let {
                assertEquals(2, countFieldsWithErrors(listValidation(it)))
                assertEquals(1, countErrors(listValidation(it), Data::registrations, 1, Register::email))
            }
    }

    @Test
    fun validateArrays() {

        data class Data(val registrations: Array<Register> = emptyArray())

        val arrayValidation = Validation<Data> {
            Data::registrations onEach {
                Register::email {
                    minLength(3)
                }
            }
        }

        Data().let { assertEquals(Valid(it), arrayValidation(it)) }
        Data(registrations = arrayOf(Register(email = "valid"), Register(email = "a")))
            .let {
                assertEquals(1, countErrors(arrayValidation(it), Data::registrations, 1, Register::email))
            }
        Data(registrations = arrayOf(Register(email = "a"), Register(email = "ab")))
            .let {
                assertEquals(2, countFieldsWithErrors(arrayValidation(it)))
                assertEquals(1, countErrors(arrayValidation(it), Data::registrations, 1, Register::email))
            }
    }

    @Test
    fun validateHashMaps() {

        data class Data(val registrations: Map<String, Register> = emptyMap())

        val mapValidation = Validation<Data> {
            Data::registrations onEach {
                Map.Entry<String, Register>::value {
                    Register::email {
                        minLength(2)
                    }
                }
            }
        }

        Data().let { assertEquals(Valid(it), mapValidation(it)) }
        Data(registrations = mapOf(
            "user1" to Register(email = "valid"),
            "user2" to Register(email = "a")))
            .let {
                println(mapValidation(it))
                assertEquals(0, countErrors(mapValidation(it), Data::registrations, "user1", Register::email))
                assertEquals(1, countErrors(mapValidation(it), Data::registrations, "user2", Register::email))
            }
    }

    @Test
    fun replacePlaceholderInString() {
        val validation = Validation<Register> {
            Register::password.has.minLength(8)
        }
        assertTrue(validation(Register(password = ""))[Register::password]!![0].contains("8"))
    }

    private fun <T> countFieldsWithErrors(validationResult: ValidationResult<T>) = (validationResult as Invalid).errors.size
    private fun countErrors(validationResult: ValidationResult<*>, vararg properties: Any) = validationResult.get(*properties)?.size
        ?: 0

    private data class Register(val password: String = "", val email: String = "", val referredBy: String? = null, val home: Address? = null)
    private data class Address(val address: String = "", val country: String = "DE")
}
