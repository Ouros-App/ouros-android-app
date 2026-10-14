package com.ouros.app.feature.onboarding.tutorial

import com.ouros.app.R

object TutorialContent {
    val pages = listOf(
        TutorialPage(
            title = "Olá, me chamo Midas!",
            description = "Seja bem-vindo ao Ouros!\n\nSou seu assistente virtual e estou aqui para tirar suas dúvidas e resolver seus problemas.\n\nPara começar, vamos conhecer as funcionalidades do Ouros! Você pode me encontrar logo abaixo, no menu principal:",
            imageResId = R.drawable.tutorial_1
        ),
        TutorialPage(
            title = "Registro e Simulador",
            description = "Use o simulador para simular seus gastos e evitar desperdícios futuramente!\n\nVocê também pode registrar os dados dos seus lotes aqui e, em alguns dias, ver o resultado dos seus esforços no ranking!",
            imageResId = R.drawable.tutorial_2
        ),
        TutorialPage(
            title = "Análises e Ranking",
            description = "Você pode analisar o desempenho da sua granja com dashboards e com uma competição amigável com outros integrados!",
            imageResId = R.drawable.tutorial_3
        ),
        TutorialPage(
            title = "ChatBot",
            description = "Tire dúvidas sobre o funcionamento do aplicativo, a evolução da sua granja e como melhorar seu desenpenho com o Midas, nosso ChatBot!\n\nAlém de fazer perguntas, você pode anexar planilhas para comporem a base de dados.",
            imageResId = R.drawable.tutorial_4
        ),
        TutorialPage(
            title = "Serviço de Atendimento ao Consumidor (SAC)",
            description = "Caso tenha alguma dúvida, sugestão ou problema, pode consultar no nosso SAC aqui!",
            imageResId = R.drawable.tutorial_5
        )
    )
}