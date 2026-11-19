package com.chatcat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexTest {

    @Test
    fun `上标下标转成 Unicode`() {
        assertEquals("x²", Latex.inline("x^2"))
        assertEquals("aᵢ", Latex.inline("a_i"))
        assertEquals("x²⁺¹", Latex.inline("x^{2+1}"))
    }

    @Test
    fun `希腊字母与运算符`() {
        assertEquals("α", Latex.inline("\\alpha"))
        assertEquals("a ≤ b ≠ c", Latex.inline("a \\le b \\ne c"))
        assertEquals("∑", Latex.inline("\\sum"))
    }

    @Test
    fun `分式与根号`() {
        assertEquals("(a)/(b)", Latex.inline("\\frac{a}{b}"))
        assertEquals("√(x+1)", Latex.inline("\\sqrt{x+1}"))
    }

    @Test
    fun `块级解析保留结构`() {
        val nodes = Latex.parse("\\frac{1}{2} + \\sqrt{x}")
        assertTrue(nodes.any { it is Latex.Node.Frac })
        assertTrue(nodes.any { it is Latex.Node.Sqrt })
    }

    @Test
    fun `纯文本不受影响`() {
        assertEquals("速度 v 与时间 t", Latex.inline("速度 v 与时间 t"))
    }
}
