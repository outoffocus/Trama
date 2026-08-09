package com.trama.shared.speech

import org.junit.Assert.assertTrue
import org.junit.Test

class IntentDetectionCorpusTest {
    private data class Example(val text: String, val expectedIntent: String?)

    private data class Metrics(
        val correct: Int,
        val falsePositives: Int,
        val falseNegatives: Int,
        val categoryErrors: Int
    ) {
        val precision: Double
            get() = correct.toDouble() / (correct + falsePositives).coerceAtLeast(1)
        val recall: Double
            get() = correct.toDouble() / (correct + falseNegatives).coerceAtLeast(1)
    }

    @Test
    fun `labelled regression corpus meets detection quality floor`() {
        val detector = IntentDetector()
        val corpus = listOf(
            Example("recordar comprar leche", "recordatorios"),
            Example("recuérdame que llame a Julia", "recordatorios"),
            Example("acordarme de renovar el DNI", "recordatorios"),
            Example("no olvidarme de las entradas", "recordatorios"),
            Example("nota mental revisar las luces", "recordatorios"),
            Example("tengo que comprar pan", "tareas"),
            Example("tenemos que preparar la reunión", "tareas"),
            Example("tengo que ir al banco", "tareas"),
            Example("necesito recoger el paquete", "tareas"),
            Example("queda pendiente de revisar", "tareas"),
            Example("falta por firmar el contrato", "tareas"),
            Example("tengo que firmar mañana", "tareas"),
            Example("tenemos que hablar con Mario", "tareas"),
            Example("tengo que contestar a Lucía", "tareas"),
            Example("tengo cita con la médica", "compromisos"),
            Example("mañana tengo la ITV", "compromisos"),
            Example("he quedado con Sara el viernes", "compromisos"),
            Example("tengo dentista el lunes", "compromisos"),
            Example("el cielo está azul", null),
            Example("vamos a poner una película", null),
            Example("quiero mirar esto", null),
            Example("llamar a esto progreso es exagerado", null),
            Example("los niños tienen que dormir", null),
            Example("tenemos que tener el mismo formato", null),
            Example("recordó la historia de ayer", null),
            Example("mis recordatorios están vacíos", null),
            Example("el recordatorio sonó tarde", null),
            Example("he contestado por whatsapp", null),
            Example("necesitamos más espacio", null),
            Example("la recitación fue estupenda", null),
            Example("quedé sorprendido con Elena", null),
            Example("la cita textual está mal", null),
            Example("hablamos de comprar una casa", null),
            Example("mañana será otro día", null),
            Example("tienes que escucharme", null),
            Example("hay que hacerlo", null),
            Example("recordar la infancia me pone triste", null),
            Example("me olvidé cómo acababa la película", null),
            Example("Pablo dijo que tengo que comprar pan", null),
            Example("no sé si tengo que llamar a Ana", null),
            Example("en la reunión dijeron que tenemos que cambiar el formato", null),
            Example("tengo que reconocer que estaba equivocado", null),
            Example("tengo que decir que la película fue buena", null)
        )

        val metrics = evaluate(detector, corpus)

        assertTrue("precision=${metrics.precision} $metrics", metrics.precision >= 0.95)
        assertTrue("recall=${metrics.recall} $metrics", metrics.recall >= 0.95)
        assertTrue("categoryErrors=${metrics.categoryErrors}", metrics.categoryErrors <= 1)
    }

    private fun evaluate(detector: IntentDetector, corpus: List<Example>): Metrics {
        var correct = 0
        var falsePositives = 0
        var falseNegatives = 0
        var categoryErrors = 0
        corpus.forEach { example ->
            val actual = detector.detect(example.text)?.pattern?.id
            when {
                actual == example.expectedIntent && actual != null -> correct++
                example.expectedIntent == null && actual != null -> falsePositives++
                example.expectedIntent != null && actual == null -> falseNegatives++
                example.expectedIntent != null && actual != example.expectedIntent -> {
                    categoryErrors++
                    falsePositives++
                    falseNegatives++
                }
            }
        }
        return Metrics(correct, falsePositives, falseNegatives, categoryErrors)
    }
}
