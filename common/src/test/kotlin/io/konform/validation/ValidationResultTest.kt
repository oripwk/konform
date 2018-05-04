package io.konform.validation

import io.konform.validation.jsonschema.maxLength
import io.konform.validation.jsonschema.maximum
import io.konform.validation.jsonschema.minLength
import io.konform.validation.jsonschema.minimum
import kotlin.test.Test
import kotlin.test.assertTrue

class ValidationResultTest {

    @Test
    fun allErrors() {
        data class Example(val num: Int, val string: String)
        val validation = Validation<Example> {
            Example::num {
                minimum(3)
                maximum(4)
            }
            Example::string {
                minLength(3)
                maxLength(4)
            }
        }
        val result = validation(Example(1, "ab"))
        val expected = listOf(
            result[Example::num],
            result[Example::string]
        ).map { it.orEmpty() }.reduce { a, b -> a + b }
        assertTrue { expected.containsAll(result.getAll()) }
        assertTrue { result.getAll().containsAll(expected) }
    }

}
