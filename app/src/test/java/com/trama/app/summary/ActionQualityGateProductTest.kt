package com.trama.app.summary

import com.trama.shared.model.EntryActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionQualityGateProductTest {

    @Test
    fun `daily actionable examples are accepted`() {
        val examples = listOf(
            Case("Comprar leche y pan mañana", EntryActionType.BUY),
            Case("Comprar una cafetera nueva este fin de semana", EntryActionType.BUY),
            Case("Comprar el regalo de papa", EntryActionType.BUY),
            Case("Llamar al dentista para pedir cita", EntryActionType.CALL),
            Case("Llamar al colegio de mi hija", EntryActionType.CALL),
            Case("Contestarle el mensaje a Sadoth mañana", EntryActionType.GENERIC),
            Case("Enviar el email a Cecile", EntryActionType.SEND),
            Case("Mandar la documentación al gestor", EntryActionType.SEND),
            Case("Pagar la piscina la próxima semana", EntryActionType.GENERIC),
            Case("A Luis le toca pagar la piscina", EntryActionType.GENERIC),
            Case("Reservar la comida para el domingo", EntryActionType.GENERIC),
            Case("Pedir cita para renovar el DNI", EntryActionType.GENERIC),
            Case("Recoger el coche en el taller", EntryActionType.GENERIC),
            Case("Recoger la medicina en la farmacia", EntryActionType.GENERIC),
            Case("Llevar a la niña al pediatra", EntryActionType.GENERIC),
            Case("Preparar la mochila de la niña", EntryActionType.GENERIC),
            Case("Limpiar lo que dejó Elena sin lavar y comer", EntryActionType.GENERIC),
            Case("Lavar las sábanas", EntryActionType.GENERIC),
            Case("Fregar los platos de la cena", EntryActionType.GENERIC),
            Case("Ordenar el armario del pasillo", EntryActionType.GENERIC),
            Case("Guardar los papeles del banco", EntryActionType.GENERIC),
            Case("Sacar la basura por la noche", EntryActionType.GENERIC),
            Case("Regar las plantas del salón", EntryActionType.GENERIC),
            Case("Devolver el paquete de Amazon", EntryActionType.GENERIC),
            Case("Firmar la autorización del colegio", EntryActionType.GENERIC),
            Case("Revisar el contrato antes del viernes", EntryActionType.REVIEW),
            Case("Mirar vuelos para Ourense", EntryActionType.REVIEW),
            Case("Hablar con Elena de la cafetera", EntryActionType.TALK_TO),
            Case("Ir a casa de los padres de Lena", EntryActionType.GENERIC),
            Case("Gemma tiene cita con el pediatra mañana", EntryActionType.EVENT),
            Case("Cita con el médico el lunes", EntryActionType.EVENT),
            Case("Reunión con Marta a las cinco", EntryActionType.EVENT)
        )

        examples.forEach { case ->
            assertTrue(
                "Expected actionable: ${case.text}",
                ActionQualityGate.isActionable(case.text, case.actionType)
            )
        }
    }

    @Test
    fun `daily non actionable examples are rejected even if model is over optimistic`() {
        val examples = listOf(
            "No quería verla ahí. Ahí no escucho",
            "Voy a hablar como sale",
            "¿Te asiste esto?",
            "¿No es barato?",
            "Abre",
            "Mañana",
            "Tengo que",
            "Hay que",
            "Recordar",
            "En esa Ourense mañana",
            "Hable con te adoro y y tengo que comprarme una",
            "Recoger nada",
            "Ya no tengo que acordarme de ir a trabajar",
            "No hay que llamar al dentista",
            "No necesito comprar leche",
            "Hoy estuve hablando con Elena",
            "Esta semana me tocó a mí pagar la piscina",
            "La piscina está pagada",
            "La cafetera nueva es cara",
            "El mensaje de Sadoth llegó ayer",
            "No sé qué hacer",
            "Tengo dudas con esto",
            "Eso se puede hacer en nada",
            "Por ejemplo haces una llamada y ya está",
            "Vale, pues Alex, tú creas una función de enviar email",
            "Función de enviar email con la dirección que usamos",
            "Descripción del plan y contenido que pongamos",
            "Comprar",
            "Llamar a",
            "Enviar el",
            "Pedir una",
            "Hacer algo",
            "Ver cosas",
            "Ahí no escucho nada",
            "Hablé con Sadoth y fue bien",
            "Me gusta la cafetera nueva"
        )

        examples.forEach { text ->
            assertFalse(
                "Expected rejected: $text",
                ActionQualityGate.isActionable(text, EntryActionType.GENERIC)
            )
        }
    }

    @Test
    fun `combinatorial daily actionable matrix is accepted`() {
        val wrappers = listOf(
            "%s",
            "mañana %s",
            "esta tarde %s",
            "cuando pueda %s",
            "antes de comer %s",
            "el viernes %s",
            "en casa %s",
            "en la oficina %s"
        )
        val triggers = listOf(
            "",
            "tengo que ",
            "tenemos que ",
            "hay que ",
            "debo ",
            "necesito ",
            "me queda pendiente "
        )
        val actions = listOf(
            "comprar" to "leche para casa",
            "comprar" to "el regalo de papa",
            "comprar" to "pañales para la niña",
            "llamar" to "al dentista",
            "llamar" to "al colegio",
            "llamar" to "a la farmacia",
            "enviar" to "el email a Cecile",
            "enviar" to "la factura al gestor",
            "mandar" to "la documentación al gestor",
            "contestar" to "el mensaje de Sadoth",
            "contestarle" to "el mensaje a Elena",
            "pagar" to "la piscina",
            "pagar" to "el recibo de la luz",
            "reservar" to "la comida del domingo",
            "reservar" to "mesa para cenar",
            "pedir" to "cita para el pediatra",
            "pedir" to "presupuesto para la cocina",
            "recoger" to "el coche del taller",
            "recoger" to "la medicina de la farmacia",
            "limpiar" to "la cocina",
            "lavar" to "las sábanas",
            "fregar" to "los platos",
            "ordenar" to "el armario",
            "preparar" to "la mochila de la niña",
            "llevar" to "a la niña al pediatra",
            "sacar" to "la basura",
            "regar" to "las plantas",
            "devolver" to "el paquete",
            "firmar" to "la autorización",
            "revisar" to "el contrato",
            "revisar" to "los papeles del banco",
            "mirar" to "los vuelos",
            "hablar con" to "Elena de la cafetera",
            "hablar con" to "Luis de la piscina",
            "ir" to "a la farmacia"
        )
        val suffixes = listOf(
            "",
            " mañana",
            " esta tarde",
            " el viernes",
            " la próxima semana",
            " antes de comer",
            " por la noche",
            " cuando salga de trabajar"
        )

        var checked = 0
        for (wrapper in wrappers) {
            for (trigger in triggers) {
                for ((verb, complement) in actions) {
                    for (suffix in suffixes) {
                        val body = "${trigger}${verb} $complement$suffix"
                        val text = wrapper.format(body).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        }
                        assertTrue(
                            "Expected generated actionable: $text",
                            ActionQualityGate.isActionable(text, inferredType(verb))
                        )
                        checked += 1
                    }
                }
            }
        }

        assertTrue("Expected a broad generated corpus", checked >= 10_000)
    }

    @Test
    fun `combinatorial noise and incomplete matrix is rejected`() {
        val conversationPrefixes = listOf(
            "hoy estuve hablando de",
            "ayer comentamos",
            "esta mañana comentamos",
            "en la oficina hablamos de",
            "me gusta",
            "no entiendo",
            "no escucho",
            "estoy viendo",
            "me parece caro",
            "es raro lo de",
            "me da igual lo de",
            "estábamos debatiendo sobre",
            "la noticia hablaba de"
        )
        val topics = listOf(
            "la cafetera nueva",
            "el mensaje de Sadoth",
            "la piscina",
            "el coche del taller",
            "la comida del domingo",
            "la documentación",
            "el colegio",
            "la farmacia",
            "la cita del pediatra",
            "el recibo de la luz",
            "el paquete",
            "los vuelos",
            "el contrato",
            "los platos",
            "la basura",
            "las plantas",
            "el banco",
            "la autorización",
            "la mochila de la niña",
            "la cena con amigos"
        )
        val conversationEndings = listOf("", " y ya está", " pero no era importante", " sin más", " como ejemplo")
        val incompleteTriggers = listOf(
            "tengo que",
            "hay que",
            "debo",
            "necesito",
            "me queda pendiente",
            "tenemos que",
            "tiene que",
            "recordar"
        )
        val incompleteActions = listOf(
            "comprar",
            "llamar a",
            "enviar el",
            "mandar una",
            "recoger la",
            "pedir",
            "ir a",
            "hablar con",
            "limpiar lo",
            "preparar la",
            "revisar el",
            "mirar una"
        )
        val negations = listOf(
            "ya no tengo que",
            "no hay que",
            "no necesito",
            "no debemos",
            "no tengo que",
            "no tenemos que"
        )
        val negatedActions = listOf(
            "comprar leche",
            "llamar al dentista",
            "enviar el email",
            "pagar la piscina",
            "recoger el coche",
            "limpiar la cocina",
            "preparar la mochila",
            "ir a la farmacia",
            "reservar la comida",
            "contestar el mensaje"
        )

        var checked = 0
        for (prefix in conversationPrefixes) {
            for (topic in topics) {
                for (ending in conversationEndings) {
                    val text = "$prefix $topic$ending"
                    assertFalse(
                        "Expected generated non-actionable: $text",
                        ActionQualityGate.isActionable(text, EntryActionType.GENERIC)
                    )
                    checked += 1
                }
            }
        }

        for (trigger in incompleteTriggers) {
            for (action in incompleteActions) {
                val text = "$trigger $action"
                assertFalse(
                    "Expected generated incomplete fragment rejected: $text",
                    ActionQualityGate.isActionable(text, EntryActionType.GENERIC)
                )
                checked += 1
            }
        }

        for (negation in negations) {
            for (action in negatedActions) {
                val text = "$negation $action"
                assertFalse(
                    "Expected generated negated task rejected: $text",
                    ActionQualityGate.isActionable(text, EntryActionType.GENERIC)
                )
                checked += 1
            }
        }

        assertTrue("Expected a broad generated rejection corpus", checked >= 1_200)
    }

    @Test
    fun `model rejection always wins over local task shape`() {
        val examples = listOf(
            "Comprar leche mañana",
            "Llamar al dentista",
            "Limpiar la cocina",
            "Cita con el médico"
        )

        examples.forEach { text ->
            assertFalse(
                "Expected model rejection to win: $text",
                ActionQualityGate.isActionable(text, EntryActionType.GENERIC, modelIsActionable = false)
            )
        }
    }

    private fun inferredType(verb: String): String = when (verb) {
        "comprar" -> EntryActionType.BUY
        "llamar" -> EntryActionType.CALL
        "enviar", "mandar" -> EntryActionType.SEND
        "revisar", "mirar" -> EntryActionType.REVIEW
        "hablar con" -> EntryActionType.TALK_TO
        else -> EntryActionType.GENERIC
    }

    private data class Case(
        val text: String,
        val actionType: String
    )
}
